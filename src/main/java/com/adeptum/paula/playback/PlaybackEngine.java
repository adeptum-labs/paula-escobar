/*
 * Paula Escobar is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adam Waldenberg, Adeptum AB, Org.nr 559494-1824.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Website: https://www.adeptum.se
 * Contact: info@adeptum.se
 */

package com.adeptum.paula.playback;

import static com.adeptum.paula.playback.PlaybackState.FINISHED;
import static com.adeptum.paula.playback.PlaybackState.PAUSED;
import static com.adeptum.paula.playback.PlaybackState.PLAYING;
import static com.adeptum.paula.playback.PlaybackState.STOPPED;

import com.adeptum.paula.audio.AudioException;
import com.adeptum.paula.audio.AudioSink;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Pumps PCM from a {@link Renderer} into an {@link AudioSink} on a background thread.
 *
 * <p>The sink the sound goes to can be swapped while a song plays, which is how it moves to a device on the
 * network and back; copies, such as a recording, get everything whichever output is in use. The tap holds
 * enough of what went out for the screen to show what a device that plays late is playing.</p>
 */
@Slf4j
public final class PlaybackEngine implements AutoCloseable {

    private static final int CHANNELS = 2;
    private static final long PAUSE_POLL_MILLIS = 20;
    private static final int TAP_SECONDS = 32;

    private final List<AudioSink> copies;
    private final int sampleRate;
    private final short[] buffer;
    private final AudioTap tap;
    private final Object rendererLock = new Object();

    private volatile AudioSink output;
    private volatile PlaybackState state = STOPPED;
    private volatile Renderer renderer;
    private volatile String title = "";
    private volatile String subtitle = "";
    private Thread pump;

    public PlaybackEngine(AudioSink output, int sampleRate, int bufferFrames) throws AudioException {
        this(output, List.of(), sampleRate, bufferFrames);
    }

    public PlaybackEngine(AudioSink output, List<AudioSink> copies, int sampleRate, int bufferFrames)
            throws AudioException {
        this.output = output;
        this.copies = copies;
        this.sampleRate = sampleRate;
        this.buffer = new short[bufferFrames * CHANNELS];
        this.tap = new AudioTap(sampleRate * TAP_SECONDS);
        output.open(sampleRate);
        for (final AudioSink copy : copies) {
            copy.open(sampleRate);
        }
    }

    public int sampleRate() {
        return sampleRate;
    }

    public PlaybackState state() {
        return state;
    }

    public AudioTap tap() {
        return tap;
    }

    public AudioSink output() {
        return output;
    }

    public Duration position() {
        final Renderer current = renderer;
        return current == null ? Duration.ZERO : current.position();
    }

    public synchronized void play(Renderer newRenderer) throws AudioException {
        play(newRenderer, "", "");
    }

    public synchronized void play(Renderer newRenderer, String newTitle, String newSubtitle) throws AudioException {
        stop();
        title = newTitle;
        subtitle = newSubtitle;
        output.begin(newTitle, newSubtitle);
        renderer = newRenderer;
        state = PLAYING;
        pump = new Thread(this::pumpLoop, "paula-audio");
        pump.setDaemon(true);
        pump.start();
    }

    /**
     * Moves the sound to another sink without stopping the song: the new one is opened and told what plays
     * before it takes over, and the old one is closed after, which lets go of a write it may be holding.
     */
    public synchronized void switchOutput(AudioSink next) throws AudioException {
        next.open(sampleRate);
        if (state == PLAYING || state == PAUSED) {
            try {
                next.begin(title, subtitle);
            } catch (AudioException e) {
                next.close();
                throw e;
            }
        }
        final AudioSink previous = output;
        output = next;
        previous.close();
    }

    public void togglePause() {
        if (state == PLAYING) {
            state = PAUSED;
        } else if (state == PAUSED) {
            state = PLAYING;
        }
    }

    /**
     * The renderer belongs to the pump thread, so a seek borrows it between two buffers instead of racing it.
     */
    public void seek(Duration delta) {
        synchronized (rendererLock) {
            final Renderer current = renderer;
            if (current != null) {
                current.seek(current.position().plus(delta));
            }
        }
    }

    public synchronized void stop() {
        state = STOPPED;
        if (pump != null) {
            joinPump();
            pump = null;
        }
    }

    public void awaitEnd() {
        final Thread current = pump;
        if (current != null) {
            joinQuietly(current);
        }
    }

    @Override
    public void close() {
        stop();
        output.close();
        copies.forEach(AudioSink::close);
    }

    private void pumpLoop() {
        while (state == PLAYING || state == PAUSED) {
            if (state == PAUSED) {
                sleepQuietly();
                continue;
            }
            final int frames = renderNextFrames();
            if (frames == 0) {
                finish();
                return;
            }
            if (!writeToSink(frames)) {
                return;
            }
        }
    }

    /**
     * The song has been rendered in full, which for a sink that plays late is not yet the end of it.
     */
    private void finish() {
        log.debug("Renderer finished at {}", renderer.position());
        try {
            output.drain();
        } catch (RuntimeException e) {
            log.debug("Draining the output", e);
        }
        if (state == PLAYING) {
            state = FINISHED;
        }
    }

    private int renderNextFrames() {
        synchronized (rendererLock) {
            try {
                return renderer.render(buffer);
            } catch (RuntimeException e) {
                log.error("The decoder failed, giving up on the current song", e);
                return 0;
            }
        }
    }

    private boolean writeToSink(int frames) {
        try {
            output.write(buffer, frames);
            for (final AudioSink copy : copies) {
                copy.write(buffer, frames);
            }
            tap.write(buffer, frames);
            return true;
        } catch (RuntimeException e) {
            log.error("Audio output failed, giving up on the current song", e);
            state = FINISHED;
            return false;
        }
    }

    /**
     * An output that plays late holds the player back while it catches up, seconds at a time, and the song
     * being changed out from under it would otherwise wait that hold out with nothing on the screen moving.
     * The pump is told to let go rather than asked to notice.
     */
    private void joinPump() {
        pump.interrupt();
        joinQuietly(pump);
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(PAUSE_POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

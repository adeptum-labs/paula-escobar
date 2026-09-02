/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import com.adeptum.paula.audio.AudioException;
import com.adeptum.paula.audio.AudioSink;

/**
 * Pumps PCM from a {@link Renderer} into an {@link AudioSink} on a background thread.
 */
@Slf4j
public final class PlaybackEngine implements AutoCloseable {

    private static final int CHANNELS = 2;
    private static final long PAUSE_POLL_MILLIS = 20;
    private static final int TAP_FRAMES = 8192;

    private final AudioSink sink;
    private final int sampleRate;
    private final short[] buffer;
    private final AudioTap tap = new AudioTap(TAP_FRAMES);
    private final Object rendererLock = new Object();

    private volatile PlaybackState state = STOPPED;
    private volatile Renderer renderer;
    private Thread pump;

    public PlaybackEngine(AudioSink sink, int sampleRate, int bufferFrames) throws AudioException {
        this.sink = sink;
        this.sampleRate = sampleRate;
        this.buffer = new short[bufferFrames * CHANNELS];
        sink.open(sampleRate);
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

    public Duration position() {
        final Renderer current = renderer;
        return current == null ? Duration.ZERO : current.position();
    }

    public synchronized void play(Renderer newRenderer) {
        stop();
        renderer = newRenderer;
        state = PLAYING;
        pump = new Thread(this::pumpLoop, "paula-audio");
        pump.setDaemon(true);
        pump.start();
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
        sink.close();
    }

    private void pumpLoop() {
        while (state == PLAYING || state == PAUSED) {
            if (state == PAUSED) {
                sleepQuietly();
                continue;
            }
            final int frames = renderNextFrames();
            if (frames == 0) {
                state = FINISHED;
                log.debug("Renderer finished at {}", renderer.position());
                return;
            }
            if (!writeToSink(frames)) {
                return;
            }
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
            sink.write(buffer, frames);
            tap.write(buffer, frames);
            return true;
        } catch (RuntimeException e) {
            log.error("Audio output failed, giving up on the current song", e);
            state = FINISHED;
            return false;
        }
    }

    private void joinPump() {
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

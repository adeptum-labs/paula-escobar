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

package com.adeptum.paula.cast;

import com.adeptum.paula.audio.AudioException;
import com.adeptum.paula.audio.AudioSink;
import com.adeptum.paula.cast.CastSession.Position;
import com.adeptum.paula.cast.CastStreamServer.Served;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Sound going to a Cast device: each song is served as a stream of its own and the device told to play it,
 * so a screen shows a card for the song, and the player is held to the device's pace by the stream.
 *
 * <p>The device runs seconds behind what has been written to it. How far is measured rather than assumed,
 * from where it says it is against what it has been given, and it is asked once a second to keep that
 * fresh.</p>
 */
@Slf4j
public final class CastSink implements AudioSink {

    private static final Duration POLL = Duration.ofSeconds(1);
    private static final Duration LONGEST_TAIL = Duration.ofSeconds(30);
    private static final long DRAIN_STEP_MILLIS = 100;
    private static final String UNTITLED = "Paula Escobar";

    private final CastDevice device;
    private final ScheduledExecutorService poller;
    private volatile CastSession session;
    private volatile CastStreamServer server;
    private volatile Served served;
    private volatile int sampleRate;

    public CastSink(CastDevice device) {
        this.device = device;
        this.poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "paula-cast-poll");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CastDevice device() {
        return device;
    }

    @Override
    public void open(int sampleRate) throws AudioException {
        this.sampleRate = sampleRate;
        try {
            session = CastSession.open(device);
            server = new CastStreamServer(session.localAddress());
        } catch (IOException e) {
            close();
            throw new AudioException("Cannot reach " + device.name() + ": " + e.getMessage(), e);
        }
        poller.scheduleAtFixedRate(this::poll, POLL.toMillis(), POLL.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void begin(String title, String subtitle) throws AudioException {
        forgetStream();
        final Served next = server.open(sampleRate);
        try {
            session.load(next.url(), title.isBlank() ? UNTITLED : title, subtitle);
        } catch (IOException e) {
            server.forget(next);
            throw new AudioException(device.name() + " would not play: " + e.getMessage(), e);
        }
        served = next;
    }

    @Override
    public void write(short[] interleavedStereo, int frames) {
        if (served == null) {
            beginUntitled();
        }
        served.stream().write(interleavedStereo, frames);
    }

    /**
     * Waits for the device to play out what it holds, or for it to say it has stopped, before the next song
     * is loaded over it.
     */
    @Override
    public void drain() {
        final Served current = served;
        if (current == null) {
            return;
        }
        current.stream().end();
        final long until = System.currentTimeMillis() + lag().orElse(Duration.ZERO).toMillis() + LONGEST_TAIL.toMillis();
        while (System.currentTimeMillis() < until && !session.isFinished()) {
            try {
                Thread.sleep(DRAIN_STEP_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        forgetStream();
    }

    /**
     * How far the sound heard runs behind the sound written, or nothing until the device has said where it
     * is.
     */
    public Optional<Duration> lag() {
        final Served current = served;
        final CastSession open = session;
        if (current == null || open == null) {
            return Optional.empty();
        }
        return open.position().filter(Position::isPlaying)
                .map(position -> current.stream().writtenFrames() / (double) sampleRate - position.now())
                .map(seconds -> Duration.ofMillis(Math.round(Math.max(0, seconds) * 1000)));
    }

    @Override
    public void close() {
        poller.shutdownNow();
        forgetStream();
        if (session != null) {
            session.close();
        }
        if (server != null) {
            server.close();
        }
    }

    private void beginUntitled() {
        try {
            begin(UNTITLED, "");
        } catch (AudioException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void poll() {
        final CastSession open = session;
        if (open != null && served != null) {
            open.poll();
        }
    }

    private void forgetStream() {
        final Served current = served;
        served = null;
        if (current != null) {
            server.forget(current);
        }
    }
}

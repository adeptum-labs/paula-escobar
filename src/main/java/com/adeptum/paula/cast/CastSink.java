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
import com.adeptum.paula.audio.NowPlaying;
import com.adeptum.paula.cast.CastSession.Position;
import com.adeptum.paula.cast.CastStreamServer.Served;
import com.adeptum.paula.image.TextPicture;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
    private static final long STEP_MILLIS = 100;
    private static final String UNTITLED = "Paula Escobar";
    private static final int WIDEST_ON_A_CARD = 44;
    private static final int CARD_MARGIN = 3;

    /**
     * How far behind the device is left to run unless another distance is asked for. A device left alone
     * buffers further ahead than this and is held here instead, so this is what the sound heard settles at
     * rather than only a ceiling on it; it is also what gives back the seconds a rebuffer costs, which the
     * device never makes up on its own. Below the sound held for the device plus what the device keeps of
     * its own the stream runs dry and the sound stutters, so shortening it has a floor.
     */
    public static final Duration FURTHEST_BEHIND = Duration.ofSeconds(4);

    /**
     * How long the player waits for a device to catch up before writing anyway, so a device that has stopped
     * saying where it is fails on the stream rather than holding the song for ever.
     */
    private static final Duration LONGEST_WAIT = Duration.ofSeconds(30);

    /**
     * How long a device is given to take the sound up before it is taken not to be playing it at all.
     */
    private static final Duration TO_START = Duration.ofSeconds(5);

    private final CastDevice device;
    private final int port;
    private final Duration furthestBehind;
    private final Connection connection;
    private final ScheduledExecutorService poller;
    private volatile CastSession session;
    private volatile CastStreamServer server;
    private volatile Served served;
    private volatile int sampleRate;

    public CastSink(CastDevice device, int port, Duration furthestBehind) {
        this(device, port, furthestBehind, CastSession::open);
    }

    CastSink(CastDevice device, int port, Duration furthestBehind, Connection connection) {
        this.device = device;
        this.port = port;
        this.furthestBehind = furthestBehind;
        this.connection = connection;
        this.poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "paula-cast-poll");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * How the sink reaches the device, which a test answers itself instead of going out on the network.
     */
    @FunctionalInterface
    interface Connection {

        CastSession to(CastDevice device) throws IOException;
    }

    public CastDevice device() {
        return device;
    }

    @Override
    public void open(int sampleRate) throws AudioException {
        this.sampleRate = sampleRate;
        try {
            session = connection.to(device);
            server = new CastStreamServer(session.localAddress(), port);
        } catch (IOException e) {
            close();
            throw new AudioException("Cannot reach " + device.name() + ": " + e.getMessage(), e);
        }
        poller.scheduleAtFixedRate(this::poll, POLL.toMillis(), POLL.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void begin(NowPlaying song) throws AudioException {
        forgetStream();
        final Served next = server.open(sampleRate, song.length(),
                song.picture() == null ? TextPicture.of(shown(song)) : new byte[0]);
        log.debug("Serving {} at {}", song.title(), next.url());
        try {
            session.load(next.url(), shown(song, next));
        } catch (IOException e) {
            server.forget(next);
            throw new AudioException(device.name() + " would not play: " + e.getMessage(), e);
        }
        served = next;
        awaitFetch(next);
    }

    /**
     * Waits for the device to take the sound up before the player is handed to it. A device that cannot
     * reach the address says so within moments, and waiting on one that never fetches is what would
     * otherwise hold the player still with nothing to show for it. What is waited for is the device
     * reaching for the stream, since that is what it does at once, while saying where it is can wait.
     */
    private void awaitFetch(Served next) throws AudioException {
        final long until = System.currentTimeMillis() + TO_START.toMillis();
        while (!next.stream().isFetched() && session.refusal().isEmpty() && System.currentTimeMillis() < until) {
            if (!pause()) {
                return;
            }
        }
        if (!next.stream().isFetched()) {
            forgetStream();
            throw new AudioException(device.name() + " could not fetch the sound from " + next.url()
                    + session.refusal().map(reason -> " (" + reason + ")").orElse(""), null);
        }
    }

    @Override
    public void write(short[] interleavedStereo, int frames) {
        if (served == null) {
            beginUntitled();
        }
        failIfTheDeviceGaveUp();
        waitForDeviceToCatchUp();
        final Served current = served;
        if (current != null) {
            current.stream().write(interleavedStereo, frames);
        }
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
            if (!pause()) {
                break;
            }
        }
        forgetStream();
    }

    /**
     * Holds the player while a playing device is further behind than it should be, so the seconds a
     * rebuffer costs are given back instead of kept for the rest of the song. A device filling its buffer is
     * left alone: it is behind because it is not playing yet, and starving it is how it stays that way.
     */
    private void waitForDeviceToCatchUp() {
        final long until = System.currentTimeMillis() + LONGEST_WAIT.toMillis();
        while (hasFallenBehind() && System.currentTimeMillis() < until) {
            if (!pause()) {
                return;
            }
        }
    }

    /**
     * A device taken over by someone else, or one that lost the stream, stops fetching; saying so beats
     * holding the player against a device that will never take another frame.
     */
    private void failIfTheDeviceGaveUp() {
        final Optional<String> refusal = session.refusal();
        if (refusal.isPresent()) {
            throw new IllegalStateException(device.name() + " stopped playing the sound (" + refusal.get() + ")");
        }
    }

    private boolean hasFallenBehind() {
        final CastSession open = session;
        return open != null && open.position().filter(Position::isPlaying).isPresent()
                && lag().orElse(Duration.ZERO).compareTo(furthestBehind) > 0;
    }

    /**
     * Returns false when the wait was cut short, which leaves the thread interrupted for the caller.
     */
    private boolean pause() {
        try {
            Thread.sleep(STEP_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * How far the sound heard runs behind the sound written, or nothing until the device has said where it
     * is. A device that has stopped playing to fill its buffer falls further behind while it does, which is
     * what the reading says, rather than nothing at all.
     */
    public Optional<Duration> lag() {
        final Served current = served;
        final CastSession open = session;
        if (current == null || open == null) {
            return Optional.empty();
        }
        return open.position()
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

    /**
     * What is drawn for a screen: the art the release carries, or a card of what is known about the song
     * where it carries none, which is most of them.
     */
    private static List<String> shown(NowPlaying song) {
        if (!song.art().isEmpty()) {
            return song.art();
        }
        final List<String> text = Stream.of(song.title(), song.artist(), song.album())
                .filter(line -> !line.isBlank())
                .map(line -> line.length() > WIDEST_ON_A_CARD ? line.substring(0, WIDEST_ON_A_CARD) : line)
                .toList();
        return text.isEmpty() ? List.of() : framed(text);
    }

    /**
     * The lines in a box the width of the longest, drawn the way the scene framed its own.
     */
    private static List<String> framed(List<String> text) {
        final int width = text.stream().mapToInt(String::length).max().orElseThrow() + 2 * CARD_MARGIN;
        final List<String> card = new ArrayList<>();
        card.add("╔" + "═".repeat(width) + "╗");
        card.add("║" + " ".repeat(width) + "║");
        text.forEach(line -> card.add("║" + " ".repeat(CARD_MARGIN) + line
                + " ".repeat(width - CARD_MARGIN - line.length()) + "║"));
        card.add("║" + " ".repeat(width) + "║");
        card.add("╚" + "═".repeat(width) + "╝");
        return card;
    }

    /**
     * The song as the device is told it: under the player's name where it has none of its own, since a screen
     * showing nothing at all is worse, and pointed at the picture drawn from its art where nobody holds one.
     */
    private static NowPlaying shown(NowPlaying song, Served served) {
        final NowPlaying.NowPlayingBuilder shown = song.toBuilder();
        if (song.title().isBlank()) {
            shown.title(UNTITLED);
        }
        if (song.picture() == null) {
            shown.picture(served.picture());
        }
        return shown.build();
    }

    private void beginUntitled() {
        try {
            begin(NowPlaying.builder().title(UNTITLED).build());
        } catch (AudioException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void poll() {
        final CastSession open = session;
        final Served current = served;
        if (open != null && current != null) {
            open.poll();
            log.debug("{} has been given {} s of the song and is {} s behind", device.name(),
                    String.format("%.1f", current.stream().writtenFrames() / (double) sampleRate),
                    lag().map(behind -> String.format("%.1f", behind.toMillis() / 1000.0)).orElse("?"));
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

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class CastSinkTest {

    private static final int SAMPLE_RATE = 8000;
    private static final int FRAMES = 400;
    private static final Duration FURTHEST_BEHIND = Duration.ofSeconds(8);
    private static final CastDevice KITCHEN = new CastDevice("id", "Kök", "Nest Audio",
            InetAddress.getLoopbackAddress(), CastDevice.CAST_PORT);

    @Test
    void measuresADeviceFillingItsBufferAsFallingFurtherBehind() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "BUFFERING");
            sink.open(SAMPLE_RATE);
            sink.begin("Paula Test", "");
            sink.write(tone(), FRAMES);

            assertTrue(waitFor(() -> sink.lag().isPresent()), "the device said where it is");
            assertTrue(sink.lag().orElseThrow().compareTo(Duration.ZERO) > 0,
                    "sound written that a device buffering has not played is sound it runs behind");
        }
    }

    /**
     * A device that gets nowhere while it still fetches otherwise falls behind for good, since the player
     * goes on at its own pace and the device never makes the seconds up.
     */
    @Test
    void holdsThePlayerBackWhenAPlayingDeviceGetsNowhere() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "PLAYING");
            sink.open(SAMPLE_RATE);
            sink.begin("Paula Test", "");
            fetch(loadedUrl(fake));

            final Thread stuck = sayItIsPlayingButNeverGetOn(fake);
            final Thread player = play(sink);
            try {
                assertTrue(waitFor(() -> behind(sink).compareTo(FURTHEST_BEHIND) >= 0),
                        "the player runs on while the device stays where it is");
                Thread.sleep(3000);

                assertFalse(behind(sink).compareTo(FURTHEST_BEHIND.plusSeconds(2)) > 0,
                        "and is then held there rather than running further ahead for the rest of the song");
            } finally {
                player.interrupt();
                stuck.interrupt();
                player.join(2000);
            }
        }
    }

    /**
     * A device saying it plays while its position stands still, which is a rebuffer as the player sees it.
     */
    private static Thread sayItIsPlayingButNeverGetOn(FakeCastDevice fake) {
        final Thread ticker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    fake.sendMediaStatus();
                    Thread.sleep(100);
                } catch (IOException e) {
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "stuck-device");
        ticker.setDaemon(true);
        ticker.start();
        return ticker;
    }

    private static Duration behind(CastSink sink) {
        return sink.lag().orElse(Duration.ZERO);
    }

    private static Thread play(CastSink sink) {
        final Thread player = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                sink.write(tone(), FRAMES);
            }
        }, "player");
        player.setDaemon(true);
        player.start();
        return player;
    }

    /**
     * The address the device was told to play, which a test fetches the way the device would.
     */
    private static URI loadedUrl(FakeCastDevice fake) {
        return fake.received().stream()
                .map(message -> CastChannel.parse(message.payload()))
                .filter(payload -> payload != null && payload.containsKey("media"))
                .map(payload -> URI.create(payload.getJsonObject("media").getString("contentId")))
                .findFirst().orElseThrow();
    }

    private static void fetch(URI url) throws IOException {
        final Socket socket = new Socket(url.getHost(), url.getPort());
        final Thread reader = new Thread(() -> {
            try (socket) {
                socket.getOutputStream().write(
                        ("GET " + url.getPath() + " HTTP/1.1\r\nHost: x\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                socket.getInputStream().readAllBytes();
            } catch (IOException e) {
                // The stream ends with the test.
            }
        }, "fetching-device");
        reader.setDaemon(true);
        reader.start();
    }

    private CastSink sink(FakeCastDevice fake) {
        return new CastSink(KITCHEN, 0, device -> new CastSession(CastChannel.over(fake.connect()), device));
    }

    private static short[] tone() {
        final short[] interleaved = new short[FRAMES * 2];
        for (int i = 0; i < interleaved.length; i++) {
            interleaved[i] = (short) (i % 2 == 0 ? 1000 : -1000);
        }
        return interleaved;
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}

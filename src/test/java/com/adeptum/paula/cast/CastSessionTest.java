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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.audio.NowPlaying;
import com.adeptum.paula.cast.CastMessages.CastMessage;
import com.adeptum.paula.cast.CastSession.Position;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class CastSessionTest {

    private static final String URL = "http://192.168.1.1:4711/paula-1.wav";
    private static final NowPlaying SONG = NowPlaying.builder().title("Paula Test").build();
    private static final CastDevice KITCHEN = new CastDevice("id", "Kök", "Nest Audio",
            InetAddress.getLoopbackAddress(), CastDevice.CAST_PORT);

    @Test
    void launchesTheReceiverAndHandsItTheStream() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastSession session = session(device)) {
            session.load(URL, NowPlaying.builder().title("Paula Test").artist("ProTracker, 4 channels")
                    .length(Duration.ofMillis(184_500)).picture("https://media.demozoo.org/s/1.png").build());

            final CastMessage launch = only(device.received(), "LAUNCH");
            assertEquals(CastMessages.RECEIVER_NAMESPACE, launch.namespace());
            assertEquals(CastSession.DEFAULT_MEDIA_RECEIVER, CastChannel.parse(launch.payload()).getString("appId"));

            final CastMessage load = awaited(device, "LOAD");
            assertTrue(device.received().stream().anyMatch(message -> message.payload().contains("CONNECT")
                    && FakeCastDevice.TRANSPORT.equals(message.destination())), "connected to the receiver's transport");

            final JsonObject media = CastChannel.parse(load.payload()).getJsonObject("media");
            assertEquals(FakeCastDevice.TRANSPORT, load.destination());
            assertEquals(URL, media.getString("contentId"));
            assertEquals("audio/wav", media.getString("contentType"));
            assertEquals("Paula Test", media.getJsonObject("metadata").getString("title"));
            assertEquals(184.5, media.getJsonNumber("duration").doubleValue(), "which a screen draws its progress against");
            assertEquals("https://media.demozoo.org/s/1.png",
                    media.getJsonObject("metadata").getJsonArray("images").getJsonObject(0).getString("url"));
            assertTrue(CastChannel.parse(load.payload()).getBoolean("autoplay"));
        }
    }

    @Test
    void carriesThePositionForwardWhileTheDevicePlays() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastSession session = session(device)) {
            device.playingAt(10, "PLAYING");
            session.load(URL, SONG);

            assertTrue(waitFor(() -> session.position().isPresent()));
            final double first = session.position().orElseThrow().now();
            Thread.sleep(60);
            final double later = session.position().orElseThrow().now();

            assertTrue(first >= 10, "starts where the device said, at " + first);
            assertTrue(later > first, "and moves on with the clock");
        }
    }

    @Test
    void holdsThePositionWhileTheDeviceIsBuffering() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastSession session = session(device)) {
            device.playingAt(4, "BUFFERING");
            session.load(URL, SONG);

            assertTrue(waitFor(() -> session.position().isPresent()), "the device said where it is");
            Thread.sleep(40);
            assertEquals(4, session.position().orElseThrow().now());
            assertFalse(session.isFinished());
        }
    }

    @Test
    void knowsWhenTheDeviceHasPlayedItAll() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastSession session = session(device)) {
            device.playingAt(0, "IDLE");
            session.load(URL, SONG);
            assertFalse(session.isFinished(), "idle before it ever played is not finished");

            device.playingAt(5, "PLAYING");
            device.sendMediaStatus();
            assertTrue(waitFor(() -> session.position().filter(Position::isPlaying).isPresent()));

            device.playingAt(31, "IDLE");
            device.sendMediaStatus();
            assertTrue(waitFor(session::isFinished));
        }
    }

    @Test
    void asksWhereTheDeviceIs() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastSession session = session(device)) {
            session.load(URL, SONG);
            session.poll();

            final CastMessage status = only(device.received(), "GET_STATUS");
            assertEquals(CastMessages.MEDIA, status.namespace());
            assertEquals(FakeCastDevice.TRANSPORT, status.destination());
        }
    }

    @Test
    void stopsTheReceiverWhenClosed() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice()) {
            final CastSession session = session(device);
            session.load(URL, SONG);
            session.close();

            assertTrue(waitFor(() -> device.received().stream().anyMatch(message -> message.payload().contains("STOP"))));
            assertEquals(FakeCastDevice.SESSION,
                    CastChannel.parse(only(device.received(), "STOP").payload()).getString("sessionId"));
        }
    }

    @Test
    void refusesADeviceThatWillNotLaunchTheReceiver() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastSession session = session(device)) {
            device.answer("LAUNCH", request -> CastChannel.object("RECEIVER_STATUS")
                    .add("status", Json.createObjectBuilder().add("volume", Json.createObjectBuilder().add("level", 1))));

            assertThrows(IOException.class, () -> session.load(URL, SONG));
        }
    }

    private static CastSession session(FakeCastDevice device) throws IOException {
        return new CastSession(CastChannel.over(device.connect()), KITCHEN);
    }

    /**
     * The one message of this type once it has arrived, for what the device is told without an answer being
     * waited for.
     */
    private static CastMessage awaited(FakeCastDevice device, String type) throws InterruptedException {
        assertTrue(waitFor(() -> device.received().stream().anyMatch(message -> type.equals(typeOf(message)))),
                "the device was sent a " + type);
        return only(device.received(), type);
    }

    private static CastMessage only(List<CastMessage> messages, String type) {
        final List<CastMessage> matching = messages.stream().filter(message -> type.equals(typeOf(message))).toList();
        assertEquals(1, matching.size(), "one " + type);
        return matching.getFirst();
    }

    private static String typeOf(CastMessage message) {
        final JsonObject payload = CastChannel.parse(message.payload());
        return payload == null ? "" : payload.getString("type", "");
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        for (int tries = 0; tries < 200; tries++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }
}

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

import com.adeptum.paula.cast.CastMessages.CastMessage;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class CastChannelTest {

    private static final Duration PATIENCE = Duration.ofSeconds(5);
    private static final Duration IMPATIENCE = Duration.ofMillis(200);

    @Test
    void opensTheConnectionTheDeviceWantsFirst() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastChannel channel = CastChannel.over(device.connect())) {
            device.awaitConnection();
            channel.request(CastMessages.RECEIVER, CastMessages.RECEIVER_NAMESPACE, CastChannel.object("GET_STATUS"), PATIENCE);

            final CastMessage first = device.received().getFirst();
            assertEquals(CastMessages.CONNECTION, first.namespace());
            assertEquals("CONNECT", CastChannel.parse(first.payload()).getString("type"));
            assertEquals(CastMessages.RECEIVER, first.destination());
        }
    }

    @Test
    void matchesAnAnswerToTheRequestThatAskedForIt() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastChannel channel = CastChannel.over(device.connect())) {
            final JsonObject status = channel.request(CastMessages.RECEIVER, CastMessages.RECEIVER_NAMESPACE,
                    CastChannel.object("GET_STATUS"), PATIENCE);

            assertEquals("RECEIVER_STATUS", status.getString("type"));
            assertEquals(1, status.getInt("requestId"));
        }
    }

    @Test
    void answersTheDevicesPingWithAPong() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastChannel channel = CastChannel.over(device.connect())) {
            device.awaitConnection();
            device.send(CastMessages.RECEIVER, CastMessages.HEARTBEAT, CastChannel.object("PING"));

            assertTrue(waitFor(() -> device.received().stream()
                    .anyMatch(message -> message.payload().contains("PONG"))), "the pong came back");
        }
    }

    @Test
    void handsMessagesTheDeviceSendsOfItsOwnAccordToListeners() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastChannel channel = CastChannel.over(device.connect())) {
            final CompletableFuture<CastMessage> heard = new CompletableFuture<>();
            channel.listen(heard::complete);
            device.awaitConnection();
            device.playingAt(12.5, "PLAYING");
            device.sendMediaStatus();

            final CastMessage message = heard.get(PATIENCE.toSeconds(), TimeUnit.SECONDS);
            assertEquals(CastMessages.MEDIA, message.namespace());
            assertEquals("MEDIA_STATUS", CastChannel.parse(message.payload()).getString("type"));
        }
    }

    @Test
    void givesUpOnADeviceThatStaysQuiet() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastChannel channel = CastChannel.over(device.connect())) {
            device.answer("GET_STATUS", request -> null);

            assertThrows(IOException.class, () -> channel.request(CastMessages.RECEIVER,
                    CastMessages.RECEIVER_NAMESPACE, CastChannel.object("GET_STATUS"), IMPATIENCE));
        }
    }

    @Test
    void closesWhenTheDeviceHangsUp() throws Exception {
        try (FakeCastDevice device = new FakeCastDevice(); CastChannel channel = CastChannel.over(device.connect())) {
            device.awaitConnection();
            device.dropConnection();

            assertTrue(waitFor(() -> !channel.isOpen()), "the channel noticed");
            assertFalse(channel.isOpen());
        }
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        final long until = System.currentTimeMillis() + PATIENCE.toMillis();
        while (System.currentTimeMillis() < until) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }
}

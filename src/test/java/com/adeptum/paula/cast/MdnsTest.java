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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The fixtures are answers a Google Nest Hub and a Samsung television with Chromecast built in gave to one
 * question on a home network, with their identifiers replaced by fixed ones of the same length.
 */
class MdnsTest {

    private static final String NEST_HUB = "/cast/nest-hub.bin";
    private static final String TELEVISION = "/cast/samsung-tv.bin";
    private static final String FAKE_ID = "0123456789abcdef0123456789abcdef";

    @Test
    void asksForTheCastServiceAndAnAnswerStraightBack() {
        final String query = HexFormat.of().formatHex(Mdns.query());

        assertTrue(query.startsWith("000000000001000000000000"), "one question, nothing else");
        assertTrue(query.contains(HexFormat.of().formatHex("_googlecast".getBytes())));
        assertTrue(query.endsWith("000c8001"), "a pointer record, answered to the asker");
    }

    @Test
    void readsTheDeviceOutOfAnAnswer() throws IOException {
        final CastDevice device = only(Mdns.devices(fixture(NEST_HUB)));

        assertEquals("Evander", device.name());
        assertEquals("Google Nest Hub", device.model());
        assertEquals(FAKE_ID, device.id());
        assertEquals(CastDevice.CAST_PORT, device.port());
        assertEquals("192.168.1.107", device.address().getHostAddress());
    }

    @Test
    void readsATelevisionWithChromecastBuiltIn() throws IOException {
        final CastDevice device = only(Mdns.devices(fixture(TELEVISION)));

        assertEquals("QLED", device.name());
        assertEquals("Q70D_NM2", device.model());
        assertEquals("QLED (Q70D_NM2)", device.describe());
    }

    @Test
    void readsNothingOutOfAnAnswerCutShort() throws IOException {
        final byte[] packet = fixture(NEST_HUB);

        assertTrue(Mdns.devices(Arrays.copyOf(packet, packet.length / 2)).isEmpty());
        assertTrue(Mdns.devices(new byte[0]).isEmpty());
    }

    private static CastDevice only(List<CastDevice> devices) {
        assertEquals(1, devices.size(), "one device answered");
        return devices.getFirst();
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = MdnsTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name);
            return in.readAllBytes();
        }
    }
}

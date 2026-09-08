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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.adeptum.paula.cast.CastMessages.CastMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * The bytes on the wire as the protocol buffer rules lay them out: the version as field one, the three names
 * and the payload as length-prefixed strings, the payload type between them, and the bytes a real device sent
 * back read the same way.
 */
class CastMessagesTest {

    private static final CastMessage CONNECT = new CastMessage(CastMessages.SENDER, CastMessages.RECEIVER,
            CastMessages.CONNECTION, "{\"type\":\"CONNECT\"}");

    /**
     * Field one, version zero; field two, "sender-0"; field three, "receiver-0"; field four, the connection
     * namespace; field five, string payload; field six, the JSON.
     */
    private static final String CONNECT_HEX = "0800"
            + "1208" + hex("sender-0")
            + "1a0a" + hex("receiver-0")
            + "2228" + hex(CastMessages.CONNECTION)
            + "2800"
            + "3212" + hex("{\"type\":\"CONNECT\"}");

    @Test
    void writesTheFieldsInOrderWithTheirLengths() {
        assertEquals(CONNECT_HEX, HexFormat.of().formatHex(CONNECT.encode()));
    }

    @Test
    void readsBackWhatItWrote() throws IOException {
        assertEquals(CONNECT, CastMessages.decode(CONNECT.encode()));
    }

    @Test
    void readsAMessageLongerThanAVarintByte() throws IOException {
        final CastMessage answer = new CastMessage(CastMessages.RECEIVER, CastMessages.SENDER,
                CastMessages.RECEIVER_NAMESPACE, "{\"status\":\"" + "x".repeat(300) + "\"}");

        assertEquals(answer, CastMessages.decode(answer.encode()));
    }

    @Test
    void skipsAFieldItDoesNotKnow() throws IOException {
        final byte[] encoded = CONNECT.encode();
        final byte[] withExtra = Arrays.copyOf(encoded, encoded.length + 2);
        withExtra[encoded.length] = 0x40;
        withExtra[encoded.length + 1] = 0x05;

        assertEquals(CONNECT, CastMessages.decode(withExtra), "a varint field eight is stepped over");
    }

    @Test
    void refusesAMessageCutInTheMiddleOfAString() {
        final byte[] encoded = CONNECT.encode();

        assertThrows(IOException.class, () -> CastMessages.decode(Arrays.copyOf(encoded, encoded.length - 5)));
    }

    private static String hex(String text) {
        return HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));
    }
}

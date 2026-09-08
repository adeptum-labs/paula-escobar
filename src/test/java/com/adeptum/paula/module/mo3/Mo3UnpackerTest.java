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

package com.adeptum.paula.module.mo3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The control stream reduces to something writable by hand when nothing repeats: the first byte goes through
 * untouched, and after it every eight literals are announced by a control byte of zero bits.
 */
class Mo3UnpackerTest {

    private static final int LITERALS_PER_CONTROL_BYTE = 8;
    private static final int FIRST_BIT_SET = 0x80;

    @Test
    void copiesAStreamThatRepeatsNothing() throws IOException {
        final byte[] wanted = plain(20);

        final Mo3Unpacker.Unpacked unpacked = Mo3Unpacker.unpack(stored(wanted), 0, wanted.length);

        assertArrayEquals(wanted, unpacked.music());
    }

    @Test
    void copiesAStreamShorterThanOneControlByte() throws IOException {
        final byte[] wanted = plain(3);

        assertArrayEquals(wanted, Mo3Unpacker.unpack(stored(wanted), 0, wanted.length).music());
    }

    @Test
    void saysWhereTheStreamEnded() throws IOException {
        final byte[] wanted = plain(20);
        final byte[] packed = stored(wanted);

        assertEquals(packed.length, Mo3Unpacker.unpack(packed, 0, wanted.length).end());
    }

    @Test
    void unpacksFromWhereItIsToldTo() throws IOException {
        final byte[] wanted = plain(20);
        final byte[] packed = stored(wanted);
        final byte[] preceded = new byte[packed.length + 4];
        System.arraycopy(packed, 0, preceded, 4, packed.length);

        assertArrayEquals(wanted, Mo3Unpacker.unpack(preceded, 4, wanted.length).music());
    }

    @Test
    void refusesAStreamThatEndsBeforeTheLengthItStates() {
        final byte[] wanted = plain(20);
        final byte[] packed = stored(wanted);

        assertRefused(Arrays.copyOf(packed, packed.length - 4), wanted.length);
    }

    @Test
    void refusesARepeatFromBeforeAnythingWasWritten() {
        assertRefused(new byte[] {1, (byte) FIRST_BIT_SET, 0, 0, 0, 0}, 16);
    }

    @Test
    void refusesAnEmptyStream() {
        assertRefused(new byte[0], 8);
    }

    private static void assertRefused(byte[] packed, int unpackedLength) {
        final IOException refused = assertThrows(IOException.class, () -> Mo3Unpacker.unpack(packed, 0, unpackedLength));

        assertNotNull(refused.getMessage(), "the refusal says what was wrong with the stream");
    }

    private static byte[] plain(int length) {
        final byte[] wanted = new byte[length];
        for (int at = 0; at < length; at++) {
            wanted[at] = (byte) (at * 7 + 1);
        }
        return wanted;
    }

    /**
     * The same bytes as a control stream that only ever says "copy the next one".
     */
    private static byte[] stored(byte[] data) {
        final ByteArrayOutputStream packed = new ByteArrayOutputStream();
        packed.write(data[0]);
        for (int at = 1; at < data.length; at += LITERALS_PER_CONTROL_BYTE) {
            packed.write(0);
            packed.write(data, at, Math.min(LITERALS_PER_CONTROL_BYTE, data.length - at));
        }
        return packed.toByteArray();
    }
}

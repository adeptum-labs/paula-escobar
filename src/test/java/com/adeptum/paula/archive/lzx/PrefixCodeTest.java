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

package com.adeptum.paula.archive.lzx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class PrefixCodeTest {

    /**
     * Canonical codes for lengths {2, 1, 3, 3} are A=10, B=0, C=110, D=111; the stream B A C D is the bits
     * 0 10 110 111, packed low bit first into the 16-bit word 0b1_1101_1010 = 0x01DA, stored high byte first.
     */
    private static final byte[] B_A_C_D = {0x01, (byte) 0xDA};

    @Test
    void decodesCanonicalCodesHighBitFirst() throws IOException {
        final PrefixCode code = PrefixCode.fromLengths(new int[] {2, 1, 3, 3}, 3);
        final BitReader reader = new BitReader(B_A_C_D, 0, B_A_C_D.length);
        assertEquals(1, code.decode(reader));
        assertEquals(0, code.decode(reader));
        assertEquals(2, code.decode(reader));
        assertEquals(3, code.decode(reader));
    }

    @Test
    void skipsSymbolsWithoutACode() throws IOException {
        final PrefixCode code = PrefixCode.fromLengths(new int[] {0, 1, 0, 1}, 4);
        final byte[] stream = {0x00, 0x02};
        final BitReader reader = new BitReader(stream, 0, stream.length);
        assertEquals(1, code.decode(reader));
        assertEquals(3, code.decode(reader));
    }

    @Test
    void unassignedCodesAreRejected() {
        final PrefixCode code = PrefixCode.fromLengths(new int[] {1, 0, 0, 0}, 2);
        final byte[] stream = {0x00, 0x01};
        assertThrows(IOException.class, () -> code.decode(new BitReader(stream, 0, stream.length)));
    }
}

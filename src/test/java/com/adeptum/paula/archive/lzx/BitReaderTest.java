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

package com.adeptum.paula.archive.lzx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class BitReaderTest {

    private static final byte[] DATA = {0x12, 0x34, 0x56, 0x78};

    @Test
    void readsWordsHighByteFirstButBitsFromTheLowEnd() throws IOException {
        final BitReader reader = new BitReader(DATA, 0, DATA.length);
        assertEquals(0x4, reader.bits(4));
        assertEquals(0x23, reader.bits(8));
        assertEquals(0x1, reader.bits(4));
        assertEquals(0x5678, reader.bits(16));
    }

    @Test
    void readsSingleBitsAcrossWordBoundaries() throws IOException {
        final BitReader reader = new BitReader(DATA, 0, DATA.length);
        assertEquals(0x234, reader.bits(12));
        assertEquals(1, reader.bit());
        assertEquals(0, reader.bit());
        assertEquals(0, reader.bit());
        assertEquals(0, reader.bit());
        assertEquals(0x78, reader.bits(8));
    }

    @Test
    void zeroBitsNeedNoInput() throws IOException {
        assertEquals(0, new BitReader(new byte[0], 0, 0).bits(0));
    }

    @Test
    void readingPastTheEndFails() throws IOException {
        final BitReader reader = new BitReader(DATA, 2, 2);
        reader.bits(16);
        assertThrows(IOException.class, reader::bit);
    }
}

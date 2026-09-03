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

import java.io.IOException;

/**
 * LZX bit streams are made of 16-bit words stored high byte first whose bits are consumed from the low end.
 */
final class BitReader {

    private static final int WORD_BITS = 16;
    private static final int BYTE_MASK = 0xFF;

    private final byte[] data;
    private final int end;
    private int position;
    private long buffer;
    private int available;

    BitReader(byte[] data, int offset, int length) {
        this.data = data;
        this.position = offset;
        this.end = offset + length;
    }

    int bits(int count) throws IOException {
        while (available < count) {
            fill();
        }
        final int value = (int) (buffer & ((1L << count) - 1));
        buffer >>>= count;
        available -= count;
        return value;
    }

    int bit() throws IOException {
        return bits(1);
    }

    private void fill() throws IOException {
        if (position + 1 >= end) {
            throw new IOException("Unexpected end of LZX data");
        }
        final int word = ((data[position] & BYTE_MASK) << 8) | (data[position + 1] & BYTE_MASK);
        position += 2;
        buffer |= (long) word << available;
        available += WORD_BITS;
    }
}

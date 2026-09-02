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

package com.adeptum.paula.archive.xpk;

import java.io.IOException;

/**
 * A packed chunk read from both ends: words are consumed from the front and single bytes from the back, and the
 * two must never cross. Bit readers on top of it each keep their own partial word, so the order in which they
 * fetch words follows the order in which the decoder asks for bits.
 */
final class PackedBytes {

    private static final int BYTE_MASK = 0xFF;

    private final byte[] data;
    private int front;
    private int back;

    PackedBytes(byte[] data) {
        this.data = data;
        this.back = data.length;
    }

    int readFront() throws IOException {
        if (front >= back) {
            throw new IOException("Packed data ran out");
        }
        return data[front++] & BYTE_MASK;
    }

    int readBack() throws IOException {
        if (back <= front) {
            throw new IOException("Packed data ran out");
        }
        return data[--back] & BYTE_MASK;
    }

    int readBigEndian(int bytes) throws IOException {
        int value = 0;
        for (int i = 0; i < bytes; i++) {
            value = (value << Byte.SIZE) | readFront();
        }
        return value;
    }

    interface Bits {

        int read(int count) throws IOException;
    }

    /**
     * Hands out bits from the most significant end of big-endian words of the given width.
     */
    final class MsbBits implements Bits {

        private final int wordBytes;
        private long buffer;
        private int buffered;

        MsbBits(int wordBytes) {
            this.wordBytes = wordBytes;
        }

        @Override
        public int read(int count) throws IOException {
            int result = 0;
            int remaining = count;
            while (remaining > 0) {
                if (buffered == 0) {
                    buffer = readBigEndian(wordBytes) & 0xFFFFFFFFL;
                    buffered = wordBytes * Byte.SIZE;
                }
                final int take = Math.min(remaining, buffered);
                buffered -= take;
                result = (result << take) | (int) ((buffer >>> buffered) & ((1L << take) - 1));
                remaining -= take;
            }
            return result;
        }
    }

    /**
     * Hands out bits from the least significant end of big-endian words of the given width.
     */
    final class LsbBits implements Bits {

        private final int wordBytes;
        private long buffer;
        private int buffered;

        LsbBits(int wordBytes) {
            this.wordBytes = wordBytes;
        }

        @Override
        public int read(int count) throws IOException {
            int result = 0;
            int position = 0;
            int remaining = count;
            while (remaining > 0) {
                if (buffered == 0) {
                    buffer = readBigEndian(wordBytes) & 0xFFFFFFFFL;
                    buffered = wordBytes * Byte.SIZE;
                }
                final int take = Math.min(remaining, buffered);
                result |= (int) ((buffer & ((1L << take) - 1)) << position);
                buffer >>>= take;
                buffered -= take;
                remaining -= take;
                position += take;
            }
            return result;
        }
    }
}

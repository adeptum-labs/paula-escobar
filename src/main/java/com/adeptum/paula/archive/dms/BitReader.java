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

package com.adeptum.paula.archive.dms;

import java.io.IOException;

/**
 * DMS packs its bits high bit first, one byte at a time. A "password" is not real encryption but a 16-bit
 * state XORed byte by byte that rolls forward through every byte of the archive in turn, so it is carried
 * here rather than in a layer of its own; a track that ends before its declared length is exhausted still
 * has the remaining bytes rolled through it, since whatever comes after depends on the same running state.
 */
final class BitReader {

    private final byte[] data;
    private final boolean encrypted;
    private int position;
    private int end;
    private int passAccumulator;
    private int bitBuffer;
    private int bitCount;

    BitReader(byte[] data, boolean encrypted) {
        this.data = data;
        this.encrypted = encrypted;
    }

    void reset(int offset, int length) {
        this.position = offset;
        this.end = offset + length;
        this.bitBuffer = 0;
        this.bitCount = 0;
    }

    void setPassword(int seed) {
        this.passAccumulator = seed;
    }

    int bits(int count) throws IOException {
        while (bitCount < count) {
            bitBuffer = bitBuffer << Byte.SIZE | readByte();
            bitCount += Byte.SIZE;
        }
        bitCount -= count;
        return (bitBuffer >>> bitCount) & ((1 << count) - 1);
    }

    int bit() throws IOException {
        return bits(1);
    }

    void skipToEnd() throws IOException {
        while (position < end) {
            readByte();
        }
    }

    private int readByte() throws IOException {
        if (position >= end) {
            throw new IOException("Unexpected end of DMS data");
        }
        final int raw = data[position++] & 0xFF;
        if (!encrypted) {
            return raw;
        }
        final int decoded = raw ^ (passAccumulator & 0xFF);
        passAccumulator = ((passAccumulator >>> 1) + raw) & 0xFFFF;
        return decoded;
    }
}

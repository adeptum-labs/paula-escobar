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

package com.adeptum.paula.archive.xpk;

import java.io.IOException;

/**
 * The output of one chunk, written front to back, with LZ77 copies from what was already written.
 */
final class UnpackedBytes {

    private final byte[] data;
    private int position;

    UnpackedBytes(int size) {
        this.data = new byte[size];
    }

    boolean full() {
        return position == data.length;
    }

    int remaining() {
        return data.length - position;
    }

    void write(int value) throws IOException {
        if (position >= data.length) {
            throw new IOException("Unpacked data exceeds the declared size");
        }
        data[position++] = (byte) value;
    }

    /**
     * Copies count bytes from distance bytes back, overlapping as LZ77 does, and returns the last byte copied.
     */
    int copy(int distance, int count) throws IOException {
        if (distance <= 0 || distance > position || position + count > data.length) {
            throw new IOException("Invalid copy in packed data");
        }
        int last = 0;
        for (int i = 0; i < count; i++, position++) {
            last = data[position - distance] & 0xFF;
            data[position] = (byte) last;
        }
        return last;
    }

    byte[] toArray() {
        return data;
    }
}

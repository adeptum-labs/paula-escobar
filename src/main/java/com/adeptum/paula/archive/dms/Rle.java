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

/**
 * The stage a HEAVY track can ask for after its own decoding: a run of one repeated byte is written as the
 * escape 0x90 followed by how many times to repeat it, so the byte 0x90 itself needs an escape of its own.
 * A stream that runs out mid-run is expanded as far as it reached and left silent after, the same tolerance
 * the other crunched formats give theirs.
 */
final class Rle {

    private static final int ESCAPE = 0x90;
    private static final int LONG_RUN = 0xFF;

    private Rle() {
    }

    static byte[] unpack(byte[] packed, int length) {
        final byte[] output = new byte[length];
        int in = 0;
        int out = 0;
        while (out < length && in < packed.length) {
            int value = packed[in++] & 0xFF;
            int count = 1;
            if (value == ESCAPE && in < packed.length) {
                count = packed[in++] & 0xFF;
                if (count == 0) {
                    count = 1;
                } else if (in < packed.length) {
                    value = packed[in++] & 0xFF;
                }
                if (count == LONG_RUN && in + 1 < packed.length) {
                    count = (packed[in++] & 0xFF) << 8 | (packed[in++] & 0xFF);
                }
            }
            for (int i = 0; i < count && out < length; i++) {
                output[out++] = (byte) value;
            }
        }
        return output;
    }
}

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
 *
 * The decoding scheme follows SQSHDecompressor.cpp from Teemu Suutari's
 * ancient, Copyright © Teemu Suutari, BSD 2-Clause License.
 */

package com.adeptum.paula.archive.xpk;

import java.io.IOException;

/**
 * SQSH packs sampled sound as deltas whose bit width adapts to the signal, mixed with LZ77 copies. Two counters
 * steer the mode: how many literal runs came in a row and a decaying measure of recent wide deltas.
 */
final class SqshUnpacker implements ChunkUnpacker {

    private static final int BYTE = 1;
    private static final int RAW_SIZE_LENGTH = 2;
    private static final int[][] MODE_CODES = {{1, 0b1, 0}, {2, 0b00, 1}, {3, 0b010, 2}, {4, 0b0110, 3}, {4, 0b0111, 4}};
    private static final int[][] LENGTH_CODES = {{1, 0b0, 0}, {2, 0b10, 1}, {3, 0b110, 2}, {4, 0b1110, 3}, {4, 0b1111, 4}};
    private static final int[][] DISTANCE_CODES = {{1, 0b1, 1}, {2, 0b00, 0}, {2, 0b01, 2}};
    private static final int[] LENGTH_BITS = {1, 1, 1, 3, 5};
    private static final int[] LENGTH_BASE = {0, 2, 4, 6, 14};
    private static final int[] DISTANCE_BITS = {8, 12, 14};
    private static final int[] DISTANCE_BASE = {0, 256, 4352};
    private static final int[][] WIDTH_TABLE = {
        {2, 3, 4, 5, 6, 7, 8, 0},
        {3, 2, 4, 5, 6, 7, 8, 0},
        {4, 3, 5, 2, 6, 7, 8, 0},
        {5, 4, 6, 2, 3, 7, 8, 0},
        {6, 5, 7, 2, 3, 4, 8, 0},
        {7, 6, 8, 2, 3, 4, 5, 0},
        {8, 7, 6, 2, 3, 4, 5, 0}};
    private static final int FULL_WIDTH = 8;
    private static final int LITERAL_RUNS_FOR_MODES = 8;
    private static final int MAX_LITERAL_RUNS = 31;
    private static final int QUIET_THRESHOLD = 20;
    private static final int WIDE_DELTA_WEIGHT = 8;
    private static final int MIN_MATCH = 2;

    private int width;
    private int count;
    private int literalRuns;
    private int recentWide;
    private int previousWidth;

    @Override
    public byte[] unpack(byte[] packed, int rawSize) throws IOException {
        if (packed.length < RAW_SIZE_LENGTH + 1) {
            throw new IOException("SQSH chunk too short");
        }
        final int declared = ((packed[0] & 0xFF) << Byte.SIZE) | (packed[1] & 0xFF);
        if (declared != rawSize) {
            throw new IOException("SQSH chunk size does not match the container");
        }
        final PackedBytes in = new PackedBytes(packed);
        in.readBigEndian(RAW_SIZE_LENGTH);
        final PackedBytes.MsbBits bits = in.new MsbBits(BYTE);
        final UnpackedBytes out = new UnpackedBytes(rawSize);
        int sample = in.readFront();
        out.write(sample);
        literalRuns = 0;
        recentWide = 0;
        previousWidth = 0;
        while (!out.full()) {
            width = 0;
            count = 0;
            boolean repeat = false;
            if (literalRuns >= LITERAL_RUNS_FOR_MODES) {
                switch (decode(bits, MODE_CODES)) {
                    case 0 -> {
                        if (previousWidth == FULL_WIDTH) {
                            width = FULL_WIDTH;
                            countForWidth();
                        } else {
                            width = previousWidth;
                            count = 5;
                            recentWide += WIDE_DELTA_WEIGHT;
                        }
                    }
                    case 1 -> repeat = true;
                    case 2 -> widthFromTable(2);
                    case 3 -> widthFromTable(3);
                    default -> widthFromTable(bits.read(2) + 4);
                }
            } else if (bits.read(1) == 1) {
                repeat = true;
            } else {
                count = 1;
                width = FULL_WIDTH;
            }
            if (repeat) {
                final int lengthClass = decode(bits, LENGTH_CODES);
                int length = LENGTH_BASE[lengthClass] + bits.read(LENGTH_BITS[lengthClass]) + MIN_MATCH;
                if (length >= 3 && literalRuns > 0) {
                    literalRuns--;
                    if (length > 3 && literalRuns > 0) {
                        literalRuns--;
                    }
                }
                final int distanceClass = decode(bits, DISTANCE_CODES);
                final int distance = DISTANCE_BASE[distanceClass] + bits.read(DISTANCE_BITS[distanceClass]) + 1;
                length = Math.min(length, out.remaining());
                sample = out.copy(distance, length);
            } else {
                final int deltas = Math.min(count, out.remaining());
                for (int i = 0; i < deltas; i++) {
                    sample = (sample - signed(bits.read(width), width)) & 0xFF;
                    out.write(sample);
                }
                if (literalRuns != MAX_LITERAL_RUNS) {
                    literalRuns++;
                }
                previousWidth = width;
            }
            recentWide -= recentWide >> 3;
        }
        return out.toArray();
    }

    private void widthFromTable(int change) throws IOException {
        if (previousWidth < 2 || change == 0) {
            throw new IOException("Invalid SQSH width change");
        }
        width = WIDTH_TABLE[previousWidth - 2][change - 1];
        if (width == 0) {
            throw new IOException("Invalid SQSH width");
        }
        countForWidth();
    }

    private void countForWidth() {
        if (width == FULL_WIDTH) {
            if (recentWide < QUIET_THRESHOLD) {
                count = 1;
            } else {
                count = 2;
                recentWide += WIDE_DELTA_WEIGHT;
            }
        } else {
            count = 5;
            recentWide += WIDE_DELTA_WEIGHT;
        }
    }

    private static int decode(PackedBytes.MsbBits bits, int[][] codes) throws IOException {
        int code = 0;
        for (int length = 1; length <= 4; length++) {
            code = (code << 1) | bits.read(1);
            for (final int[] candidate : codes) {
                if (candidate[0] == length && candidate[1] == code) {
                    return candidate[2];
                }
            }
        }
        throw new IOException("Invalid SQSH code");
    }

    private static int signed(int value, int bits) {
        return (value & (1 << (bits - 1))) != 0 ? value | (-1 << bits) : value;
    }
}

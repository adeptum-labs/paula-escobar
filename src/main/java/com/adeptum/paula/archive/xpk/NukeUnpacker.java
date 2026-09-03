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
 * The decoding scheme follows NUKEDecompressor.cpp from Teemu Suutari's
 * ancient, Copyright © Teemu Suutari, BSD 2-Clause License.
 */

package com.adeptum.paula.archive.xpk;

import java.io.IOException;

/**
 * NUKE is an LZ77 scheme with literal bytes stored backwards from the end of the chunk and four separate bit
 * streams from the front; DUKE is NUKE followed by delta decoding, which suits sampled sound.
 */
final class NukeUnpacker implements ChunkUnpacker {

    private static final int WORD = 2;
    private static final int LONG_WORD = 4;
    private static final int[] DISTANCE_BITS = {4, 6, 8, 9, 4, 7, 9, 11, 13, 14, 5, 7, 9, 11, 13, 14};
    private static final int[] DISTANCE_BASE = {0, 16, 80, 336, 0, 16, 144, 656, 2704, 10896, 0, 32, 160, 672, 2720, 10912};
    private static final int SHORT_MATCH_CLASSES = 4;
    private static final int MEDIUM_MATCH_CLASSES = 10;

    private final boolean delta;

    NukeUnpacker(boolean delta) {
        this.delta = delta;
    }

    @Override
    public byte[] unpack(byte[] packed, int rawSize) throws IOException {
        final PackedBytes in = new PackedBytes(packed);
        final PackedBytes.MsbBits flagBits = in.new MsbBits(WORD);
        final PackedBytes.MsbBits pairBits = in.new MsbBits(WORD);
        final PackedBytes.LsbBits nibbleBits = in.new LsbBits(LONG_WORD);
        final PackedBytes.MsbBits distanceBits = in.new MsbBits(WORD);
        final UnpackedBytes out = new UnpackedBytes(rawSize);
        while (true) {
            if (flagBits.read(1) == 0) {
                final int literals = flagBits.read(1) == 1 ? 1 : runLength(pairBits, 2, 5, 3);
                for (int i = 0; i < literals; i++) {
                    out.write(in.readBack());
                }
            }
            if (out.full()) {
                break;
            }
            final int distanceClass = nibbleBits.read(4);
            final int distance = DISTANCE_BASE[distanceClass] + distanceBits.read(DISTANCE_BITS[distanceClass]);
            out.copy(distance, matchLength(distanceClass, pairBits, nibbleBits));
        }
        return delta ? deltaDecode(out.toArray()) : out.toArray();
    }

    private static int matchLength(int distanceClass, PackedBytes.Bits pairBits, PackedBytes.Bits nibbleBits) throws IOException {
        if (distanceClass < SHORT_MATCH_CLASSES) {
            return 2;
        }
        if (distanceClass < MEDIUM_MATCH_CLASSES) {
            return 3;
        }
        final int code = pairBits.read(2);
        return code != 0 ? 3 + 4 - code : 6 + runLength(nibbleBits, 4, 16, 15);
    }

    /**
     * A run count is sent as codes where zero means "add the maximum and keep going".
     */
    private static int runLength(PackedBytes.Bits bits, int width, int base, int onZero) throws IOException {
        int count = 0;
        int code;
        do {
            code = bits.read(width);
            count += code != 0 ? base - code : onZero;
        } while (code == 0);
        return count;
    }

    private static byte[] deltaDecode(byte[] data) {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += data[i];
            data[i] = (byte) sum;
        }
        return data;
    }
}

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
 * The algorithm follows DMSDecompressor.cpp from Teemu Suutari's ancient,
 * Copyright © Teemu Suutari, BSD 2-Clause License.
 */

package com.adeptum.paula.archive.dms;

import java.io.IOException;
import java.util.Arrays;

/**
 * HEAVY1 and HEAVY2 are the same LZH-style coder over a 4 KiB or 8 KiB window, one literal-or-length table and
 * one offset-length table read once and kept until a track says to read them again. Both the window and the
 * "last offset used" carry across every track of the disk unless a track says otherwise, so one instance
 * plays a whole archive rather than one track.
 */
final class HeavyUnpacker {

    private static final int SYMBOL_COUNT_BITS = 9;
    private static final int SYMBOL_LENGTH_BITS = 5;
    private static final int OFFSET_COUNT_BITS = 5;
    private static final int OFFSET_LENGTH_BITS = 4;
    private static final int MAX_CODE_LENGTH = 31;

    private static final int FIRST_MATCH_SYMBOL = 256;
    private static final int MINIMUM_MATCH = 3;
    private static final int MATCH_SYMBOL_OFFSET = FIRST_MATCH_SYMBOL - MINIMUM_MATCH;

    private static final int MAX_WINDOW = 8192;
    private static final int SMALL_MASK = 0xFFF;
    private static final int LARGE_MASK = 0x1FFF;
    private static final int SMALL_OFFSET_WIDTH = 13;
    private static final int LARGE_OFFSET_WIDTH = 14;

    private final byte[] window = new byte[MAX_WINDOW];
    private int position;
    private HeavyTable symbolTable;
    private HeavyTable offsetTable;
    private boolean lastOffsetKnown;
    private int lastOffset;

    void resetWindow() {
        Arrays.fill(window, (byte) 0);
        position = 0;
    }

    byte[] unpack(BitReader bits, boolean readTables, boolean wideWindow, int length) throws IOException {
        if (readTables) {
            symbolTable = readTable(bits, SYMBOL_COUNT_BITS, SYMBOL_LENGTH_BITS);
            offsetTable = readTable(bits, OFFSET_COUNT_BITS, OFFSET_LENGTH_BITS);
        }
        if (!lastOffsetKnown) {
            lastOffset = wideWindow ? 0 : -1;
            lastOffsetKnown = true;
        }
        final int mask = wideWindow ? LARGE_MASK : SMALL_MASK;
        final int noRepeatWidth = wideWindow ? LARGE_OFFSET_WIDTH : SMALL_OFFSET_WIDTH;
        final byte[] output = new byte[length];
        int written = 0;
        while (written < length) {
            final int symbol = symbolTable.decode(bits);
            if (symbol < FIRST_MATCH_SYMBOL) {
                window[position] = (byte) symbol;
                output[written++] = (byte) symbol;
                position = (position + 1) & mask;
                continue;
            }
            final int count = symbol - MATCH_SYMBOL_OFFSET;
            final int offsetWidth = offsetTable.decode(bits);
            if (offsetWidth != noRepeatWidth) {
                lastOffset = offsetWidth == 0 ? 0 : (1 << (offsetWidth - 1)) | bits.bits(offsetWidth - 1);
            }
            int source = (position - lastOffset - 1) & mask;
            for (int copied = 0; copied < count && written < length; copied++) {
                final byte value = window[source];
                window[position] = value;
                output[written++] = value;
                position = (position + 1) & mask;
                source = (source + 1) & mask;
            }
        }
        return output;
    }

    /**
     * A count of zero means the track held too little of this alphabet to earn a table, so what follows names
     * its one symbol directly rather than a set of code lengths.
     */
    private static HeavyTable readTable(BitReader bits, int countBits, int valueBits) throws IOException {
        final int count = bits.bits(countBits);
        if (count == 0) {
            return HeavyTable.constant(bits.bits(countBits));
        }
        final int[] lengths = new int[count];
        long sum = 0;
        for (int symbol = 0; symbol < count; symbol++) {
            final int length = bits.bits(valueBits);
            lengths[symbol] = length;
            if (length != 0) {
                sum += 1L << (32 - length);
                if (sum > (1L << 32)) {
                    throw new IOException("Invalid Huffman table in DMS data");
                }
            }
        }
        return HeavyTable.of(PrefixCode.fromLengths(lengths, MAX_CODE_LENGTH));
    }
}

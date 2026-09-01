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

import java.io.IOException;

/**
 * A canonical Huffman code built from code lengths: shorter codes get the smaller values, symbols of equal length
 * are numbered in symbol order, and the stream carries each code high bit first.
 */
final class PrefixCode {

    private final int[] countPerLength;
    private final int[] symbolsByCode;
    private final int maxLength;

    private PrefixCode(int[] countPerLength, int[] symbolsByCode, int maxLength) {
        this.countPerLength = countPerLength;
        this.symbolsByCode = symbolsByCode;
        this.maxLength = maxLength;
    }

    static PrefixCode fromLengths(int[] lengths, int maxLength) {
        final int[] count = new int[maxLength + 1];
        for (final int length : lengths) {
            count[length]++;
        }
        count[0] = 0;
        final int[] offsets = new int[maxLength + 2];
        for (int length = 1; length <= maxLength; length++) {
            offsets[length + 1] = offsets[length] + count[length];
        }
        final int[] symbols = new int[lengths.length];
        for (int symbol = 0; symbol < lengths.length; symbol++) {
            if (lengths[symbol] != 0) {
                symbols[offsets[lengths[symbol]]++] = symbol;
            }
        }
        return new PrefixCode(count, symbols, maxLength);
    }

    int decode(BitReader in) throws IOException {
        int code = 0;
        int first = 0;
        int index = 0;
        for (int length = 1; length <= maxLength; length++) {
            code |= in.bit();
            final int count = countPerLength[length];
            if (code - first < count) {
                return symbolsByCode[index + code - first];
            }
            index += count;
            first = (first + count) << 1;
            code <<= 1;
        }
        throw new IOException("Invalid prefix code in LZX data");
    }
}

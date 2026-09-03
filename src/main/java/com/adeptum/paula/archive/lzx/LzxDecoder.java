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
 * The decoding scheme follows XADLZXHandle.m from XADMaster,
 * Copyright © 2017-present MacPaw Inc., licensed under the GNU Lesser
 * General Public License version 2.1 or later and used here under the
 * GNU General Public License as section 3 of the LGPL permits.
 */

package com.adeptum.paula.archive.lzx;

import java.io.IOException;
import java.util.Arrays;

/**
 * Decompresses one LZX stream: blocks of Huffman coded literals and LZ77 matches over a 64 KB window, with the
 * main code lengths sent as deltas against the previous block.
 */
final class LzxDecoder {

    private static final int LITERALS = 256;
    private static final int MAIN_SYMBOLS = 768;
    private static final int MAIN_MAX_LENGTH = 16;
    private static final int OFFSET_SYMBOLS = 8;
    private static final int OFFSET_MAX_LENGTH = 7;
    private static final int PRE_SYMBOLS = 20;
    private static final int PRE_MAX_LENGTH = 15;
    private static final int LENGTH_MODULUS = 17;
    private static final int MIN_MATCH = 3;
    private static final int BLOCK_STORED = 1;
    private static final int BLOCK_ALIGNED_OFFSETS = 3;
    private static final int ALIGNED_OFFSET_BITS = 3;
    private static final int OFFSET_CLASS_MASK = 31;
    private static final int LENGTH_CLASS_MASK = 15;
    private static final int LENGTH_CLASS_SHIFT = 5;
    private static final int[] ADDITIONAL_BITS = {
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14};
    private static final int[] BASE = {
        0, 1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048, 3072, 4096,
        6144, 8192, 12288, 16384, 24576, 32768, 49152};

    private final BitReader in;
    private final int[] mainLengths = new int[MAIN_SYMBOLS];
    private PrefixCode mainCode;
    private PrefixCode offsetCode;
    private int blockType;
    private int blockEnd;
    private int lastOffset = 1;

    private LzxDecoder(BitReader in) {
        this.in = in;
    }

    static byte[] decode(byte[] packed, int offset, int length, int unpackedSize) throws IOException {
        return new LzxDecoder(new BitReader(packed, offset, length)).decode(unpackedSize);
    }

    private byte[] decode(int size) throws IOException {
        final byte[] out = new byte[size];
        int position = 0;
        while (position < size) {
            if (position >= blockEnd) {
                readBlockHeader(position);
            }
            final int symbol = mainCode.decode(in);
            if (symbol < LITERALS) {
                out[position++] = (byte) symbol;
                continue;
            }
            final int distance = matchDistance(symbol & OFFSET_CLASS_MASK);
            final int lengthClass = ((symbol - LITERALS) >> LENGTH_CLASS_SHIFT) & LENGTH_CLASS_MASK;
            final int length = BASE[lengthClass] + MIN_MATCH + in.bits(ADDITIONAL_BITS[lengthClass]);
            if (distance > position) {
                throw new IOException("LZX match reaches before the start of the data");
            }
            for (int i = 0; i < length && position < size; i++, position++) {
                out[position] = out[position - distance];
            }
            lastOffset = distance;
        }
        return out;
    }

    private int matchDistance(int offsetClass) throws IOException {
        final int extraBits = ADDITIONAL_BITS[offsetClass];
        final int base = BASE[offsetClass];
        if (base == 0) {
            return lastOffset;
        }
        if (blockType == BLOCK_ALIGNED_OFFSETS && extraBits >= ALIGNED_OFFSET_BITS) {
            return base + (in.bits(extraBits - ALIGNED_OFFSET_BITS) << ALIGNED_OFFSET_BITS) + offsetCode.decode(in);
        }
        return base + in.bits(extraBits);
    }

    private void readBlockHeader(int position) throws IOException {
        blockType = in.bits(3);
        if (blockType < BLOCK_STORED || blockType > BLOCK_ALIGNED_OFFSETS) {
            throw new IOException("Unknown LZX block type " + blockType);
        }
        if (blockType == BLOCK_STORED) {
            throw new IOException("Stored LZX blocks are not supported");
        }
        if (blockType == BLOCK_ALIGNED_OFFSETS) {
            final int[] lengths = new int[OFFSET_SYMBOLS];
            for (int i = 0; i < OFFSET_SYMBOLS; i++) {
                lengths[i] = in.bits(3);
            }
            offsetCode = PrefixCode.fromLengths(lengths, OFFSET_MAX_LENGTH);
        }
        final int blockSize = (in.bits(8) << 16) | (in.bits(8) << 8) | in.bits(8);
        blockEnd = position + blockSize;
        readDeltaLengths(0, LITERALS, false);
        readDeltaLengths(LITERALS, MAIN_SYMBOLS - LITERALS, true);
        mainCode = PrefixCode.fromLengths(mainLengths, MAIN_MAX_LENGTH);
    }

    /**
     * Pre-code symbols 0 to 16 give a new length relative to the old one, 17 and 18 zero runs, and 19 a run of one
     * relative length; the match half of the table uses slightly shorter run counts.
     */
    private void readDeltaLengths(int start, int count, boolean matchHalf) throws IOException {
        final int fix = matchHalf ? 1 : 0;
        final int[] preLengths = new int[PRE_SYMBOLS];
        for (int i = 0; i < PRE_SYMBOLS; i++) {
            preLengths[i] = in.bits(4);
        }
        final PrefixCode preCode = PrefixCode.fromLengths(preLengths, PRE_MAX_LENGTH);
        final int end = start + count;
        int i = start;
        while (i < end) {
            final int value = preCode.decode(in);
            final int run;
            final int length;
            if (value <= 16) {
                run = 1;
                length = (mainLengths[i] + LENGTH_MODULUS - value) % LENGTH_MODULUS;
            } else if (value == 17) {
                run = in.bits(4) + 4 - fix;
                length = 0;
            } else if (value == 18) {
                run = in.bits(5 + fix) + 20 - fix;
                length = 0;
            } else {
                run = in.bits(1) + 4 - fix;
                length = (mainLengths[i] + LENGTH_MODULUS - preCode.decode(in)) % LENGTH_MODULUS;
            }
            if (i + run > end) {
                throw new IOException("LZX code length run overflows the table");
            }
            Arrays.fill(mainLengths, i, i + run, length);
            i += run;
        }
    }
}

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
 * The layout and the sample packing follow Load_dmf.cpp of OpenMPT,
 * Copyright © 2004-2026 the OpenMPT project developers and Copyright ©
 * 1997-2003 Olivier Lapicque, licensed under the three-clause BSD licence
 * and used here under the GNU General Public License.
 */

package com.adeptum.paula.module.xtracker;

import java.io.EOFException;

/**
 * Unpacks the samples X-Tracker stores packed: a Huffman tree of up to 256 seven-bit deltas read depth first, then
 * for every byte a sign and a walk down the tree, each delta added to the running value. The bits are taken from
 * the lowest of each byte upwards.
 */
final class DmfUnpacker {

    private static final int MAX_NODES = 256;
    private static final int VALUE_BITS = 7;
    private static final int NO_BRANCH = -1;
    private static final int BYTE = 0xFF;

    private final Bits bits;
    private final int[] values = new int[MAX_NODES];
    private final int[] left = new int[MAX_NODES];
    private final int[] right = new int[MAX_NODES];
    private int nodeCount;
    private int lastNode;
    private int delta;

    private DmfUnpacker(byte[] block) {
        bits = new Bits(block);
    }

    /**
     * As many bytes as the sample holds. A stream that ends early leaves the rest silent, and so does a whole
     * sample whose tree has a root without both branches.
     */
    static byte[] unpack(byte[] block, int length) {
        final byte[] unpacked = new byte[length];
        final DmfUnpacker unpacker = new DmfUnpacker(block);
        try {
            unpacker.node();
            if (unpacker.left[0] < 0 || unpacker.right[0] < 0) {
                return unpacked;
            }
            int value = 0;
            for (int at = 0; at < length; at++) {
                value = value + unpacker.nextDelta() & BYTE;
                unpacked[at] = (byte) value;
            }
        } catch (EOFException e) {
            return unpacked;
        }
        return unpacked;
    }

    /**
     * Reads one node and, depth first, the branches it says it has. The node counts are kept exactly as
     * OpenMPT's DMFNewNode keeps them, since a tree of more than 256 nodes points past the table and the walk
     * has to stop where OpenMPT's stops.
     */
    private void node() throws EOFException {
        int current = nodeCount;
        if (current >= MAX_NODES) {
            return;
        }
        values[current] = bits.read(VALUE_BITS);
        final boolean hasLeft = bits.read(1) != 0;
        final boolean hasRight = bits.read(1) != 0;
        current = lastNode;
        if (current >= MAX_NODES) {
            return;
        }
        nodeCount++;
        lastNode = nodeCount;
        if (hasLeft) {
            left[current] = lastNode;
            node();
        } else {
            left[current] = NO_BRANCH;
        }
        lastNode = nodeCount;
        if (hasRight) {
            right[current] = lastNode;
            node();
        } else {
            right[current] = NO_BRANCH;
        }
    }

    /**
     * The delta of the leaf a walk ends on; a branch pointing past the table ends the walk on the delta found
     * last, which may be the previous byte's.
     */
    private int nextDelta() throws EOFException {
        final boolean negative = bits.read(1) != 0;
        int node = 0;
        do {
            node = bits.read(1) != 0 ? right[node] : left[node];
            if (node >= MAX_NODES) {
                break;
            }
            delta = values[node];
        } while (left[node] >= 0 && right[node] >= 0);
        return negative ? delta ^ BYTE : delta;
    }

    private static final class Bits {

        private final byte[] bytes;
        private int bit;

        private Bits(byte[] bytes) {
            this.bytes = bytes;
        }

        private int read(int width) throws EOFException {
            int value = 0;
            for (int at = 0; at < width; at++, bit++) {
                if (bit / Byte.SIZE >= bytes.length) {
                    throw new EOFException();
                }
                value |= (bytes[bit / Byte.SIZE] >> bit % Byte.SIZE & 1) << at;
            }
            return value;
        }
    }
}

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
 * The bit order follows Load_mo3.cpp of OpenMPT, Copyright © 2004-2026 the
 * OpenMPT project developers and Copyright © 1997-2003 Olivier Lapicque,
 * licensed under the three-clause BSD licence and used here under the GNU
 * General Public License.
 */

package com.adeptum.paula.module.mo3;

import java.io.EOFException;
import java.io.IOException;

/**
 * The control bits an MO3 makes its decisions from, which both the compressed music and the compressed
 * waveforms are read through.
 *
 * <p>The bits are taken off the top of a byte held in hand. An exhausted byte is refilled from the stream with
 * a marker bit put in below it, and it is that marker reaching the top which empties the byte again eight bits
 * later, so nothing has to be counted.</p>
 */
final class Mo3Bits {

    private final byte[] packed;
    private final int end;
    private int at;
    private int data;

    Mo3Bits(byte[] packed, int from, int length) {
        this.packed = packed;
        this.at = from;
        this.end = from + length;
    }

    int position() {
        return at;
    }

    boolean bit() throws IOException {
        data <<= 1;
        if ((data & 0xFF) == 0) {
            data = (next() & 0xFF) << 1 | 1;
        }
        final boolean bit = data > 0xFF;
        data &= 0xFF;
        return bit;
    }

    /**
     * The bit as the number it stands for, which is how it is shifted into a value being read a bit at a time.
     */
    int digit() throws IOException {
        return bit() ? 1 : 0;
    }

    byte next() throws IOException {
        if (at >= end) {
            throw new EOFException("The compressed stream ends before what it holds is read");
        }
        return packed[at++];
    }
}

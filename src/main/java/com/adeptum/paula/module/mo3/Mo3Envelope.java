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
 * The layout follows Load_mo3.cpp of OpenMPT, Copyright © 2004-2026 the
 * OpenMPT project developers and Copyright © 1997-2003 Olivier Lapicque,
 * licensed under the three-clause BSD licence and used here under the GNU
 * General Public License.
 */

package com.adeptum.paula.module.mo3;

import java.io.IOException;
import java.util.Arrays;

/**
 * An envelope of an MO3 instrument: up to twenty-five points, each a tick and a value, with somewhere to
 * sustain and somewhere to loop. Ticks that walk backwards are pushed forward one, since a point that arrives
 * before the one before it is a shape no player can follow.
 */
record Mo3Envelope(int flags, int nodes, int sustainStart, int sustainEnd, int loopStart, int loopEnd,
                   int[] ticks, int[] values) {

    static final int ENABLED = 0x01;
    static final int SUSTAIN = 0x02;
    static final int LOOP = 0x04;
    static final int FILTER = 0x10;
    static final int CARRY = 0x20;

    private static final int MOST_POINTS = 25;

    static Mo3Envelope read(Mo3Bytes bytes) throws IOException {
        final int flags = bytes.u8();
        final int nodes = Math.min(bytes.u8(), MOST_POINTS);
        final int sustainStart = bytes.u8();
        final int sustainEnd = bytes.u8();
        final int loopStart = bytes.u8();
        final int loopEnd = bytes.u8();

        final int[] ticks = new int[MOST_POINTS];
        final int[] values = new int[MOST_POINTS];
        for (int point = 0; point < MOST_POINTS; point++) {
            ticks[point] = bytes.s16();
            values[point] = bytes.s16();
            if (point > 0 && ticks[point] < ticks[point - 1]) {
                ticks[point] = ticks[point - 1] + 1;
            }
        }
        return new Mo3Envelope(flags, nodes, sustainStart, sustainEnd, loopStart, loopEnd,
                Arrays.copyOf(ticks, nodes), Arrays.copyOf(values, nodes));
    }

    boolean has(int flag) {
        return (flags & flag) != 0;
    }
}

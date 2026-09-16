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
 * The layout follows Load_ult.cpp of OpenMPT, Copyright © 2004-2026 the
 * OpenMPT project developers and Copyright © 1997-2003 Olivier Lapicque,
 * which ports Storlek's reader from Schism Tracker; licensed under the
 * three-clause BSD licence and used here under the GNU General Public
 * License.
 */

package com.adeptum.paula.module.ult;

/**
 * One sample: its names, its loop in bytes as the file counts it, its volume from nought to two hundred and
 * fifty-five, the speed it sounds at middle C and a finetune in thirty-two-thousandths of a semitone, and its
 * data as signed values of eight or sixteen bits.
 */
public record UltSample(String name, String fileName, int loopStart, int loopEnd, int volume, int flags,
        int speed, int finetune, int[] data) {

    public static final int SIXTEEN_BIT = 4;
    public static final int LOOP = 8;
    public static final int PING_PONG_LOOP = 16;

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }
}

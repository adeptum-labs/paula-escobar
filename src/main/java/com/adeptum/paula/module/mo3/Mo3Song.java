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

/**
 * What an MO3 says about the song as a whole: how much of everything it holds, how fast it starts, and the
 * flags that say which tracker wrote it and which of that tracker's habits it wants back.
 */
record Mo3Song(int channels, int orders, int restart, int patterns, int tracks, int instruments, int samples,
               int speed, int tempo, int flags, int globalVolume, int panSeparation, int sampleVolume,
               int[] channelVolume, int[] channelPanning) {

    static final int LINEAR_SLIDES = 0x0001;
    static final int IS_S3M = 0x0002;
    static final int S3M_FAST_SLIDES = 0x0004;
    static final int IS_MTM = 0x0008;
    static final int S3M_AMIGA_LIMITS = 0x0010;
    static final int IS_MOD = 0x0080;
    static final int IS_IT = 0x0100;
    static final int INSTRUMENT_MODE = 0x0200;
    static final int IT_COMPATIBLE_GXX = 0x0400;
    static final int IT_OLD_EFFECTS = 0x0800;
    static final int MODPLUG_MODE = 0x10000;
    static final int MOD_VBLANK = 0x80000;
    static final int EXTENDED_FILTER_RANGE = 0x200000;

    /**
     * Channel volume and panning are kept for as many channels as Impulse Tracker had room for, whatever the
     * module goes on to use.
     */
    static final int CHANNELS_IN_HEADER = 64;

    static final int LARGEST_CHANNEL_COUNT = 64;

    /**
     * A panning value of its own, rather than one of the two the format spends on surround and full right.
     */
    static final int PANNING_SURROUND = 127;
    static final int PANNING_FULL_RIGHT = 255;

    Mo3Kind kind() {
        return Mo3Kind.of(flags);
    }

    boolean has(int flag) {
        return (flags & flag) != 0;
    }
}

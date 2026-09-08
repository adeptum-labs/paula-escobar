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

/**
 * What an MO3 says about one sample: how it is tuned, how loud it is, where it loops, and how its waveform is
 * packed in the half of the file the music chunk does not cover.
 *
 * <p>A negative compressed size is not a size at all but a step back to the sample this one shares its
 * waveform with.</p>
 */
record Mo3Sample(String name, String fileName, int frequency, int transpose, int volume, int panning,
                 int length, int loopStart, int loopEnd, int flags, Mo3Vibrato vibrato, int globalVolume,
                 int sustainStart, int sustainEnd, int compressedSize, int encoderDelay, int sharedOggHeader) {

    static final int SIXTEEN_BIT = 0x01;
    static final int LOOP = 0x10;
    static final int PING_PONG_LOOP = 0x20;
    static final int SUSTAIN = 0x100;
    static final int PING_PONG_SUSTAIN = 0x200;
    static final int STEREO = 0x400;

    static final int COMPRESSION_MASK = 0xF000;
    static final int MPEG = 0x1000;
    static final int OGG = 0x3000;
    static final int SHARED_OGG = 0x7000;
    static final int DELTA = 0x2000;
    static final int DELTA_PREDICTION = 0x4000;

    static final int LARGEST_PANNING = 256;

    static Mo3Sample read(Mo3Bytes bytes, int version, String name, String fileName) throws IOException {
        final int frequency = bytes.s32();
        final int transpose = bytes.s8();
        final int volume = bytes.u8();
        final int panning = bytes.u16();
        final int length = bytes.u32();
        final int loopStart = bytes.u32();
        final int loopEnd = bytes.u32();
        final int flags = bytes.u16();
        final Mo3Vibrato vibrato = Mo3Vibrato.read(bytes);
        final int globalVolume = bytes.u8();
        final int sustainStart = bytes.u32();
        final int sustainEnd = bytes.u32();
        final int compressedSize = bytes.s32();
        final int encoderDelay = bytes.u16();
        final int sharedOggHeader = version >= Mo3Reader.NEWEST_LAYOUT_FROM && sharesAnOggHeader(flags)
                ? bytes.s16() : 0;

        return new Mo3Sample(name, fileName, frequency, transpose, volume, panning, length, loopStart, loopEnd,
                flags, vibrato, globalVolume, sustainStart, sustainEnd, compressedSize, encoderDelay,
                sharedOggHeader);
    }

    private static boolean sharesAnOggHeader(int flags) {
        return (flags & COMPRESSION_MASK) == SHARED_OGG;
    }

    boolean has(int flag) {
        return (flags & flag) != 0;
    }

    int compression() {
        return flags & COMPRESSION_MASK;
    }

    int channels() {
        return has(STEREO) ? 2 : 1;
    }

    /**
     * The sample this one copies its waveform from, counted back from itself, or nothing when it has a
     * waveform of its own.
     */
    boolean isDuplicate() {
        return compressedSize < 0;
    }
}

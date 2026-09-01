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

package com.adeptum.paula.audio;

/**
 * Sample layout shared by every sink: interleaved stereo, signed 16-bit, little-endian.
 */
public final class Pcm {

    public static final int CHANNELS = 2;
    public static final int BYTES_PER_FRAME = CHANNELS * Short.BYTES;

    private Pcm() {
    }

    public static byte[] toLittleEndian(short[] interleavedStereo, int frames, byte[] target) {
        final int byteCount = frames * BYTES_PER_FRAME;
        final byte[] bytes = target.length >= byteCount ? target : new byte[byteCount];
        for (int i = 0; i < frames * CHANNELS; i++) {
            bytes[i * 2] = (byte) interleavedStereo[i];
            bytes[i * 2 + 1] = (byte) (interleavedStereo[i] >> 8);
        }
        return bytes;
    }
}

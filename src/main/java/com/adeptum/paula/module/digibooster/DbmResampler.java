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
 * The resampling follows dsp_linresampler.c of libdigibooster3, Copyright © 2014
 * Grzegorz Kraszewski, licensed under the two-clause BSD licence and used
 * here under the GNU General Public License.
 */

package com.adeptum.paula.module.digibooster;

import java.util.Arrays;

/**
 * Resamples what the wavetable plays to the mixing rate, reading the source in blocks and interpolating
 * linearly between the two frames the current fractional position falls between.
 */
final class DbmResampler {

    private static final int BLOCK = 1008;
    private static final int GUARD = 8;
    private static final int KEPT = 16;
    private static final int FRACTION_BITS = 16;
    private static final int FRACTION_MASK = 0xFFFF;

    private final DbmWavetable source;
    private final short[] buffer = new short[1024];

    private long position;
    private int step = DbmTables.UNIT;
    private boolean flushed = true;

    DbmResampler(DbmWavetable source) {
        this.source = source;
    }

    void ratio(int samplesPerFrame) {
        step = samplesPerFrame;
    }

    void flush() {
        flushed = true;
    }

    boolean pull(short[] into, int at, int frames) {
        boolean playing = true;
        int write = at;
        for (int frame = 0; frame < frames; frame++) {
            if (flushed) {
                Arrays.fill(buffer, 0, GUARD, (short) 0);
                playing = source.pull(buffer, GUARD, 1016);
                position = 0;
                flushed = false;
            }
            while (position >= (long) BLOCK << FRACTION_BITS) {
                System.arraycopy(buffer, BLOCK, buffer, 0, KEPT);
                playing = source.pull(buffer, KEPT, BLOCK);
                position -= (long) BLOCK << FRACTION_BITS;
            }
            final int whole = (int) (position >> FRACTION_BITS) + GUARD;
            final short lower = buffer[whole];
            final short upper = buffer[whole + 1];
            into[write++] = (short) (lower + (((upper - lower) * (int) (position & FRACTION_MASK)) >> FRACTION_BITS));
            position += step;
        }
        return playing;
    }
}

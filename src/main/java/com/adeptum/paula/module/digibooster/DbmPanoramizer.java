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
 *
 * The panning follows dsp_panoramizer.c of libdigibooster3, Copyright © 2014
 * Grzegorz Kraszewski, licensed under the two-clause BSD licence and used
 * here under the GNU General Public License.
 */

package com.adeptum.paula.module.digibooster;

import java.util.Arrays;

/**
 * Spreads a mono track over the stereo field by delaying one side against the other, the way two speakers reach
 * the ears at slightly different times. Loudness is left to the mixer; this only shifts the phase.
 */
final class DbmPanoramizer {

    private static final int MAX_SHIFT_MICROSECONDS = 333;
    private static final int SHIFT_SCALE = 14;
    private static final int PANNING_STEPS = 128;
    private static final int MICROSECONDS = 1000000;
    private static final int CHUNK = 1024;
    private static final int DELAY = 64;

    private final DbmResampler source;
    private final int[] shifts;
    private final short[] delayed = new short[CHUNK + DELAY];

    private int leftDelay;
    private int rightDelay;

    DbmPanoramizer(DbmResampler source, int[] shifts) {
        this.source = source;
        this.shifts = shifts;
    }

    static int[] shifts(int sampleRate) {
        final int longest = sampleRate * MAX_SHIFT_MICROSECONDS / MICROSECONDS;
        final int[] shifts = new int[PANNING_STEPS];
        for (int step = 1; step <= PANNING_STEPS; step++) {
            shifts[step - 1] = (step * step * longest) >> SHIFT_SCALE;
        }
        return shifts;
    }

    void panning(int panning) {
        leftDelay = panning > 0 ? shifts[panning - 1] : 0;
        rightDelay = panning < 0 ? shifts[-panning - 1] : 0;
    }

    void flush() {
        Arrays.fill(delayed, 0, DELAY, (short) 0);
        source.flush();
    }

    boolean pull(short[] into, int at, int frames) {
        boolean playing = true;
        int write = at;
        int left = frames;
        while (left > 0) {
            final int chunk = Math.min(left, CHUNK);
            playing = source.pull(delayed, DELAY, chunk);
            for (int frame = 0; frame < chunk; frame++) {
                into[write++] = delayed[frame + DELAY - leftDelay];
                into[write++] = delayed[frame + DELAY - rightDelay];
            }
            System.arraycopy(delayed, chunk, delayed, 0, DELAY);
            left -= chunk;
        }
        return playing;
    }
}

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
 */

package com.adeptum.paula.module.zx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BeeperSynthTest {

    private static final int RATE = 48000;
    private static final int CLOCK = 3_500_000;

    private double[] render(BeeperSynth synth, int samples) {
        final double[] out = new double[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = synth.next();
        }
        return out;
    }

    @Test
    void aStepLandsOnTheSampleItsTStateFallsIn() {
        final BeeperSynth synth = new BeeperSynth(RATE, CLOCK, 0, 0);
        synth.level(100L * CLOCK / RATE, 1);

        final double[] out = render(synth, 160);

        assertEquals(0, out[90], 0.01);
        assertTrue(out[104] > 0.45, "past the edge the output has risen, was " + out[104]);
    }

    @Test
    void aSquareWaveIsCentredOnZero() {
        final BeeperSynth synth = new BeeperSynth(RATE, CLOCK, 0, 0);
        final int half = CLOCK / 2000 / 2;
        final double[] out = new double[4000];
        int edge = 0;
        for (int i = 0; i < out.length; i++) {
            while ((long) edge * half <= (long) (i + BeeperSynth.LOOKAHEAD) * CLOCK / RATE) {
                synth.level((long) edge * half, edge % 2 == 0 ? 1 : 0);
                edge++;
            }
            out[i] = synth.next();
        }
        double sum = 0;
        double peak = 0;
        for (int i = 1000; i < 3000; i++) {
            sum += out[i];
            peak = Math.max(peak, Math.abs(out[i]));
        }

        assertEquals(0, sum / 2000, 0.02);
        assertTrue(peak > 0.3 && peak < 0.7, "a full-scale square wave peaks at about half, was " + peak);
    }

    @Test
    void aHeldLevelFadesToSilence() {
        final BeeperSynth synth = new BeeperSynth(RATE, CLOCK, 0, 0);
        synth.level(0, 1);

        final double[] out = render(synth, 48000);

        assertEquals(0, out[47999], 0.001);
    }

    @Test
    void anEdgeAtTheFurthestAllowedDistanceStillLandsOnItsSample() {
        final BeeperSynth synth = new BeeperSynth(RATE, CLOCK, 0, 0);
        synth.level((long) BeeperSynth.MAX_AHEAD * CLOCK / RATE + 1, 1);

        final double[] out = render(synth, 160);

        assertEquals(0, out[0], 0.01);
        assertTrue(out[BeeperSynth.MAX_AHEAD] > 0.9, "the edge's own sample is full, was " + out[BeeperSynth.MAX_AHEAD]);
    }

    @Test
    void startsSilentAtTheLevelItIsGiven() {
        final BeeperSynth synth = new BeeperSynth(RATE, CLOCK, 5000, 0.96);

        assertEquals(0, render(synth, 200)[199], 1e-9);
    }

    @Test
    void anEdgeBetweenTwoSamplesSplitsTheStepBetweenThem() {
        final BeeperSynth early = new BeeperSynth(RATE, CLOCK, 0, 0);
        final BeeperSynth late = new BeeperSynth(RATE, CLOCK, 0, 0);
        final BeeperSynth middle = new BeeperSynth(RATE, CLOCK, 0, 0);
        early.level(100L * CLOCK / RATE + 1, 1);
        late.level(101L * CLOCK / RATE - 1, 1);
        middle.level(100L * CLOCK / RATE + CLOCK / RATE / 2, 1);

        final double[] a = render(early, 160);
        final double[] b = render(late, 160);
        final double[] c = render(middle, 160);

        assertEquals(0.5, c[100], 0.2);

        assertTrue(a[100] > 0.9, "an edge at the start of sample 100 fills it, was " + a[100]);
        assertTrue(b[100] < 0.1, "an edge at the end of sample 100 leaves it empty, was " + b[100]);
    }
}

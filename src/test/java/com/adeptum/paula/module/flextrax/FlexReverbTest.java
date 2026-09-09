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

package com.adeptum.paula.module.flextrax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlexReverbTest {

    private static final int IMPULSE = 20000;
    private static final int LOUD = FlexEffects.LOUDEST;
    private static final int FRAMES = 20000;

    /**
     * The first loop reads a thousand frames back, so nothing of the tail can have come round again before
     * then; energy after it is the reverb rather than the reflections.
     */
    private static final int TAIL_FROM = 4000;

    @Test
    void keepsSoundingLongAfterTheMixHasStopped() {
        final int[][] frames = render(effects(LOUD, LOUD));

        assertTrue(energyIn(frames, 0, TAIL_FROM) > 0, "the reflections sound at once");
        assertTrue(energyIn(frames, TAIL_FROM, FRAMES) > 0, "and a tail is still running much later");
    }

    @Test
    void fadesTheTailAsItRuns() {
        final int[][] frames = render(effects(LOUD / 2, LOUD));

        final long early = energyIn(frames, TAIL_FROM, TAIL_FROM + 4000);
        final long late = energyIn(frames, FRAMES - 4000, FRAMES);
        assertTrue(late < early, "the tail is quieter later than it was earlier: " + early + " then " + late);
    }

    @Test
    void holdsTheTailLongerForAGreaterDecay() {
        final long shorter = energyIn(render(effects(LOUD / 4, LOUD)), FRAMES - 4000, FRAMES);
        final long longer = energyIn(render(effects(LOUD, LOUD)), FRAMES - 4000, FRAMES);

        assertTrue(longer > shorter, "a greater decay leaves more still sounding: " + longer + " over " + shorter);
    }

    /**
     * The two sides read the history at their own distances, so what they sound cannot be the same signal.
     */
    @Test
    void soundsTheTwoSidesApart() {
        final int[][] frames = render(effects(LOUD, LOUD));

        boolean apart = false;
        for (int frame = 0; frame < FRAMES && !apart; frame++) {
            apart = frames[frame][0] != frames[frame][1];
        }
        assertTrue(apart, "the sides differ somewhere");
    }

    @Test
    void soundsNothingWhenTheLevelIsNone() {
        final int[][] frames = render(effects(LOUD, 0));

        assertEquals(0, energyIn(frames, 0, FRAMES), "a reverb at no level adds nothing to the mix");
    }

    private static FlexEffects effects(int decay, int level) {
        return new FlexEffects(decay, level, 0, 0, 0, 0);
    }

    private static int[][] render(FlexEffects effects) {
        final FlexReverb reverb = new FlexReverb(effects, FlexDelay.FALCON_RATE);
        final int[][] rendered = new int[FRAMES][];
        final int[] wet = new int[2];
        for (int frame = 0; frame < FRAMES; frame++) {
            final int dry = frame == 0 ? IMPULSE : 0;
            reverb.wet(dry, dry, wet);
            rendered[frame] = new int[] {wet[0], wet[1]};
        }
        return rendered;
    }

    private static long energyIn(int[][] frames, int from, int to) {
        long energy = 0;
        for (int frame = from; frame < to; frame++) {
            energy += Math.abs(frames[frame][0]) + Math.abs(frames[frame][1]);
        }
        return energy;
    }
}

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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlexDelayTest {

    private static final int IMPULSE = 8000;
    private static final int LOUD = FlexEffects.LOUDEST;
    private static final int HALF = FlexEffects.LOUDEST / 2;

    /**
     * The gap between repeats the tracker asks the chip for, in frames, which the delay keeps when it renders
     * at the rate the Falcon itself ran at.
     */
    private static int repeatFrames(int delayTime) {
        return 2008 + delayTime * 78;
    }

    @Test
    void repeatsAtTheGapTheModuleAsksFor() {
        final List<Integer> repeats = leftRepeats(effects(HALF, 0, 0, LOUD), repeatFrames(0) * 3);

        assertTrue(repeats.size() >= 2, "an impulse comes back more than once: " + repeats);
        assertEquals(repeatFrames(0), repeats.get(1) - repeats.get(0), "and evenly, at the gap asked for");
    }

    @Test
    void spacesTheRepeatsFurtherForALongerTime() {
        final List<Integer> repeats = leftRepeats(effects(HALF, LOUD, 0, LOUD), repeatFrames(LOUD) * 3);

        assertEquals(repeatFrames(LOUD), repeats.get(1) - repeats.get(0));
    }

    /**
     * Each repeat is what the one before it was, taken down by the decay, which is the slider over its own
     * top: half of it halves the repeat.
     */
    @Test
    void fadesEachRepeatByTheDecay() {
        final int[][] frames = render(effects(HALF, 0, 0, LOUD), repeatFrames(0) * 3);
        final List<Integer> repeats = leftRepeats(frames);

        final int first = frames[repeats.get(0)][0];
        final int second = frames[repeats.get(1)][0];
        assertEquals(first * HALF / LOUD, second, 1, "the second repeat is the first over the decay");
    }

    /**
     * Ping-pong at its top puts the right channel half a gap ahead of the left, which is what the tracker's
     * own documentation promises.
     */
    @Test
    void trailsTheRightChannelBehindTheLeftByThePingPong() {
        final int[][] frames = render(effects(HALF, 0, LOUD, LOUD), repeatFrames(0) * 2);

        final int left = leftRepeats(frames).get(0);
        final int right = repeatsIn(frames, 1).get(0);
        assertEquals(repeatFrames(0) / 2, left - right, 1, "the right sounds half a gap before the left");
    }

    @Test
    void soundsNothingWhenTheLevelIsNone() {
        final int[][] frames = render(effects(LOUD, 0, 0, 0), repeatFrames(0) * 2);

        assertTrue(leftRepeats(frames).isEmpty(), "a delay at no level adds nothing to the mix");
    }

    private static FlexEffects effects(int decay, int time, int pingPong, int level) {
        return new FlexEffects(0, 0, decay, time, pingPong, level);
    }

    /**
     * Feeds one impulse and then silence, returning what the delay sounds for each frame.
     */
    private static int[][] render(FlexEffects effects, int frames) {
        final FlexDelay delay = new FlexDelay(effects, FlexDelay.FALCON_RATE);
        final int[][] rendered = new int[frames][];
        final int[] wet = new int[2];
        for (int frame = 0; frame < frames; frame++) {
            final int dry = frame == 0 ? IMPULSE : 0;
            delay.wet(dry, dry, wet);
            rendered[frame] = new int[] {wet[0], wet[1]};
        }
        return rendered;
    }

    private static List<Integer> leftRepeats(FlexEffects effects, int frames) {
        return leftRepeats(render(effects, frames));
    }

    private static List<Integer> leftRepeats(int[][] frames) {
        return repeatsIn(frames, 0);
    }

    private static List<Integer> repeatsIn(int[][] frames, int channel) {
        final List<Integer> at = new ArrayList<>();
        for (int frame = 0; frame < frames.length; frame++) {
            if (frames[frame][channel] != 0) {
                at.add(frame);
            }
        }
        return at;
    }
}

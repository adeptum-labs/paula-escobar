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

/**
 * The reverb FlexTrax ran on the Falcon's DSP, following what that program does and using the tables it
 * carries. The mix goes at half strength into a history two thousand frames long, written backwards, which
 * six taps a side read at their own distances into the past to make the early reflections; the two sides are
 * given the same gains but different distances, and that is where the width comes from. Those reflections
 * then feed two loops running side by side, each holding a pair of channels, each adding what it held a
 * moment ago and taking the sum down by the decay before storing it again, which is what keeps the tail
 * going. What both loops read is summed and taken down by the level.
 *
 * <p>The chip was handed distances in frames rather than times, so a module played at another rate has them
 * scaled. The format is described in {@code docs/flextrax-format.md}.</p>
 */
final class FlexReverb {

    private static final int HISTORY_FRAMES = 2000;
    private static final int LOOP_FRAMES = 1996;

    /**
     * How far into the past each early reflection reaches, a side at a time; the two sides differ so that
     * they do not arrive together.
     */
    private static final int[] LEFT_TAPS = {1, 440, 780, 863, 1265, 1408};
    private static final int[] RIGHT_TAPS = {1, 453, 760, 913, 1225, 1448};

    /**
     * What each reflection is worth, and what each loop adds of its own, as the chip's own fractions of two
     * to the twenty-third.
     */
    private static final int[] TAP_GAINS = {0x1B4396, 0x1645A2, 0x1147AE, 0x139581, 0x141893, 0x0851EC};
    private static final int[] LOOP_GAINS = {0x3A9FBE, 0x32F1AA};
    private static final int[] LOOP_TAPS = {1052, 1745};

    /**
     * What the tracker makes of the two settings before handing them over: the decay is taken up from a floor
     * it never falls below and then taken down again, the level is simply scaled. Neither may pass unity,
     * which the tracker's own arithmetic would let it do by wrapping.
     */
    private static final int DECAY_PER_STEP = 135447;
    private static final int DECAY_FLOOR = 500000;
    private static final int DECAY_SCALE = 0x456042;
    private static final int LEVEL_PER_STEP = 147456;

    private static final int DSP_FRACTION_BITS = 23;
    private static final int DSP_ONE = 1 << DSP_FRACTION_BITS;
    private static final int FRACTION_BITS = 16;
    private static final int SIDES = 2;

    private final int[] history;
    private final int[][] loops = new int[LOOP_GAINS.length][];
    private final int[][] held = new int[LOOP_GAINS.length][SIDES];
    private final int[] loopAt = new int[LOOP_GAINS.length];
    private final int[] leftTaps;
    private final int[] rightTaps;
    private final int[] loopTaps = new int[LOOP_GAINS.length];
    private final int[] tapGains = new int[TAP_GAINS.length];
    private final int[] loopGains = new int[LOOP_GAINS.length];
    private final int decay;
    private final int level;

    private int historyAt;

    FlexReverb(FlexEffects effects, int sampleRate) {
        this.history = new int[scaled(HISTORY_FRAMES, sampleRate)];
        this.leftTaps = scaled(LEFT_TAPS, sampleRate);
        this.rightTaps = scaled(RIGHT_TAPS, sampleRate);
        for (int loop = 0; loop < loops.length; loop++) {
            loops[loop] = new int[scaled(LOOP_FRAMES, sampleRate) * SIDES];
            loopTaps[loop] = scaled(LOOP_TAPS[loop], sampleRate);
            loopGains[loop] = LOOP_GAINS[loop] >> DSP_FRACTION_BITS - FRACTION_BITS;
        }
        for (int tap = 0; tap < TAP_GAINS.length; tap++) {
            tapGains[tap] = TAP_GAINS[tap] >> DSP_FRACTION_BITS - FRACTION_BITS;
        }
        this.decay = fraction((int) ((long) unity(effects.reverbDecay() * DECAY_PER_STEP + DECAY_FLOOR)
                * DECAY_SCALE >> DSP_FRACTION_BITS));
        this.level = fraction(unity(effects.reverbLevel() * LEVEL_PER_STEP));
    }

    /**
     * Takes the dry frame and puts what the reverb sounds for it into the two places given.
     */
    void wet(int left, int right, int[] into) {
        history[historyAt] = (left + right) / 4;
        historyAt = historyAt == 0 ? history.length - 1 : historyAt - 1;

        int earlyLeft = 0;
        int earlyRight = 0;
        for (int tap = 0; tap < tapGains.length; tap++) {
            earlyLeft += history[wrapped(historyAt + leftTaps[tap], history.length)] * tapGains[tap]
                    >> FRACTION_BITS;
            earlyRight += history[wrapped(historyAt + rightTaps[tap], history.length)] * tapGains[tap]
                    >> FRACTION_BITS;
        }

        into[0] = 0;
        into[1] = 0;
        for (int loop = 0; loop < loops.length; loop++) {
            run(loop, earlyLeft, earlyRight, into);
        }
        into[0] = into[0] * level >> FRACTION_BITS;
        into[1] = into[1] * level >> FRACTION_BITS;
    }

    private void run(int loop, int earlyLeft, int earlyRight, int[] into) {
        final int[] line = loops[loop];
        final int frames = line.length / SIDES;
        final int read = wrapped(loopAt[loop] + loopTaps[loop], frames) * SIDES;
        final int gain = loopGains[loop];

        final int soundedLeft = line[read] + (held[loop][0] * gain >> FRACTION_BITS);
        final int soundedRight = line[read + 1] + (held[loop][1] * gain >> FRACTION_BITS);
        held[loop][0] = soundedLeft;
        held[loop][1] = soundedRight;

        line[loopAt[loop] * SIDES] = bounded((soundedLeft * decay >> FRACTION_BITS) + earlyLeft);
        line[loopAt[loop] * SIDES + 1] = bounded((soundedRight * decay >> FRACTION_BITS) + earlyRight);
        loopAt[loop] = loopAt[loop] == 0 ? frames - 1 : loopAt[loop] - 1;

        final int sounding = wrapped(loopAt[loop] + loopTaps[loop], frames) * SIDES;
        into[0] += line[sounding];
        into[1] += line[sounding + 1];
    }

    private static int wrapped(int at, int length) {
        return at < length ? at : at - length;
    }

    /**
     * The tracker's arithmetic runs past unity for the upper part of both sliders, and what reaches the chip
     * wraps rather than holds. Holding is what a musician sliding the control would have expected, so the
     * coefficient stops at unity instead.
     */
    private static int unity(int coefficient) {
        return Math.min(coefficient, DSP_ONE);
    }

    private static int fraction(int coefficient) {
        return coefficient >> DSP_FRACTION_BITS - FRACTION_BITS;
    }

    private static int scaled(int frames, int sampleRate) {
        return Math.max(1, Math.toIntExact((long) frames * sampleRate / FlexDelay.FALCON_RATE));
    }

    private static int[] scaled(int[] frames, int sampleRate) {
        final int[] at = new int[frames.length];
        for (int frame = 0; frame < frames.length; frame++) {
            at[frame] = scaled(frames[frame], sampleRate);
        }
        return at;
    }

    private static int bounded(int sample) {
        return Math.clamp(sample, Short.MIN_VALUE, Short.MAX_VALUE);
    }
}

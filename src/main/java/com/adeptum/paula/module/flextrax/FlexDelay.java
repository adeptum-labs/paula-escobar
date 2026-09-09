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
 * The delay FlexTrax ran on the Falcon's DSP, following what that program does. The line holds one value per
 * frame rather than a pair, so both channels share it and it is fed what they average to. It is read where it
 * is about to be written over, which is the oldest thing in it; what comes out is taken down by the decay,
 * added to what is going in, and written back, so the repeats fade. The line is read a second time further
 * along for the right channel, and since that tap is nearer the present it sounds sooner, which is the
 * ping-pong: at its top the tap sits half the line away and the right runs half a gap ahead of the left.
 *
 * <p>The chip was handed a length in frames rather than a time, so a module played at another rate has to
 * scale it. The format is described in {@code docs/flextrax-format.md}.
 */
final class FlexDelay {

    /**
     * The rate FlexTrax drove the Falcon at, and so the rate its frame counts were meant for.
     */
    static final int FALCON_RATE = 49170;

    private static final int SHORTEST = 2008;
    private static final int FRAMES_PER_STEP = 78;
    private static final int PING_PONG_OVER = 128;
    private static final int FRACTION_BITS = 16;
    private static final int ONE = 1 << FRACTION_BITS;

    private final short[] line;
    private final int offset;
    private final int decay;
    private final int level;

    private int at;

    FlexDelay(FlexEffects effects, int sampleRate) {
        final int frames = SHORTEST + effects.delayTime() * FRAMES_PER_STEP;
        this.line = new short[Math.max(1, Math.toIntExact((long) frames * sampleRate / FALCON_RATE))];
        this.offset = effects.delayPingPong() * line.length / PING_PONG_OVER;
        this.decay = coefficient(effects.delayDecay());
        this.level = coefficient(effects.delayLevel());
    }

    /**
     * Takes the dry frame and puts what the delay sounds for it into the two places given.
     */
    void wet(int left, int right, int[] into) {
        final int stored = line[at];
        line[at] = clamped((stored * decay >> FRACTION_BITS) + (left + right) / 2);
        at = at + 1 == line.length ? 0 : at + 1;
        final int trailing = at + offset < line.length ? at + offset : at + offset - line.length;
        into[0] = line[at] * level >> FRACTION_BITS;
        into[1] = line[trailing] * level >> FRACTION_BITS;
    }

    /**
     * A slider over its own top, so that the top of it is unity and the repeats neither grow nor fade.
     */
    private static int coefficient(int slider) {
        return slider * ONE / FlexEffects.LOUDEST;
    }

    private static short clamped(int sample) {
        return (short) Math.clamp(sample, Short.MIN_VALUE, Short.MAX_VALUE);
    }
}

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
 * The unrolling follows dsp_wavetable.c of libdigibooster3, Copyright © 2014
 * Grzegorz Kraszewski, licensed under the two-clause BSD licence and used
 * here under the GNU General Public License.
 */

package com.adeptum.paula.module.digibooster;

/**
 * Plays a sample at its own rate, unrolling the loop as it goes. Loops are turnpoints: a position that, when
 * reached from a given direction, jumps the read position and may reverse it, which covers both the forward and
 * the ping-pong loop, played forwards or backwards.
 */
final class DbmWavetable {

    private static final int FORWARDS = 1;
    private static final int BACKWARDS = 2;
    private static final int ENDLESS = 0x7FFFFFFF;

    private static final class Turnpoint {

        private int position;
        private int jump;
        private int on;
        private int leaves;
        private int times;
    }

    private static final Turnpoint[] NONE = new Turnpoint[0];

    private final short[] sample;
    private final int frames;
    private final Turnpoint first = new Turnpoint();
    private final Turnpoint second = new Turnpoint();
    private final int loopType;
    private final int loopFirst;
    private final int loopLast;

    private Turnpoint[] turnpoints = NONE;
    private int position;
    private int direction = FORWARDS;
    private boolean backwards;

    DbmWavetable(short[] sample, int loopStart, int loopLength, int loopType) {
        this.sample = sample;
        this.frames = sample.length;
        this.loopType = loopType;
        this.loopFirst = loopStart;
        this.loopLast = loopStart + loopLength - 1;
        regenerate();
    }

    short[] sample() {
        return sample;
    }

    int position() {
        return position;
    }

    void reverse(boolean play) {
        backwards = play;
        direction = play ? BACKWARDS : FORWARDS;
        regenerate();
    }

    void offset(int offset) {
        position = Math.clamp(backwards ? frames - offset : offset, 0, frames);
    }

    private void regenerate() {
        if (loopType == DbmInstrument.NO_LOOP) {
            turnpoints = NONE;
        } else if (loopType == DbmInstrument.PINGPONG_LOOP) {
            set(first, loopFirst, 0, BACKWARDS, FORWARDS);
            set(second, loopLast + 1, 0, FORWARDS, BACKWARDS);
            turnpoints = new Turnpoint[] {first, second};
        } else if (backwards) {
            set(first, loopFirst, loopLast - loopFirst + 1, BACKWARDS, BACKWARDS);
            turnpoints = new Turnpoint[] {first};
        } else {
            set(first, loopLast + 1, loopFirst - loopLast - 1, FORWARDS, FORWARDS);
            turnpoints = new Turnpoint[] {first};
        }
    }

    private static void set(Turnpoint turnpoint, int position, int jump, int on, int leaves) {
        turnpoint.position = position;
        turnpoint.jump = jump;
        turnpoint.on = on;
        turnpoint.leaves = leaves;
        turnpoint.times = ENDLESS;
    }

    /**
     * Fills the buffer with the frames the sample has left, padding with silence and reporting the instrument
     * as finished once it runs out.
     */
    boolean pull(short[] into, int at, int frames) {
        boolean playing = true;
        int wanted = frames;
        int write = at;
        while (wanted > 0) {
            int chunk = wanted;
            Turnpoint turnpoint = null;
            for (final Turnpoint candidate : turnpoints) {
                if (candidate.on == direction && inRange(candidate.position, chunk)) {
                    turnpoint = candidate;
                    chunk = direction == FORWARDS ? candidate.position - position : position - candidate.position;
                }
            }
            final int silence = Math.max(0, direction == FORWARDS ? position + chunk - this.frames : chunk - position);
            if (silence > 0) {
                playing = false;
                chunk -= silence;
            }
            for (int i = 0; i < chunk; i++) {
                into[write++] = direction == FORWARDS ? sample[position + i] : sample[position - i - 1];
            }
            for (int i = 0; i < silence; i++) {
                into[write++] = 0;
            }
            position += direction == FORWARDS ? chunk : -chunk;
            wanted -= chunk + silence;
            if (turnpoint != null && turnpoint.times > 0) {
                position += turnpoint.jump;
                direction = turnpoint.leaves;
                turnpoint.times--;
            }
        }
        return playing;
    }

    private boolean inRange(int turnpoint, int chunk) {
        return direction == FORWARDS
                ? turnpoint >= position && turnpoint < position + chunk
                : turnpoint > position - chunk && turnpoint <= position;
    }
}

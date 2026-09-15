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

package com.adeptum.paula.module.xtracker;

/**
 * The sample a channel sounds and where it stands in it: the direction it runs in, whether its loop still holds,
 * and whether anything is left to sound. Volumes and balances run from 0 to {@link #FULL}.
 */
final class DmfVoice {

    static final int FULL = 255;
    static final int MIDDLE = 128;

    DmfSample sample;
    double position;
    boolean backwards;
    boolean released;
    boolean sounding;
    boolean muted;
    int peak;

    /**
     * Starts a sample forwards at a frame, with its loop in force; a frame past its end leaves nothing to sound.
     */
    void start(DmfSample from, int frame) {
        sample = from;
        position = frame;
        backwards = false;
        released = false;
        sounding = frame < from.data().length;
    }

    void silence() {
        sounding = false;
        peak = 0;
    }

    /**
     * The sound where the position falls, on a straight line between the two stored frames either side of it;
     * at the end of a loop the line runs to the loop's first frame.
     */
    int frameAt() {
        final short[] data = sample.data();
        final int index = (int) position;
        if (index < 0 || index >= data.length) {
            return 0;
        }
        final double fraction = position - index;
        return (int) (data[index] + (data[following(index)] - data[index]) * fraction);
    }

    /**
     * Moves on by one frame of output: forwards round the loop from its end to its start, backwards from its
     * start to its end. A sample without a loop, or one whose loop was released, runs out off either end.
     */
    void advance(double step) {
        position += backwards ? -step : step;
        if (loops()) {
            final int length = sample.loopEnd() - sample.loopStart();
            while (!backwards && position >= sample.loopEnd()) {
                position -= length;
            }
            while (backwards && position < sample.loopStart()) {
                position += length;
            }
        } else if (position < 0 || position >= sample.data().length) {
            sounding = false;
        }
    }

    private int following(int index) {
        if (loops() && index + 1 >= sample.loopEnd()) {
            return sample.loopStart();
        }
        return Math.min(index + 1, sample.data().length - 1);
    }

    private boolean loops() {
        return sample.looped() && !released;
    }
}

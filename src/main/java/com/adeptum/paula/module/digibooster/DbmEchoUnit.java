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
 * The echo follows dsp_echo.c of libdigibooster3, Copyright © 2014 Grzegorz
 * Kraszewski, licensed under the two-clause BSD licence and used here under
 * the GNU General Public License.
 */

package com.adeptum.paula.module.digibooster;

/**
 * The cross feeding echo of the tracker: what the track played a moment ago comes back, mixed into both sides
 * by the cross setting and fed into the delay line again by the feedback setting. A track with an echo on it
 * never falls silent by itself, since the tail is always still to come.
 */
final class DbmEchoUnit {

    static final int GLOBAL = 1;
    static final int PER_TRACK = 2;

    private static final int LEVELS = 256;
    private static final int HALF = LEVELS / 2;
    private static final int LEVEL_BITS = 8;
    private static final int FEEDBACK_BITS = 16;
    private static final int CHUNK = 16;
    private static final int STEREO = 2;
    private static final int UNITS_PER_SECOND = 500;
    private static final int ROUNDING = 250;
    private static final int DEFAULT_DELAY = 0x40;
    private static final int TAIL_DIVIDER = 6;
    private static final int DEFAULT_CROSS_FEEDBACK = 32640;

    private final int type;
    private final int sampleRate;
    private final int lineFrames;
    private final short[] line;
    private final short[] dry = new short[CHUNK * STEREO];
    private final DbmSource source;

    private int writeFrame;
    private int delayFrames;
    private int wetLevel = HALF;
    private int dryLevel = HALF;
    private int crossFeedback = DEFAULT_CROSS_FEEDBACK;
    private int crossDry = DEFAULT_CROSS_FEEDBACK;
    private int straightFeedback = HALF;
    private int straightDry = HALF;

    DbmEchoUnit(int type, int sampleRate, DbmSource source) {
        this.type = type;
        this.sampleRate = sampleRate;
        this.source = source;
        this.lineFrames = ((sampleRate >> 1) + (sampleRate >> TAIL_DIVIDER) + 3) & ~4;
        this.line = new short[lineFrames * STEREO];
        this.delayFrames = frames(DEFAULT_DELAY);
    }

    int type() {
        return type;
    }

    void delay(int units) {
        delayFrames = frames(units);
    }

    void levels(int mix, int cross, int feedback) {
        wetLevel = mix;
        dryLevel = LEVELS - mix;
        crossFeedback = cross * feedback;
        crossDry = cross * (LEVELS - feedback);
        straightFeedback = (cross - LEVELS) * feedback;
        straightDry = (cross - LEVELS) * (feedback - LEVELS);
    }

    private int frames(int units) {
        return (units * sampleRate + ROUNDING) / UNITS_PER_SECOND;
    }

    boolean pull(short[] into, int at, int frames) {
        int write = at;
        int left = frames;
        while (left > 0) {
            final int chunk = Math.min(left, CHUNK);
            source.pull(dry, 0, chunk);
            left -= chunk;
            for (int frame = 0; frame < chunk * STEREO; frame += STEREO) {
                write = echo(into, write, dry[frame], dry[frame + 1]);
            }
        }
        return true;
    }

    private int echo(short[] into, int at, int left, int right) {
        final int read = (writeFrame >= delayFrames ? writeFrame - delayFrames : writeFrame - delayFrames + lineFrames) * STEREO;
        final int leftDelayed = line[read];
        final int rightDelayed = line[read + 1];
        line[writeFrame * STEREO] = (short) ((left * straightDry + right * crossDry
                + leftDelayed * straightFeedback + rightDelayed * crossFeedback) >> FEEDBACK_BITS);
        line[writeFrame * STEREO + 1] = (short) ((right * straightDry + left * crossDry
                + rightDelayed * straightFeedback + leftDelayed * crossFeedback) >> FEEDBACK_BITS);
        writeFrame = writeFrame + 1 == lineFrames ? 0 : writeFrame + 1;
        into[at] = (short) ((left * dryLevel + leftDelayed * wetLevel) >> LEVEL_BITS);
        into[at + 1] = (short) ((right * dryLevel + rightDelayed * wetLevel) >> LEVEL_BITS);
        return at + STEREO;
    }
}

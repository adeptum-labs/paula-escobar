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
 * How long X-Tracker holds a row, in either of its speed systems: a tick speed in quarter hertz, or beats a
 * minute at a number of rows to a beat, the second only while a beat is set. X-Tracker has no ticks, so a row is
 * handed out a unit at a time, a 256th of it, with what does not make a whole frame carried to the next unit. A
 * speed slide moves its whole amount over the rows to the global track's next entry, shared evenly with the
 * remainder carried, and holds once it has moved that amount.
 */
final class DmfTempo {

    static final int UNITS_PER_ROW = 256;

    private static final int DEFAULT_TICK_SPEED = 32;
    private static final int SET_TICK_SPEED = 1;
    private static final int SET_BPM = 2;
    private static final int SET_BEAT = 3;
    private static final int TICK_DELAY = 4;
    private static final int SLIDE_UP = 6;
    private static final int SLIDE_DOWN = 7;
    private static final int NO_SLIDE = 0;
    private static final int QUARTERS_OF_A_SECOND = 4;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SIXTEENTHS = 16;
    private static final int NIBBLE_BITS = 4;
    private static final int NIBBLE = 0x0F;
    private static final int SLOWEST = 1;
    private static final int FASTEST = 255;

    private final long sampleRate;
    private int tickSpeed = DEFAULT_TICK_SPEED;
    private int bpm;
    private boolean bpmChosen;
    private int beat;
    private int slide = NO_SLIDE;
    private int slideData;
    private int slideSpan = 1;
    private int slideRows;
    private int slideCarried;
    private long framesNumerator;
    private long unitDenominator = 1;
    private long remainder;

    DmfTempo(int sampleRate) {
        this.sampleRate = sampleRate;
        measure();
    }

    void patternStarts(int patternBeat) {
        beat = patternBeat;
    }

    /**
     * Starts a row with whatever its global track holds and says how many units it lasts: a whole row, or more
     * where a tick delay holds it back. Any command ends a speed slide before its own takes hold; a slide command
     * starts moving the speed over the rows to the global track's next entry.
     */
    int startRow(DmfGlobalEntry entry, int spanRows) {
        int units = UNITS_PER_ROW;
        if (entry != null) {
            slide = NO_SLIDE;
            final int data = entry.data();
            switch (entry.command()) {
                case SET_TICK_SPEED -> {
                    tickSpeed = Math.max(SLOWEST, data);
                    bpmChosen = false;
                }
                case SET_BPM -> chooseBpm(data);
                case SET_BEAT -> beat = data >> NIBBLE_BITS;
                case TICK_DELAY -> units += (data >> NIBBLE_BITS) * UNITS_PER_ROW + (data & NIBBLE) * UNITS_PER_ROW / SIXTEENTHS;
                case SLIDE_UP, SLIDE_DOWN -> {
                    slide = entry.command();
                    slideData = data;
                    slideSpan = Math.max(1, spanRows);
                    slideRows = 0;
                    slideCarried = 0;
                }
                default -> {
                }
            }
        }
        slideSpeed();
        measure();
        return units;
    }

    int nextUnitFrames() {
        remainder += framesNumerator;
        final int frames = (int) (remainder / unitDenominator);
        remainder %= unitDenominator;
        return frames;
    }

    /**
     * The tracker only takes beats a minute while a beat is set, and a speed of nothing does nothing.
     */
    private void chooseBpm(int data) {
        if (data > 0 && beat > 0) {
            bpm = data;
            bpmChosen = true;
        }
    }

    private boolean inBeats() {
        return bpmChosen && beat > 0;
    }

    private void slideSpeed() {
        if (slide == NO_SLIDE || slideRows >= slideSpan) {
            return;
        }
        slideCarried += slideData;
        final int share = slideCarried / slideSpan;
        slideCarried %= slideSpan;
        final int step = slide == SLIDE_UP ? share : -share;
        if (inBeats()) {
            bpm = Math.clamp(bpm + step, SLOWEST, FASTEST);
        } else {
            tickSpeed = Math.clamp(tickSpeed + step, SLOWEST, FASTEST);
        }
        slideRows++;
    }

    /**
     * A unit lasts the sample rate times four over the tick speed times 256 frames, or the sample rate times sixty
     * over beats a minute, rows to a beat and 256. The part of a frame carried over is scaled to the new measure so
     * a change of speed neither loses nor doubles it.
     */
    private void measure() {
        final long denominator = (long) (inBeats() ? bpm * beat : tickSpeed) * UNITS_PER_ROW;
        framesNumerator = sampleRate * (inBeats() ? SECONDS_PER_MINUTE : QUARTERS_OF_A_SECOND);
        remainder = remainder * denominator / unitDenominator;
        unitDenominator = denominator;
    }
}

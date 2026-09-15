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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DmfTempoTest {

    private static final int SAMPLE_RATE = 48000;
    private static final int ROW = DmfTempo.UNITS_PER_ROW;
    private static final int TICK_SPEED = 1;
    private static final int BPM = 2;
    private static final int BEAT = 3;
    private static final int TICK_DELAY = 4;
    private static final int SLIDE_UP = 6;
    private static final int SLIDE_DOWN = 7;

    private final DmfTempo tempo = new DmfTempo(SAMPLE_RATE);

    private long row(DmfGlobalEntry entry) {
        final int units = tempo.startRow(entry);
        long frames = 0;
        for (int unit = 0; unit < units; unit++) {
            frames += tempo.nextUnitFrames();
        }
        return frames;
    }

    private static DmfGlobalEntry command(int command, int data) {
        return new DmfGlobalEntry(command, data);
    }

    @Test
    void playsEightRowsASecondUntilToldOtherwise() {
        assertEquals(SAMPLE_RATE / 8, row(null), "the manual's default tick speed of 32 quarter hertz");
    }

    @Test
    void setsTheTickSpeedInQuarterHertz() {
        assertEquals(SAMPLE_RATE * 4 / 64, row(command(TICK_SPEED, 64)));
        assertEquals(SAMPLE_RATE * 4 / 64, row(null), "and keeps it on the rows after");
    }

    @Test
    void takesATickSpeedOfZeroAsOne() {
        assertEquals(SAMPLE_RATE * 4, row(command(TICK_SPEED, 0)));
    }

    @Test
    void playsBeatsAMinuteAtTheRowsToABeat() {
        tempo.patternStarts(4);

        assertEquals(SAMPLE_RATE * 60 / 48, row(command(BPM, 12)), "TPB 4 at BPM 12 plays 48 rows a minute");
    }

    @Test
    void ignoresBeatsAMinuteWithoutABeat() {
        tempo.patternStarts(0);

        assertEquals(SAMPLE_RATE / 8, row(command(BPM, 120)));
    }

    @Test
    void returnsToTheTickSpeedWhenAPatternHasNoBeat() {
        tempo.patternStarts(4);
        row(command(BPM, 12));
        tempo.patternStarts(0);

        assertEquals(SAMPLE_RATE / 8, row(null));
    }

    @Test
    void takesTheBeatFromTheHighNibbleOfItsCommand() {
        tempo.patternStarts(2);
        assertEquals(SAMPLE_RATE * 60 / 120, row(command(BPM, 60)));

        assertEquals(SAMPLE_RATE * 60 / 240, row(command(BEAT, 0x40)));
        assertEquals(SAMPLE_RATE / 8, row(command(BEAT, 0)), "a beat of nothing is the tick speed again");
    }

    @Test
    void holdsARowBackByWholeRowsAndSixteenths() {
        final int units = tempo.startRow(command(TICK_DELAY, 0x28));

        assertEquals(ROW + 2 * ROW + 8 * ROW / 16, units);
    }

    @Test
    void slidesTheSpeedOnEveryRowUntilTheGlobalTrackSaysSomethingElse() {
        assertEquals(SAMPLE_RATE * 4 / 40, row(command(SLIDE_UP, 8)));
        assertEquals(SAMPLE_RATE * 4 / 48, row(null));
        assertEquals(SAMPLE_RATE * 4 / 32, row(command(TICK_SPEED, 32)));
        assertEquals(SAMPLE_RATE * 4 / 32, row(null));
    }

    @Test
    void slidesBeatsAMinuteWhenPlayingInBeats() {
        tempo.patternStarts(4);
        row(command(BPM, 15));

        assertEquals(SAMPLE_RATE * 60 / (10 * 4), row(command(SLIDE_DOWN, 5)));
    }

    @Test
    void keepsASlideWithinTheSpeedsTheTrackerHas() {
        row(command(TICK_SPEED, 250));

        assertEquals(SAMPLE_RATE * 4 / 255, row(command(SLIDE_UP, 10)), "four frames short of a whole, carried");
    }

    @Test
    void carriesWhatDoesNotMakeAWholeFrameFromUnitToUnit() {
        final DmfTempo odd = new DmfTempo(44100);
        odd.startRow(command(TICK_SPEED, 3));
        long frames = 0;
        for (int unit = 0; unit < 3 * ROW; unit++) {
            frames += odd.nextUnitFrames();
        }

        assertEquals(44100 * 4, frames, "three rows of four thirds of a second");
    }
}

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

package com.adeptum.paula.module.mo3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The commands of an MO3 read against the effects the five trackers wrote. An Impulse Tracker or Scream
 * Tracker effect is named by its letter, counted from one for A; a ProTracker, Fast Tracker or MultiTracker
 * one by its number, the sixteen it wrote as digits and the rest carrying on from sixteen at the letter G.
 */
class Mo3CommandsTest {

    private static final int NOTE = 0x01;
    private static final int INSTRUMENT = 0x02;
    private static final int ARPEGGIO = 0x03;
    private static final int TONE_PORTAMENTO = 0x06;
    private static final int VIBRATO = 0x07;
    private static final int PANNING = 0x0B;
    private static final int VOLUME = 0x0F;
    private static final int PATTERN_BREAK = 0x10;
    private static final int SPEED_OR_TEMPO = 0x12;
    private static final int COARSE_COLUMN_SLIDE = 0x14;
    private static final int GLOBAL_VOLUME = 0x16;
    private static final int RETRIGGER = 0x1C;
    private static final int EXTRA_FINE_UP = 0x1D;
    private static final int EXTRA_FINE_DOWN = 0x1E;
    private static final int IT_SPEED = 0x21;
    private static final int IT_VOLUME_SLIDE = 0x22;
    private static final int IT_TEMPO = 0x2C;
    private static final int IT_EXTENDED = 0x2B;
    private static final int IT_COLUMN_SLIDE = 0x30;
    private static final int IT_COLUMN_OTHER = 0x34;

    private static final int MIDDLE_C = 48;

    @Test
    void countsANoteFromTheFirstTheFormatHolds() {
        assertEquals(MIDDLE_C + 1, applied(NOTE, MIDDLE_C, Mo3Kind.IMPULSE_TRACKER).note());
        assertEquals(MIDDLE_C + 1, applied(NOTE, MIDDLE_C, Mo3Kind.FAST_TRACKER).note());
        assertEquals(MIDDLE_C + 1, applied(NOTE, MIDDLE_C, Mo3Kind.PROTRACKER).note());
        assertEquals(MIDDLE_C + 2, applied(NOTE, MIDDLE_C, Mo3Kind.MULTITRACKER).note(),
                "MultiTracker sits a semitone above the rest");
    }

    @Test
    void readsTheNotesThatAreNotNotes() {
        assertEquals(Mo3Event.KEY_OFF, applied(NOTE, 0xFF, Mo3Kind.IMPULSE_TRACKER).note());
        assertEquals(Mo3Event.NOTE_CUT, applied(NOTE, 0xFE, Mo3Kind.IMPULSE_TRACKER).note());
        assertEquals(Mo3Event.NOTE_FADE, applied(NOTE, 120, Mo3Kind.IMPULSE_TRACKER).note());
    }

    @Test
    void countsAnInstrumentFromOne() {
        assertEquals(1, applied(INSTRUMENT, 0, Mo3Kind.IMPULSE_TRACKER).instrument());
        assertEquals(32, applied(INSTRUMENT, 31, Mo3Kind.PROTRACKER).instrument());
    }

    @Test
    void givesTheImpulseTrackerEffectsTheirLetters() {
        assertEquals(letter('A'), applied(IT_SPEED, 6, Mo3Kind.IMPULSE_TRACKER).effect());
        assertEquals(letter('T'), applied(IT_TEMPO, 125, Mo3Kind.IMPULSE_TRACKER).effect());
        assertEquals(letter('S'), applied(IT_EXTENDED, 0x8F, Mo3Kind.IMPULSE_TRACKER).effect());
        assertEquals(letter('J'), applied(ARPEGGIO, 0x47, Mo3Kind.IMPULSE_TRACKER).effect());
        assertEquals(letter('J'), applied(ARPEGGIO, 0x47, Mo3Kind.SCREAM_TRACKER).effect(),
                "Scream Tracker counts them the same way");
    }

    @Test
    void givesTheFastTrackerEffectsTheirNumbers() {
        assertEquals(0x00, applied(ARPEGGIO, 0x47, Mo3Kind.FAST_TRACKER).effect());
        assertEquals(0x0F, applied(SPEED_OR_TEMPO, 125, Mo3Kind.FAST_TRACKER).effect(),
                "one command sets both the speed and the tempo");
        assertEquals(xmLetter('G'), applied(GLOBAL_VOLUME, 64, Mo3Kind.FAST_TRACKER).effect());
        assertEquals(xmLetter('R'), applied(RETRIGGER, 3, Mo3Kind.FAST_TRACKER).effect());
    }

    @Test
    void keepsTheEffectValueAsItStands() {
        assertEquals(125, applied(IT_TEMPO, 125, Mo3Kind.IMPULSE_TRACKER).effectOp());
    }

    @Test
    void leavesACommandTheTrackerNeverHadAlone() {
        assertEquals(Mo3Event.NO_EFFECT, applied(IT_SPEED, 6, Mo3Kind.FAST_TRACKER).effect());
        assertEquals(Mo3Event.NO_EFFECT, applied(RETRIGGER, 3, Mo3Kind.IMPULSE_TRACKER).effect());
    }

    @Test
    void putsAVolumeThatFitsInTheVolumeColumn() {
        final Mo3Event impulse = applied(VOLUME, 40, Mo3Kind.IMPULSE_TRACKER);

        assertEquals(Mo3Event.VOLUME_COLUMN_VOLUME, impulse.volumeEffect());
        assertEquals(40, impulse.volumeEffectOp());
        assertEquals(Mo3Event.NO_EFFECT, impulse.effect());
    }

    @Test
    void leavesAVolumeAsAnEffectWhereThereIsNoVolumeColumn() {
        final Mo3Event protracker = applied(VOLUME, 40, Mo3Kind.PROTRACKER);

        assertEquals(0x0C, protracker.effect(), "ProTracker sets a volume with an effect of its own");
        assertEquals(40, protracker.effectOp());
        assertEquals(Mo3Event.VOLUME_COLUMN_NONE, protracker.volumeEffect());
    }

    @Test
    void packsAPatternBreakInDecimalDigitsOutsideImpulseTracker() {
        assertEquals(0x10, applied(PATTERN_BREAK, 0x10, Mo3Kind.IMPULSE_TRACKER).effectOp());
        assertEquals(10, applied(PATTERN_BREAK, 0x10, Mo3Kind.FAST_TRACKER).effectOp());
        assertEquals(45, applied(PATTERN_BREAK, 0x45, Mo3Kind.PROTRACKER).effectOp());
    }

    @Test
    void joinsAVolumeSlideToTheTonePortamentoBeforeIt() {
        final Mo3Event event = new Mo3Event();
        Mo3Commands.apply(event, TONE_PORTAMENTO, 3, Mo3Kind.SCREAM_TRACKER);
        Mo3Commands.apply(event, IT_VOLUME_SLIDE, 0x40, Mo3Kind.SCREAM_TRACKER);

        assertEquals(letter('L'), event.effect(), "tone portamento and volume slide together");
        assertEquals(0x40, event.effectOp());
    }

    @Test
    void joinsAVolumeSlideToTheVibratoBeforeIt() {
        final Mo3Event event = new Mo3Event();
        Mo3Commands.apply(event, VIBRATO, 0x35, Mo3Kind.SCREAM_TRACKER);
        Mo3Commands.apply(event, IT_VOLUME_SLIDE, 0x40, Mo3Kind.SCREAM_TRACKER);

        assertEquals(letter('K'), event.effect(), "vibrato and volume slide together");
    }

    @Test
    void leavesAVolumeSlideOnItsOwnAlone() {
        assertEquals(letter('D'), applied(IT_VOLUME_SLIDE, 0x40, Mo3Kind.SCREAM_TRACKER).effect());
    }

    @Test
    void carriesTheDirectionOfAnExtraFinePortamento() {
        final Mo3Event up = applied(EXTRA_FINE_UP, 5, Mo3Kind.FAST_TRACKER);
        final Mo3Event down = applied(EXTRA_FINE_DOWN, 5, Mo3Kind.FAST_TRACKER);

        assertEquals(xmLetter('X'), up.effect());
        assertEquals(0x15, up.effectOp());
        assertEquals(xmLetter('X'), down.effect());
        assertEquals(0x25, down.effectOp());
    }

    @Test
    void countsTheImpulseTrackerVolumeColumnSlidesInTens() {
        assertColumn(IT_COLUMN_SLIDE, 3, Mo3Kind.IMPULSE_TRACKER, Mo3Event.VOLUME_COLUMN_FINE_UP, 3);
        assertColumn(IT_COLUMN_SLIDE, 15, Mo3Kind.IMPULSE_TRACKER, Mo3Event.VOLUME_COLUMN_FINE_DOWN, 5);
        assertColumn(IT_COLUMN_SLIDE, 25, Mo3Kind.IMPULSE_TRACKER, Mo3Event.VOLUME_COLUMN_SLIDE_UP, 5);
        assertColumn(IT_COLUMN_SLIDE, 35, Mo3Kind.IMPULSE_TRACKER, Mo3Event.VOLUME_COLUMN_SLIDE_DOWN, 5);
    }

    @Test
    void splitsTheFastTrackerVolumeColumnSlidesOnTheNibble() {
        assertColumn(COARSE_COLUMN_SLIDE, 0x30, Mo3Kind.FAST_TRACKER, Mo3Event.VOLUME_COLUMN_SLIDE_UP, 3);
        assertColumn(COARSE_COLUMN_SLIDE, 0x03, Mo3Kind.FAST_TRACKER, Mo3Event.VOLUME_COLUMN_SLIDE_DOWN, 3);
    }

    @Test
    void putsAPanningThatFitsInTheVolumeColumn() {
        assertColumn(PANNING, 128, Mo3Kind.IMPULSE_TRACKER, Mo3Event.VOLUME_COLUMN_PANNING, 32);
        assertColumn(PANNING, 0xFF, Mo3Kind.IMPULSE_TRACKER, Mo3Event.VOLUME_COLUMN_PANNING, 64);
        assertColumn(PANNING, 0x40, Mo3Kind.FAST_TRACKER, Mo3Event.VOLUME_COLUMN_PANNING, 4);
        assertEquals(letter('X'), applied(PANNING, 130, Mo3Kind.IMPULSE_TRACKER).effect(),
                "a panning between the steps the column holds stays an effect");
    }

    @Test
    void putsATonePortamentoInTheColumnAtTheStepsItHolds() {
        assertColumn(TONE_PORTAMENTO, 0x10, Mo3Kind.IMPULSE_TRACKER, Mo3Event.VOLUME_COLUMN_TONE_PORTAMENTO, 4);
        assertColumn(TONE_PORTAMENTO, 0x40, Mo3Kind.FAST_TRACKER, Mo3Event.VOLUME_COLUMN_TONE_PORTAMENTO, 4);
        assertEquals(letter('G'), applied(TONE_PORTAMENTO, 0x03, Mo3Kind.IMPULSE_TRACKER).effect(),
                "one finer than the column holds stays an effect");
    }

    @Test
    void putsAShallowVibratoInTheColumn() {
        assertColumn(VIBRATO, 5, Mo3Kind.IMPULSE_TRACKER, Mo3Event.VOLUME_COLUMN_VIBRATO_DEPTH, 5);
        assertEquals(letter('H'), applied(VIBRATO, 15, Mo3Kind.IMPULSE_TRACKER).effect());
    }

    @Test
    void readsTheOffsetKeptAmongTheOtherColumnCommands() {
        assertColumn(IT_COLUMN_OTHER, 225, Mo3Kind.IMPULSE_TRACKER, Mo3Event.VOLUME_COLUMN_OFFSET, 2);
        assertEquals(Mo3Event.VOLUME_COLUMN_NONE, applied(IT_COLUMN_OTHER, 100, Mo3Kind.IMPULSE_TRACKER).volumeEffect(),
                "and nothing of the ones it does not know");
    }

    private static void assertColumn(int command, int value, Mo3Kind kind, int expected, int expectedOp) {
        final Mo3Event event = applied(command, value, kind);

        assertEquals(expected, event.volumeEffect());
        assertEquals(expectedOp, event.volumeEffectOp());
    }

    private static Mo3Event applied(int command, int value, Mo3Kind kind) {
        final Mo3Event event = new Mo3Event();
        Mo3Commands.apply(event, command, value, kind);
        return event;
    }

    private static int letter(char effect) {
        return effect - 'A' + 1;
    }

    private static int xmLetter(char effect) {
        return 0x10 + effect - 'G';
    }
}

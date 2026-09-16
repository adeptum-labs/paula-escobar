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

package com.adeptum.paula.module.ult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UltCommandsTest {

    private static final char NEWEST = '4';

    private static final int SPEED = 'A' - 'A' + 1;
    private static final int BREAK = 'C' - 'A' + 1;
    private static final int VOLUME_SLIDE = 'D' - 'A' + 1;
    private static final int PORTAMENTO_DOWN = 'E' - 'A' + 1;
    private static final int PORTAMENTO_UP = 'F' - 'A' + 1;
    private static final int VIBRATO_VOLUME = 'K' - 'A' + 1;
    private static final int OFFSET = 'O' - 'A' + 1;
    private static final int RETRIG = 'Q' - 'A' + 1;
    private static final int EXTENDED = 'S' - 'A' + 1;
    private static final int TEMPO = 'T' - 'A' + 1;

    /**
     * An eight-bit volume that divides by four goes to the volume column and leaves the effect column to the
     * slide beside it.
     */
    @Test
    void putsAVolumeInTheColumnAndTheSlideBesideIt() {
        final UltCell cell = convert(0x0c, 0x80, 0x0a, 0x40);

        assertEquals(UltCell.COLUMN_VOLUME, cell.volumeEffect());
        assertEquals(0x20, cell.volumeParam());
        assertEquals(VOLUME_SLIDE, cell.effect());
        assertEquals(0x40, cell.param());
    }

    @Test
    void readsTheSpeedCommandAsASpeedOrATempoByItsSize() {
        assertEquals(new Effect(SPEED, 6), effectOf(convert(0x0f, 0x06, 0, 0)));
        assertEquals(new Effect(TEMPO, 0x80), effectOf(convert(0x0f, 0x80, 0, 0)));
    }

    /**
     * UltraTracker writes the row of a pattern break in decimal.
     */
    @Test
    void readsAPatternBreakInDecimal() {
        assertEquals(new Effect(BREAK, 12), effectOf(convert(0x0d, 0x12, 0, 0)));
    }

    /**
     * Two sample offsets together make one offset finer than either could be alone.
     */
    @Test
    void combinesTwoOffsetsIntoOneFinerOne() {
        assertEquals(new Effect(OFFSET, 5), effectOf(convert(0x09, 0x40, 0x09, 0x01)));
    }

    @Test
    void scalesASingleOffsetByFour() {
        assertEquals(new Effect(OFFSET, 0x40), effectOf(convert(0x09, 0x10, 0, 0)));
    }

    @Test
    void turnsTheSpecialCommandsIntoTheirFineAndExtendedForms() {
        assertEquals(new Effect(PORTAMENTO_UP, 0xf3), effectOf(convert(0x0e, 0x13, 0, 0)));
        assertEquals(new Effect(PORTAMENTO_DOWN, 0xf3), effectOf(convert(0x0e, 0x23, 0, 0)));
        assertEquals(new Effect(RETRIG, 0x05), effectOf(convert(0x0e, 0x95, 0, 0)));
        assertEquals(new Effect(VOLUME_SLIDE, 0x4f), effectOf(convert(0x0e, 0xa4, 0, 0)));
        assertEquals(new Effect(VOLUME_SLIDE, 0xf4), effectOf(convert(0x0e, 0xb4, 0, 0)));
        assertEquals(new Effect(EXTENDED, 0xc4), effectOf(convert(0x0e, 0xc4, 0, 0)));
    }

    /**
     * From version 1.5 on, the sample command can stop a sample's loop, which is taken as a key off.
     */
    @Test
    void takesTheStopLoopCommandAsAKeyOffFromVersionOneFive() {
        assertTrue(UltCommands.convert(event(0x05, 0x0c, 0, 0), '3').keyOff());
        assertEquals(false, UltCommands.convert(event(0x05, 0x0c, 0, 0), '2').keyOff());
    }

    @Test
    void dropsAnArpeggioWithNothingToPlayAndAnyBeforeVersionOneFive() {
        assertEquals(new Effect(0, 0), effectOf(convert(0x00, 0x00, 0, 0)));
        assertEquals(new Effect(0, 0), effectOf(UltCommands.convert(event(0x00, 0x37, 0, 0), '2')));
    }

    /**
     * A volume slide beside a vibrato that says nothing of its own is the one command that does both.
     */
    @Test
    void mergesAVolumeSlideWithAnEmptyVibrato() {
        assertEquals(new Effect(VIBRATO_VOLUME, 0x40), effectOf(convert(0x0a, 0x40, 0x04, 0x00)));
    }

    /**
     * Where neither command fits the volume column, the weightier one keeps the effect column, and a command
     * that governs the whole song is handed on to be written elsewhere on the row rather than lost.
     */
    @Test
    void keepsTheWeightierOfTwoAndHandsOnACommandForTheWholeSong() {
        final UltCell cell = convert(0x0f, 0x80, 0x0d, 0x12);

        assertEquals(new Effect(BREAK, 12), effectOf(cell));
        assertEquals(TEMPO, cell.lostEffect());
        assertEquals(0x80, cell.lostParam());
    }

    private static UltCell convert(int firstEffect, int firstParam, int secondEffect, int secondParam) {
        return UltCommands.convert(event(firstEffect, firstParam, secondEffect, secondParam), NEWEST);
    }

    private static UltEvent event(int firstEffect, int firstParam, int secondEffect, int secondParam) {
        return new UltEvent(0, 0, firstEffect, firstParam, secondEffect, secondParam);
    }

    private static Effect effectOf(UltCell cell) {
        return new Effect(cell.effect(), cell.param());
    }

    private record Effect(int effect, int param) {
    }
}

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

package com.adeptum.paula.module.med;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MedEffectsTest {

    private static final int NOTE = 25;
    private static final int INSTRUMENT = 1;

    private final MedSong decimal = song(0, 0);
    private final MedSong hex = song(MedSong.FLAG_VOLUMES_ARE_HEX, 0);
    private final MedSong eightChannel = song(MedSong.FLAG_EIGHT_CHANNEL, 0);
    private final MedSong beats = song(0, MedSong.FLAG2_BPM);

    private static MedSong song(int flags, int flags2) {
        return new MedSong(new int[]{0}, 33, 6, 0, flags, flags2, 64, new int[0], new int[0]);
    }

    private MedCommand translate(int command, int parameter) {
        return MedEffects.translate(new MedEntry(NOTE, INSTRUMENT, command, parameter), decimal);
    }

    @Test
    void tellsApartTheTwoVibratos() {
        assertEquals(MedEffect.DEEP_VIBRATO, translate(0x04, 0x42).effect(), "the one OctaMED swings twice as far");
        assertEquals(MedEffect.VIBRATO, translate(0x14, 0x42).effect(), "and the one a tracker would write");
    }

    @Test
    void readsAVolumeInDecimalUnlessTheSongSaysOtherwise() {
        final MedEntry line = new MedEntry(NOTE, INSTRUMENT, 0x0C, 0x40);

        assertEquals(40, MedEffects.translate(line, decimal).parameter(), "forty reads as forty");
        assertEquals(0x40, MedEffects.translate(line, hex).parameter(), "unless the song counts in hex");
    }

    @Test
    void dropsASlideOfNothing() {
        assertEquals(MedEffect.NONE, translate(0x01, 0x00).effect());
        assertEquals(MedEffect.SLIDE_UP, translate(0x01, 0x04).effect());
    }

    @Test
    void takesOnlyTheSpeedsThatMeanSomething() {
        assertEquals(MedEffect.SET_SPEED, translate(0x09, 0x06).effect());
        assertEquals(MedEffect.NONE, translate(0x09, 0x00).effect(), "nothing is not a speed");
        assertEquals(MedEffect.NONE, translate(0x09, 0x21).effect(), "nor is anything past the fastest");
    }

    @Test
    void readsTheFiveCommandsHiddenBehindTheTempo() {
        assertEquals(MedEffect.BREAK, translate(0x0F, 0x00).effect(), "no tempo at all ends the block");
        assertEquals(MedEffect.RETRIGGER, translate(0x0F, 0xF1).effect());
        assertEquals(MedEffect.NOTE_DELAY, translate(0x0F, 0xF2).effect());
        assertEquals(MedEffect.RETRIGGER, translate(0x0F, 0xF3).effect());
        assertEquals(0, translate(0x0F, 0xFF).note(), "turning the note off leaves no note to start");
        assertEquals(MedEffect.NOTE_CUT, translate(0x0F, 0xFF).effect());
        assertEquals(MedEffect.NONE, translate(0x0F, 0xFE).effect(), "and the rest are not ours to play");
    }

    @Test
    void turnsTheLowestTemposIntoTheSpeedsTheyStandFor() {
        assertEquals(195, MedEffects.convertTempo(1, decimal), "the ten lowest are Soundtracker speeds");
        assertEquals(20, MedEffects.convertTempo(10, decimal));
        assertEquals(33, MedEffects.convertTempo(33, decimal), "above them a tempo is itself");
        assertEquals(1, MedEffects.convertTempo(1, beats), "except where the song counts in beats a minute");
        assertEquals(179, MedEffects.convertTempo(1, eightChannel), "or drives the mixing buffer instead");
        assertEquals(99, MedEffects.convertTempo(240, eightChannel), "where anything past ten means ten");
    }

    @Test
    void readsPanningAsAStepEitherSideOfTheMiddle() {
        assertEquals(128, translate(0x2E, 0x00).parameter(), "no step is the middle");
        assertEquals(0, translate(0x2E, 0xF0).parameter(), "sixteen steps left is hard left");
        assertEquals(255, translate(0x2E, 0x10).parameter(), "and sixteen right stops one short of the wall");
        assertEquals(MedEffect.NONE, translate(0x2E, 0x40).parameter() == 0 ? MedEffect.NONE : MedEffect.SET_PAN,
                "a step further than the format goes means nothing");
    }

    @Test
    void keepsTheHoldSymbolApartFromANote() {
        final MedCommand held = MedEffects.translate(new MedEntry(0, INSTRUMENT, 0, 0), decimal);

        assertEquals(true, held.holds(), "an instrument with no note holds the one sounding");
        assertEquals(false, translate(0x00, 0x00).holds(), "a line with a note starts it afresh");
    }

    @Test
    void dropsACommandItHasNoMeaningFor() {
        assertEquals(MedEffect.NONE, translate(0x0E, 0x11).effect(), "the one that drives the synth sequence");
        assertEquals(MedEffect.NONE, translate(0x7F, 0x11).effect(), "and anything past the set");
    }
}

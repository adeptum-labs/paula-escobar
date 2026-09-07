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
 * The replay follows the MED loaders and med_extras.c of libxmp,
 * Copyright © 1996-2026 Claudio Matsuoka and Hipolito Carraro Jr,
 * licensed under the MIT licence and used here under the GNU General
 * Public License.
 */

package com.adeptum.paula.module.med;

/**
 * The tables the replayer reads rather than computes: the periods a note sounds at, the two tempo tables that
 * turn a tracker tempo into a rate, and the wave the vibrato swings along.
 */
final class MedTables {

    /**
     * The periods of the twelve notes of the lowest octave, one octave lower every time they are doubled and
     * one higher every time they are halved, as the Amiga hardware was driven.
     */
    private static final int[] OCTAVE_PERIODS = {
        3424, 3232, 3048, 2880, 2712, 2560, 2416, 2280, 2152, 2032, 1920, 1812};

    /**
     * Tempos one to ten in tempo mode stand for the speeds of Soundtracker rather than for themselves.
     */
    private static final int[] COMPATIBILITY_TEMPOS = {195, 97, 65, 49, 39, 32, 28, 24, 22, 20};

    /**
     * In five to eight channel mode the tempo sets the size of the mixing buffer instead, so the ten values it
     * takes stand for speeds of their own and anything above ten means ten.
     */
    private static final int[] EIGHT_CHANNEL_TEMPOS = {179, 164, 152, 141, 131, 123, 116, 110, 104, 99};

    private static final int[] VIBRATO_SINE = {
        0, 49, 97, 141, 180, 212, 235, 250,
        255, 250, 235, 212, 180, 141, 97, 49,
        0, -49, -97, -141, -180, -212, -235, -250,
        -255, -250, -235, -212, -180, -141, -97, -49};

    /**
     * How many octaves of samples each of the six multi-octave instrument types carries.
     */
    private static final int[] OCTAVES_OF_TYPE = {5, 3, 2, 4, 6, 7};

    static final int NOTES_PER_OCTAVE = 12;
    static final int LOWEST_TEMPO = 1;
    static final int HIGHEST_COMPATIBILITY_TEMPO = 10;
    static final int VIBRATO_STEPS = 32;

    private MedTables() {
    }

    /**
     * The period of a note counted from one, halving with every octave above the first; a note past the range
     * the hardware could sound stays at the end of it.
     */
    static int period(int note) {
        final int index = Math.max(0, note - 1);
        final int octave = index / NOTES_PER_OCTAVE;
        return OCTAVE_PERIODS[index % NOTES_PER_OCTAVE] >> octave;
    }

    static int notes(int octaves) {
        return octaves * NOTES_PER_OCTAVE;
    }

    static int compatibilityTempo(int tempo) {
        return COMPATIBILITY_TEMPOS[tempo - 1];
    }

    static int eightChannelTempo(int tempo) {
        return EIGHT_CHANNEL_TEMPOS[Math.min(tempo, HIGHEST_COMPATIBILITY_TEMPO) - 1];
    }

    static int vibrato(int step) {
        return VIBRATO_SINE[step & VIBRATO_STEPS - 1];
    }

    static int octavesOfType(int type) {
        return type >= 1 && type <= OCTAVES_OF_TYPE.length ? OCTAVES_OF_TYPE[type - 1] : 0;
    }
}

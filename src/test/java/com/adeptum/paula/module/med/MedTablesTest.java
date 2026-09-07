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

class MedTablesTest {

    private static final int C1 = 1;
    private static final int C2 = 13;
    private static final int C3 = 25;
    private static final int B1 = 12;

    /**
     * The periods are the Amiga's own, so a note has an absolute pitch rather than one that only holds
     * against itself: libxmp sounds the C of the second octave at 428, and so does this.
     */
    @Test
    void soundsANoteAtThePeriodTheAmigaGaveIt() {
        assertEquals(856, MedTables.period(C1), "the lowest C OctaMED writes");
        assertEquals(428, MedTables.period(C2));
        assertEquals(214, MedTables.period(C3), "an octave halves it");
        assertEquals(453, MedTables.period(B1));
    }

    @Test
    void countsTheOctavesOfTheMultiOctaveInstruments() {
        assertEquals(5, MedTables.octavesOfType(1));
        assertEquals(3, MedTables.octavesOfType(2));
        assertEquals(7, MedTables.octavesOfType(6));
        assertEquals(0, MedTables.octavesOfType(7), "past the six there are none");
        assertEquals(0, MedTables.octavesOfType(0));
    }

    @Test
    void swingsTheVibratoBothWaysAndBackToNothing() {
        assertEquals(0, MedTables.vibrato(0));
        assertEquals(255, MedTables.vibrato(8), "furthest one way");
        assertEquals(-255, MedTables.vibrato(24), "and the other");
        assertEquals(MedTables.vibrato(1), MedTables.vibrato(1 + MedTables.VIBRATO_STEPS), "then round again");
    }
}

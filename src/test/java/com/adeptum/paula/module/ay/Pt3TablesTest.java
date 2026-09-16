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

package com.adeptum.paula.module.ay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Pt3TablesTest {

    /**
     * Recordings of version 4 modules on the Pro Tracker, ASM and real tables all follow the old tuning, the
     * one Pro Tracker 3.4r kept, rather than the one ZXTune gives version 4.
     */
    @Test
    void tunesAVersionFourModuleTheOldWay() {
        for (final int table : new int[]{Pt3Tables.PRO_TRACKER, Pt3Tables.ASM, Pt3Tables.REAL}) {
            assertArrayEquals(Pt3Tables.tones(table, 3), Pt3Tables.tones(table, 4), "table " + table);
        }
    }

    @Test
    void tunesTheProTrackerAndAsmTablesAnewFromVersionFive() {
        assertNotEquals(Arrays.toString(Pt3Tables.tones(Pt3Tables.ASM, 4)), Arrays.toString(Pt3Tables.tones(Pt3Tables.ASM, 5)));
        assertNotEquals(Arrays.toString(Pt3Tables.tones(Pt3Tables.PRO_TRACKER, 4)),
                Arrays.toString(Pt3Tables.tones(Pt3Tables.PRO_TRACKER, 5)));
    }

    @Test
    void neverRetunesTheSoundTrackerTable() {
        assertArrayEquals(Pt3Tables.tones(Pt3Tables.SOUND_TRACKER, 3), Pt3Tables.tones(Pt3Tables.SOUND_TRACKER, 6));
    }

    @Test
    void fallsBackToTheProTrackerTableForANumberItDoesNotKnow() {
        assertArrayEquals(Pt3Tables.tones(Pt3Tables.PRO_TRACKER, 5), Pt3Tables.tones(9, 5));
    }

    /**
     * The volume table changed with version 3.5: at full volume both give a level back unchanged, but a quiet
     * channel lets a quiet line through only by the newer one.
     */
    @Test
    void scalesLevelsByTheNewerTableFromVersionFive() {
        assertEquals(15, Pt3Tables.level(4, 15, 15));
        assertEquals(15, Pt3Tables.level(5, 15, 15));
        assertEquals(0, Pt3Tables.level(4, 2, 4));
        assertEquals(1, Pt3Tables.level(5, 2, 4));
    }
}

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

import static com.adeptum.paula.module.xtracker.DmfFiles.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DmfPatternTest {

    private static final int ROWS = 8;

    private static DmfPattern pattern() {
        final DmfTrackEntry[][] entries = new DmfTrackEntry[ROWS][1];
        entries[0][0] = empty();
        entries[3][0] = empty();
        final DmfGlobalEntry[] global = new DmfGlobalEntry[ROWS];
        global[5] = new DmfGlobalEntry(1, 32);
        return new DmfPattern(1, 0, global, entries);
    }

    @Test
    void spansToTheTracksNextEntryOrThePatternsEnd() {
        final DmfPattern pattern = pattern();

        assertEquals(3, pattern.span(0, 0));
        assertEquals(5, pattern.span(3, 0));
        assertEquals(1, pattern.span(7, 0));
        assertEquals(3, pattern.globalSpan(5));
        assertEquals(5, pattern.globalSpan(0));
    }

    @Test
    void spansToThePatternsEndForATrackItDoesNotHave() {
        final DmfPattern pattern = pattern();

        assertEquals(ROWS, pattern.span(0, 1));
    }
}

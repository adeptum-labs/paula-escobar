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

package com.adeptum.paula.ui.visual;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BarsTest {

    @Test
    void fullLevelFillsEveryRowTopDown() {
        assertArrayEquals(new char[] {'█', '█'}, Bars.column(1.0, 2));
    }

    @Test
    void partialLevelsUseEighthBlocksForTheTopRow() {
        assertArrayEquals(new char[] {' ', '█'}, Bars.column(0.5, 2));
        assertArrayEquals(new char[] {' ', '▄'}, Bars.column(0.25, 2));
        assertArrayEquals(new char[] {'▂', '█'}, Bars.column(0.625, 2));
    }

    @Test
    void silenceIsBlank() {
        assertArrayEquals(new char[] {' ', ' ', ' '}, Bars.column(0, 3));
    }

    @Test
    void horizontalMetersFillFromTheLeft() {
        assertEquals("████▌     ", Bars.row(0.45, 10));
        assertEquals("          ", Bars.row(0, 10));
        assertEquals("██████████", Bars.row(1, 10));
    }

    @Test
    void sweepsABlockToAndFroForWorkWithNoEndInSight() {
        final String start = Bars.sweep(0, 20);
        final String moved = Bars.sweep(3, 20);

        assertEquals(20, start.length());
        assertEquals(start.chars().filter(c -> c == '\u2588').count(), moved.chars().filter(c -> c == '\u2588').count(),
                "the block keeps its size as it travels");
        assertEquals(0, start.indexOf('\u2588'), "it starts against the left");
        assertEquals(3, moved.indexOf('\u2588'), "and moves along with the work");
    }

    @Test
    void sweepsBackWhenItReachesTheEnd() {
        final int width = 10;
        final int travel = width - width / 5;

        assertEquals(travel, Bars.sweep(travel, width).indexOf('\u2588'), "as far as it goes");
        assertEquals(travel - 1, Bars.sweep(travel + 1, width).indexOf('\u2588'), "then back the way it came");
        assertEquals(0, Bars.sweep(travel * 2L, width).indexOf('\u2588'), "and round again");
    }

    @Test
    void hasNothingToSweepAcrossNoWidth() {
        assertEquals("", Bars.sweep(5, 0));
    }
}

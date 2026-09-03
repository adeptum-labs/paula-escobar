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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BrailleCanvasTest {

    private static final char BLANK = '⠀';

    @Test
    void countsItsDotsTwoAcrossAndFourDownPerCell() {
        final BrailleCanvas canvas = new BrailleCanvas(10, 3);

        assertEquals(20, canvas.dotColumns());
        assertEquals(12, canvas.dotRows());
        assertEquals(3, canvas.rows().size());
        assertTrue(canvas.rows().stream().allMatch(row -> row.length() == 10));
    }

    @Test
    void isBlankUntilSomethingIsPlotted() {
        assertEquals(List.of("⠀⠀⠀", "⠀⠀⠀"), new BrailleCanvas(3, 2).rows());
    }

    /**
     * The origin is at the bottom left, so the highest point lands on the top row and the lowest on the last.
     */
    @Test
    void putsTheOriginAtTheBottomLeft() {
        final BrailleCanvas canvas = new BrailleCanvas(2, 2);

        canvas.set(0, 1);
        canvas.set(1, 0);

        final List<String> rows = canvas.rows();
        assertEquals('⠁', rows.get(0).charAt(0), "the top left dot of the first cell");
        assertEquals('⢀', rows.get(1).charAt(1), "the bottom right dot of the last cell");
    }

    @Test
    void lightsSeveralDotsInOneColumn() {
        final BrailleCanvas canvas = new BrailleCanvas(1, 1);

        canvas.lightDot(0, 0);
        canvas.lightDot(0, 1);
        canvas.lightDot(0, 2);
        canvas.lightDot(0, 3);

        assertEquals("⡇", canvas.rows().get(0), "a waveform plotter cannot draw this, a scatter must");
    }

    @Test
    void dropsWhatFallsOutsideTheUnitSquare() {
        final BrailleCanvas canvas = new BrailleCanvas(2, 2);

        canvas.set(-0.01, 0.5);
        canvas.set(1.01, 0.5);
        canvas.set(0.5, -0.01);
        canvas.set(0.5, 1.01);
        canvas.lightDot(-1, 0);
        canvas.lightDot(0, 99);

        assertTrue(canvas.rows().stream().allMatch(row -> row.chars().allMatch(c -> c == BLANK)));
    }

    @Test
    void survivesAGridWithNoRoomInIt() {
        assertEquals(List.of(), new BrailleCanvas(0, 0).rows());
        new BrailleCanvas(0, 0).set(0.5, 0.5);
    }
}

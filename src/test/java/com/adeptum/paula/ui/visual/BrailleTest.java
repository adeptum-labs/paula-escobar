/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

import java.util.List;
import org.junit.jupiter.api.Test;

class BrailleTest {

    @Test
    void aFlatLineSitsInTheMiddleOfTheCell() {
        assertEquals(List.of("⠤"), Braille.plot(new double[] {0, 0}, 1, 1));
    }

    @Test
    void extremesReachTheTopAndBottomDots() {
        assertEquals(List.of("⢁"), Braille.plot(new double[] {1, -1}, 1, 1));
    }

    @Test
    void tallPlotsSpreadOverSeveralRows() {
        final List<String> rows = Braille.plot(new double[] {1, 1, -1, -1}, 2, 2);
        assertEquals(2, rows.size());
        assertEquals("⠉⠀", rows.get(0));
        assertEquals("⠀⣀", rows.get(1));
    }

    @Test
    void samplesAreResampledToTheWidth() {
        final List<String> rows = Braille.plot(new double[] {0, 0, 0, 0, 0, 0, 0, 0}, 2, 1);
        assertEquals("⠤⠤", rows.get(0));
    }

    @Test
    void emptyGridsProduceNothing() {
        assertEquals(List.of(), Braille.plot(new double[] {0, 1}, 4, 0));
        assertEquals(List.of("⠀"), Braille.plot(new double[0], 1, 1));
    }

    @Test
    void valuesBeyondFullScaleAreClamped() {
        assertEquals(List.of("⢁"), Braille.plot(new double[] {5, -5}, 1, 1));
    }
}

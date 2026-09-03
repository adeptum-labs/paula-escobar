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

class WaterfallTest {

    private static final int BANDS = 3;
    private static final double PRECISION = 1e-9;

    @Test
    void readsBackTheRowsItWasFedNewestFirst() {
        final Waterfall waterfall = new Waterfall(BANDS, 4);

        waterfall.feed(new double[] {0.1, 0.1, 0.1});
        waterfall.feed(new double[] {0.2, 0.2, 0.2});
        waterfall.feed(new double[] {0.3, 0.3, 0.3});

        assertArrayEquals(new double[] {0.3, 0.3, 0.3}, waterfall.row(0), PRECISION);
        assertArrayEquals(new double[] {0.2, 0.2, 0.2}, waterfall.row(1), PRECISION);
        assertArrayEquals(new double[] {0.1, 0.1, 0.1}, waterfall.row(2), PRECISION);
    }

    @Test
    void readsSilenceWhereNothingHasBeenFedYet() {
        final Waterfall waterfall = new Waterfall(BANDS, 4);
        waterfall.feed(new double[] {0.5, 0.5, 0.5});

        assertArrayEquals(new double[BANDS], waterfall.row(1), PRECISION, "before the first row");
        assertArrayEquals(new double[BANDS], waterfall.row(99), PRECISION, "and past what is kept");
        assertArrayEquals(new double[BANDS], waterfall.row(-1), PRECISION);
    }

    @Test
    void forgetsWhatFallsOffTheEndOfTheRing() {
        final Waterfall waterfall = new Waterfall(BANDS, 2);

        waterfall.feed(new double[] {0.1, 0.1, 0.1});
        waterfall.feed(new double[] {0.2, 0.2, 0.2});
        waterfall.feed(new double[] {0.3, 0.3, 0.3});

        assertEquals(2, waterfall.depth());
        assertArrayEquals(new double[] {0.3, 0.3, 0.3}, waterfall.row(0), PRECISION);
        assertArrayEquals(new double[] {0.2, 0.2, 0.2}, waterfall.row(1), PRECISION);
        assertArrayEquals(new double[BANDS], waterfall.row(2), PRECISION, "the oldest is gone");
    }

    @Test
    void takesARowOfADifferentLengthThanItsBands() {
        final Waterfall waterfall = new Waterfall(BANDS, 2);

        waterfall.feed(new double[] {0.4});

        assertArrayEquals(new double[] {0.4, 0, 0}, waterfall.row(0), PRECISION, "the bands it was not told about");
    }
}

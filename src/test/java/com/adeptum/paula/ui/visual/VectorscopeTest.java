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
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class VectorscopeTest {

    private static final int WIDTH = 16;
    private static final int HEIGHT = 8;
    private static final char BLANK = '⠀';

    @Test
    void drawsMonoAsAnUprightLineDownTheMiddle() {
        final List<String> rows = Vectorscope.plot(sweep(1, 1), WIDTH, HEIGHT);

        assertEquals(1, columnsDrawnOn(rows).size(), "a signal the same in both channels leans neither way");
        assertEquals(WIDTH / 2, columnsDrawnOn(rows).iterator().next(), "and stands in the middle");
    }

    /**
     * One channel against the other is the phase a mono listener loses, and it lies flat across the scope
     * rather than standing up in it.
     */
    @Test
    void drawsOppositeChannelsAsALineLyingFlat() {
        final List<String> rows = Vectorscope.plot(sweep(1, -1), WIDTH, HEIGHT);

        assertTrue(columnsDrawnOn(rows).size() > 1, "it spreads across the width");
        assertEquals(1, rowsDrawnOn(rows).size(), "and keeps to one row of cells");
    }

    @Test
    void opensOutWhenTheChannelsDiffer() {
        final List<String> apart = Vectorscope.plot(circle(), WIDTH, HEIGHT);

        assertTrue(columnsDrawnOn(apart).size() > 1, "a channel panned apart from the other has width");
        assertTrue(rowsDrawnOn(apart).size() > 1, "and height");
    }

    @Test
    void drawsNothingForSilenceButKeepsTheGrid() {
        final List<String> rows = Vectorscope.plot(new double[64], WIDTH, HEIGHT);

        assertEquals(HEIGHT, rows.size());
        assertEquals(WIDTH / 2, columnsDrawnOn(rows).iterator().next(), "silence is a dot in the middle");
    }

    @Test
    void survivesAnEmptyOrOddBuffer() {
        assertEquals(HEIGHT, Vectorscope.plot(new double[0], WIDTH, HEIGHT).size());
        assertEquals(HEIGHT, Vectorscope.plot(new double[] {0.5}, WIDTH, HEIGHT).size(), "a frame without its pair");
    }

    private static double[] sweep(double leftGain, double rightGain) {
        final double[] frames = new double[128];
        for (int frame = 0; frame < frames.length / 2; frame++) {
            final double value = Math.sin(2 * Math.PI * frame / (frames.length / 2.0));
            frames[frame * 2] = value * leftGain;
            frames[frame * 2 + 1] = value * rightGain;
        }
        return frames;
    }

    private static double[] circle() {
        final double[] frames = new double[128];
        for (int frame = 0; frame < frames.length / 2; frame++) {
            final double angle = 2 * Math.PI * frame / (frames.length / 2.0);
            frames[frame * 2] = Math.sin(angle);
            frames[frame * 2 + 1] = Math.cos(angle);
        }
        return frames;
    }

    private static Set<Integer> columnsDrawnOn(List<String> rows) {
        final Set<Integer> columns = new TreeSet<>();
        for (final String row : rows) {
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) != BLANK) {
                    columns.add(x);
                }
            }
        }
        return columns;
    }

    private static Set<Integer> rowsDrawnOn(List<String> rows) {
        final Set<Integer> drawn = new TreeSet<>();
        for (int y = 0; y < rows.size(); y++) {
            if (rows.get(y).chars().anyMatch(c -> c != BLANK)) {
                drawn.add(y);
            }
        }
        return drawn;
    }
}

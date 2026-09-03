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

import java.util.ArrayList;
import java.util.List;

/**
 * A grid of braille cells to light dots in, each character packing two columns and four rows of them. Points
 * are given in the unit square with the origin at the bottom left, the way a graph is read rather than the way
 * a screen is scanned, and anything falling outside it is dropped.
 */
public final class BrailleCanvas {

    private static final char BLANK = '⠀';
    private static final int DOT_COLUMNS = 2;
    private static final int DOT_ROWS = 4;
    private static final int[][] DOT_BITS = {{0x01, 0x08}, {0x02, 0x10}, {0x04, 0x20}, {0x40, 0x80}};

    private final int widthCells;
    private final int heightCells;
    private final int[] cells;

    public BrailleCanvas(int widthCells, int heightCells) {
        this.widthCells = Math.max(0, widthCells);
        this.heightCells = Math.max(0, heightCells);
        this.cells = new int[this.widthCells * this.heightCells];
    }

    public int dotColumns() {
        return widthCells * DOT_COLUMNS;
    }

    public int dotRows() {
        return heightCells * DOT_ROWS;
    }

    /**
     * Lights the dot a point in the unit square falls on; a point on any edge belongs to the cell inside it.
     */
    public void set(double x, double y) {
        if (x < 0 || x > 1 || y < 0 || y > 1 || cells.length == 0) {
            return;
        }
        lightDot((int) Math.min(dotColumns() - 1, x * dotColumns()),
                (int) Math.min(dotRows() - 1, (1 - y) * dotRows()));
    }

    /**
     * Lights a dot by its place in the dot grid, counted from the top left as the characters are.
     */
    public void lightDot(int column, int row) {
        if (column < 0 || row < 0 || column >= dotColumns() || row >= dotRows()) {
            return;
        }
        cells[row / DOT_ROWS * widthCells + column / DOT_COLUMNS] |= DOT_BITS[row % DOT_ROWS][column % DOT_COLUMNS];
    }

    public List<String> rows() {
        final List<String> lines = new ArrayList<>(heightCells);
        for (int y = 0; y < heightCells; y++) {
            final StringBuilder line = new StringBuilder(widthCells);
            for (int x = 0; x < widthCells; x++) {
                line.append((char) (BLANK + cells[y * widthCells + x]));
            }
            lines.add(line.toString());
        }
        return lines;
    }
}

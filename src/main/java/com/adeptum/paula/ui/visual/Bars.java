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

import java.util.Arrays;

/**
 * Level bars made of block elements, with eighth-block glyphs for the partial cell at the end.
 */
public final class Bars {

    private static final char FULL = '█';
    private static final char[] VERTICAL_EIGHTHS = {' ', '▁', '▂', '▃', '▄', '▅', '▆', '▇'};
    private static final char[] HORIZONTAL_EIGHTHS = {' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉'};
    private static final int EIGHTHS = 8;

    private Bars() {
    }

    /**
     * A vertical bar as rows from top to bottom.
     */
    public static char[] column(double level, int height) {
        final char[] rows = new char[height];
        Arrays.fill(rows, ' ');
        final int eighths = eighths(level, height);
        for (int row = 0; row < height; row++) {
            final int fromBottom = height - 1 - row;
            final int cellEighths = Math.clamp(eighths - fromBottom * EIGHTHS, 0, EIGHTHS);
            rows[row] = cellEighths == EIGHTHS ? FULL : VERTICAL_EIGHTHS[cellEighths];
        }
        return rows;
    }

    /**
     * A horizontal bar filling from the left.
     */
    public static String row(double level, int width) {
        final StringBuilder cells = new StringBuilder(width);
        final int eighths = eighths(level, width);
        for (int cell = 0; cell < width; cell++) {
            final int cellEighths = Math.clamp(eighths - cell * EIGHTHS, 0, EIGHTHS);
            cells.append(cellEighths == EIGHTHS ? FULL : HORIZONTAL_EIGHTHS[cellEighths]);
        }
        return cells.toString();
    }

    private static int eighths(double level, int cells) {
        return (int) Math.round(Math.clamp(level, 0, 1) * cells * EIGHTHS);
    }
}

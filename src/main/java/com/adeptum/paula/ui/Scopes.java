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

package com.adeptum.paula.ui;

import java.util.OptionalInt;

/**
 * The grid the channel scopes are drawn on: equal cells filled left to right and top to bottom, placed at a
 * corner of the screen so that a click can be traced back to the channel under it.
 */
public record Scopes(int top, int left, int columns, int rows, int cellWidth, int cellHeight, int channels) {

    public static final Scopes NONE = new Scopes(0, 0, 0, 0, 0, 0, 0);

    static final int LABEL_WIDTH = 3;

    private static final int MIN_CELL_WIDTH = 12;

    /**
     * As square a grid as the room allows, each cell wide enough for its label and at least one row tall.
     */
    static Scopes grid(int top, int left, int width, int height, int channels) {
        final int columns = Math.clamp((int) Math.ceil(Math.sqrt(channels)), 1, Math.max(1, width / MIN_CELL_WIDTH));
        final int rows = (int) Math.ceil((double) channels / columns);
        return new Scopes(top, left, columns, rows,
                Math.max(LABEL_WIDTH + 1, width / columns), Math.max(1, height / rows), channels);
    }

    /**
     * The channel at a screen cell, numbered from one, or nothing when the click landed outside the scopes.
     */
    public OptionalInt channelAt(int column, int row) {
        if (column < left || row < top || cellWidth <= 0 || cellHeight <= 0) {
            return OptionalInt.empty();
        }
        final int x = (column - left) / cellWidth;
        final int y = (row - top) / cellHeight;
        final int index = y * columns + x;
        return x < columns && y < rows && index < channels ? OptionalInt.of(index + 1) : OptionalInt.empty();
    }
}

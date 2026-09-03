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

import java.util.List;

/**
 * Draws a waveform with braille cells, which pack two columns and four rows of dots into one character.
 */
public final class Braille {

    private Braille() {
    }

    /**
     * Plots samples in the range -1..1 across the given cell grid, resampling to one dot column per column.
     */
    public static List<String> plot(double[] samples, int widthCells, int heightCells) {
        final BrailleCanvas canvas = new BrailleCanvas(widthCells, heightCells);
        final int columns = canvas.dotColumns();
        final int rows = canvas.dotRows();
        for (int column = 0; column < columns && rows > 0 && samples.length > 0; column++) {
            final double value = Math.clamp(samples[(int) ((long) column * samples.length / columns)], -1, 1);
            canvas.lightDot(column, (int) Math.round((1 - value) / 2 * (rows - 1)));
        }
        return canvas.rows();
    }
}

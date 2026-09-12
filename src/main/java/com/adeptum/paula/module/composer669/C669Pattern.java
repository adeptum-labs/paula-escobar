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

package com.adeptum.paula.module.composer669;

/**
 * Sixty-four rows of eight channels, with the speed the pattern starts at and the row it is broken off after;
 * both are the tracker's per-pattern settings rather than anything written into the rows.
 */
record C669Pattern(int speed, int breakRow, C669Cell[][] cells) {

    static final int ROWS = 64;
    static final int CHANNELS = 8;

    C669Cell cell(int row, int channel) {
        return cells[row][channel];
    }
}

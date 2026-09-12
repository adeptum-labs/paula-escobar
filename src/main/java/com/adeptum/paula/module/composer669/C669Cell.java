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
 * One slot of a pattern: a note with its instrument, a volume, and an effect with its parameter, each of which
 * the tracker may leave out.
 */
record C669Cell(int note, int instrument, int volume, int command, int parameter) {

    static final int NONE = -1;

    static final C669Cell EMPTY = new C669Cell(NONE, NONE, NONE, NONE, 0);

    boolean hasNote() {
        return note != NONE;
    }

    boolean hasVolume() {
        return volume != NONE;
    }

    boolean hasCommand() {
        return command != NONE;
    }
}

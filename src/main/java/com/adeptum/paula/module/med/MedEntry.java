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

package com.adeptum.paula.module.med;

/**
 * One cell of a block: a note with its instrument and one effect, all as stored in the module.
 */
record MedEntry(int note, int instrument, int command, int parameter) {

    static final MedEntry EMPTY = new MedEntry(0, 0, 0, 0);

    boolean hasNote() {
        return note > 0;
    }

    /**
     * An instrument on a line that carries no note holds the note sounding on that track rather than starting
     * it again, which is how OctaMED writes the symbol that sustains a note.
     */
    boolean isHoldSymbol() {
        return note == 0 && instrument > 0;
    }
}

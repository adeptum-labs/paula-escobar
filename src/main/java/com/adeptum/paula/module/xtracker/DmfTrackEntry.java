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

package com.adeptum.paula.module.xtracker;

/**
 * What one track holds on a row where the tracker wrote something for it; a field the entry lacks is
 * {@link #NONE}. Notes 1 to 108 are C-0 to B-8, the same notes above 128 are buffer notes that only mark where a
 * portamento heads, and 255 is a note off.
 */
record DmfTrackEntry(int instrument, int note, int volume, int instrumentEffect, int instrumentData, int noteEffect,
        int noteData, int volumeEffect, int volumeData) {

    static final int NONE = -1;
    static final int NOTE_OFF = 255;
    static final int BUFFER_OFFSET = 128;
    private static final int LAST_NOTE = 108;

    boolean hasInstrument() {
        return instrument > 0;
    }

    boolean hasNote() {
        return note >= 1 && note <= LAST_NOTE;
    }

    boolean hasBufferNote() {
        return note > BUFFER_OFFSET && note <= BUFFER_OFFSET + LAST_NOTE;
    }

    boolean isNoteOff() {
        return note == NOTE_OFF;
    }

    boolean hasVolume() {
        return volume != NONE;
    }
}

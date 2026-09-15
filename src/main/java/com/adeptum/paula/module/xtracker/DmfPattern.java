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
 * One pattern unpacked into rows: the global track's command on each row and every track's entry, either of
 * which is null where the tracker wrote nothing. The beat is the number of rows to a beat, 0 for none.
 */
record DmfPattern(int tracks, int beat, DmfGlobalEntry[] global, DmfTrackEntry[][] entries) {

    int rows() {
        return global.length;
    }

    DmfGlobalEntry global(int row) {
        return global[row];
    }

    DmfTrackEntry entry(int row, int track) {
        return track < tracks ? entries[row][track] : null;
    }

    /**
     * The rows from {@code row} up to the row of the track's next entry in this pattern, or up to the pattern's
     * end when no entry follows; always at least one row.
     */
    int span(int row, int track) {
        if (track >= tracks) {
            return rows() - row;
        }
        for (int next = row + 1; next < rows(); next++) {
            if (entries[next][track] != null) {
                return next - row;
            }
        }
        return rows() - row;
    }

    /**
     * The same span as {@link #span(int, int)}, but to the global track's next entry.
     */
    int globalSpan(int row) {
        for (int next = row + 1; next < rows(); next++) {
            if (global[next] != null) {
                return next - row;
            }
        }
        return rows() - row;
    }
}

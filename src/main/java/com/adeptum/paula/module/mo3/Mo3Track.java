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
 *
 * The layout follows Load_mo3.cpp of OpenMPT, Copyright © 2004-2026 the
 * OpenMPT project developers and Copyright © 1997-2003 Olivier Lapicque,
 * licensed under the three-clause BSD licence and used here under the GNU
 * General Public License.
 */

package com.adeptum.paula.module.mo3;

/**
 * Walks one track of an MO3 into the rows it fills.
 *
 * <p>A track is a run of events rather than a row for every row: each begins with a byte saying how many
 * commands make it up and how many rows it covers, so a note held for four rows costs one event and a track
 * of nothing at all costs a single zero. Running out of events before the pattern ends leaves the rest of it
 * empty, and a track longer than the pattern is cut where the pattern stops.</p>
 */
final class Mo3Track {

    private static final int COMMANDS_MASK = 0x0F;
    private static final int ROWS_SHIFT = 4;
    private static final int COMMAND_LENGTH = 2;

    private Mo3Track() {
    }

    /**
     * The rows of one track, an entry per row of the pattern and nothing where the track says nothing.
     */
    static Mo3Event[] rows(byte[] track, int rows, Mo3Kind kind) {
        final Mo3Event[] events = new Mo3Event[rows];
        int at = 0;
        int row = 0;
        while (row < rows && at < track.length) {
            final int header = track[at++] & 0xFF;
            if (header == 0) {
                break;
            }
            final int commands = header & COMMANDS_MASK;
            if (at + commands * COMMAND_LENGTH > track.length) {
                break;
            }

            final Mo3Event event = new Mo3Event();
            for (int command = 0; command < commands; command++) {
                Mo3Commands.apply(event, track[at] & 0xFF, track[at + 1] & 0xFF, kind);
                at += COMMAND_LENGTH;
            }

            final int until = Math.min(row + (header >> ROWS_SHIFT), rows);
            while (row < until) {
                events[row++] = event;
            }
        }
        return events;
    }
}

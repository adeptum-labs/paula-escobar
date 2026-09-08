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

package com.adeptum.paula.module.mo3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * A track holds runs of rows rather than a row apiece: the byte that opens each event says how many commands
 * make it up in its low half and how many rows it covers in its high half.
 */
class Mo3TrackTest {

    private static final int ROWS = 8;
    private static final int MIDDLE_C = 0x30;
    private static final byte END = 0;

    @Test
    void soundsTheNoteWrittenOnTheFirstRow() {
        final Mo3Event[] rows = Mo3Track.rows(track(0x12, 0x01, MIDDLE_C, 0x02, 0x00, END), ROWS,
                Mo3Kind.PROTRACKER);

        assertNotNull(rows[0]);
        assertEquals(MIDDLE_C + 1, rows[0].note());
        assertEquals(1, rows[0].instrument());
        assertNull(rows[1], "and nothing on the rows after it");
    }

    @Test
    void holdsAnEventForAsManyRowsAsItCovers() {
        final Mo3Event[] rows = Mo3Track.rows(track(0x41, 0x01, MIDDLE_C, END), ROWS, Mo3Kind.PROTRACKER);

        assertSame(rows[0], rows[3], "one event fills the four rows it says it covers");
        assertNull(rows[4]);
    }

    @Test
    void readsAnEventThatCoversNoRowsAsNothing() {
        final Mo3Event[] rows = Mo3Track.rows(track(0x01, 0x01, MIDDLE_C, 0x11, 0x02, 0x03, END), ROWS,
                Mo3Kind.PROTRACKER);

        assertEquals(4, rows[0].instrument(), "the event after it is the one that fills the row");
        assertEquals(Mo3Event.NO_NOTE, rows[0].note());
    }

    @Test
    void readsATrackOfNothingAsNothing() {
        final Mo3Event[] rows = Mo3Track.rows(track(END), ROWS, Mo3Kind.PROTRACKER);

        for (final Mo3Event row : rows) {
            assertNull(row);
        }
    }

    @Test
    void stopsWhereThePatternStops() {
        final Mo3Event[] rows = Mo3Track.rows(track(0xF1, 0x01, MIDDLE_C, END), 2, Mo3Kind.PROTRACKER);

        assertEquals(2, rows.length);
        assertNotNull(rows[1]);
    }

    @Test
    void stopsAtACommandTheTrackHasNoRoomFor() {
        final Mo3Event[] rows = Mo3Track.rows(track(0x11, 0x01), ROWS, Mo3Kind.PROTRACKER);

        assertNull(rows[0], "a command cut in half sounds nothing rather than reading past the track");
    }

    @Test
    void stopsAtATrackThatNeverEnds() {
        final Mo3Event[] rows = Mo3Track.rows(track(0x11, 0x01, MIDDLE_C), ROWS, Mo3Kind.PROTRACKER);

        assertNotNull(rows[0]);
        assertNull(rows[1]);
    }

    private static byte[] track(int... bytes) {
        final byte[] track = new byte[bytes.length];
        for (int at = 0; at < bytes.length; at++) {
            track[at] = (byte) bytes[at];
        }
        return track;
    }
}

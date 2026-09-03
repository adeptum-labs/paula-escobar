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

package com.adeptum.paula.playback;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AudioTapTest {

    private final AudioTap tap = new AudioTap(4);

    @Test
    void snapshotReturnsTheNewestFramesNewestLast() {
        tap.write(frames(1, 2, 3), 3);

        assertArrayEquals(frames(2, 3), tap.snapshot(2));
        assertEquals(3, tap.written());
    }

    @Test
    void historyWrapsAroundTheRing() {
        tap.write(frames(1, 2, 3), 3);
        tap.write(frames(4, 5, 6), 3);

        assertArrayEquals(frames(3, 4, 5, 6), tap.snapshot(4));
        assertEquals(6, tap.written());
    }

    @Test
    void missingHistoryIsSilence() {
        tap.write(frames(7), 1);

        assertArrayEquals(frames(0, 0, 7), tap.snapshot(3));
    }

    @Test
    void writesLongerThanTheRingKeepTheTail() {
        tap.write(frames(1, 2, 3, 4, 5, 6), 6);

        assertArrayEquals(frames(3, 4, 5, 6), tap.snapshot(4));
    }

    private static short[] frames(int... values) {
        final short[] interleaved = new short[values.length * 2];
        for (int i = 0; i < values.length; i++) {
            interleaved[i * 2] = (short) values[i];
            interleaved[i * 2 + 1] = (short) -values[i];
        }
        return interleaved;
    }
}

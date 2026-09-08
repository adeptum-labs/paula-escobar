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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaybackDelayTest {

    private final PlaybackDelay delay = new PlaybackDelay(1000);

    @Test
    void givesBackTheReadingTakenAsTheFrameWasWritten() {
        delay.record(100, List.of(), Duration.ofSeconds(1));
        delay.record(200, List.of(), Duration.ofSeconds(2));
        delay.record(300, List.of(), Duration.ofSeconds(3));

        assertEquals(Duration.ofSeconds(2), delay.at(200).position());
        assertEquals(Duration.ofSeconds(2), delay.at(250).position(), "the nearest reading before");
        assertEquals(Duration.ofSeconds(3), delay.at(999).position());
    }

    @Test
    void givesTheOldestForAMomentBeforeAnyReading() {
        delay.record(100, List.of(), Duration.ofSeconds(1));

        assertEquals(Duration.ofSeconds(1), delay.at(0).position());
    }

    @Test
    void forgetsReadingsOlderThanItKeeps() {
        delay.record(100, List.of(), Duration.ofSeconds(1));
        delay.record(2000, List.of(), Duration.ofSeconds(20));

        assertEquals(Duration.ofSeconds(20), delay.at(100).position(), "the reading at 100 has been let go");
    }

    @Test
    void hasNothingBeforeAnythingWasRecorded() {
        assertNull(delay.at(5));
        delay.record(1, List.of(), Duration.ZERO);
        delay.clear();
        assertNull(delay.at(5));
    }
}

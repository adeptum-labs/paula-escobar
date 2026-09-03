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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeadlineTest {

    @Test
    void passesOnceTheDurationHasElapsed() {
        final long[] now = {-Duration.ofDays(1).toNanos()};
        final Deadline deadline = Deadline.after(Duration.ofSeconds(2), () -> now[0]);

        assertFalse(deadline.passed());
        now[0] += Duration.ofSeconds(2).toNanos();
        assertTrue(deadline.passed());
    }

    @Test
    void neverIsNeverPassed() {
        assertFalse(Deadline.never().passed());
    }
}

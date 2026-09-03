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

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * A point in time after which the session ends by itself, or none at all.
 */
public final class Deadline {

    private final Long endNanos;
    private final LongSupplier clock;

    private Deadline(final Long endNanos, final LongSupplier clock) {
        this.endNanos = endNanos;
        this.clock = clock;
    }

    public static Deadline never() {
        return new Deadline(null, System::nanoTime);
    }

    public static Deadline after(final Duration duration) {
        return after(duration, System::nanoTime);
    }

    static Deadline after(final Duration duration, final LongSupplier clock) {
        return new Deadline(clock.getAsLong() + duration.toNanos(), clock);
    }

    public boolean passed() {
        return endNanos != null && clock.getAsLong() - endNanos >= 0;
    }
}

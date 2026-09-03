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

import java.util.Optional;

/**
 * What the loader thread is busy with, for the screen to say while it waits. Written by whoever is doing the
 * work and read by the interface a frame later, so it is held in one field rather than locked.
 */
public final class Progress {

    /**
     * What is being done, and how far along it is: a fraction where the whole of it is known, or a count of
     * what has been got through where it is not.
     */
    public record Step(String text, double fraction, long count) {

        public boolean measured() {
            return fraction >= 0;
        }
    }

    private static final double UNMEASURED = -1;

    private volatile Step step;

    /**
     * Something being done whose end is not in sight; the count is what has been got through so far.
     */
    public void counted(String text, long count) {
        step = new Step(text, UNMEASURED, count);
    }

    /**
     * Something being done whose end is known, given as a part of one.
     */
    public void measured(String text, double fraction) {
        step = new Step(text, Math.clamp(fraction, 0, 1), 0);
    }

    public void clear() {
        step = null;
    }

    public Optional<Step> step() {
        return Optional.ofNullable(step);
    }
}

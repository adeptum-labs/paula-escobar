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

package com.adeptum.paula.module.ay;

import java.util.OptionalLong;

/**
 * Where a tune's register writes come from, a frame at a time: a recording replays what was written down, a
 * tracker works it out as it goes. Everything above this reads the same either way.
 */
public interface AySource {

    /**
     * Fills the registers for the next frame and moves on, or answers false where the tune has ended.
     */
    boolean nextFrame(int[] registers);

    void rewindTo(long frame);

    /**
     * How many frames the tune runs to, where that is known before playing it.
     */
    OptionalLong frames();
}

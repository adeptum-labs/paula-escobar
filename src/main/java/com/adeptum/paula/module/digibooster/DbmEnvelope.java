/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

package com.adeptum.paula.module.digibooster;

/**
 * A volume or panning envelope as a series of points; the loop and the two sustain points are point numbers,
 * {@link #NONE} when the module leaves them off.
 */
record DbmEnvelope(int instrument, int sections, int loopFirst, int loopLast, int sustainA, int sustainB,
                   int[] positions, int[] values) {

    static final int NONE = 0xFFFF;
    static final int MAX_SECTIONS = 31;
}

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
 * The echo the module asks for, with the tracks it is switched on for; the levels are the raw eighth-bit values
 * the format stores.
 */
record DbmEcho(int delay, int feedback, int mix, int cross, boolean[] tracks) {

    static final int DEFAULT_DELAY = 0x40;
    static final int DEFAULT_FEEDBACK = 0x80;
    static final int DEFAULT_MIX = 0x80;
    static final int DEFAULT_CROSS = 0xFF;

    static DbmEcho off(int tracks) {
        return new DbmEcho(DEFAULT_DELAY, DEFAULT_FEEDBACK, DEFAULT_MIX, DEFAULT_CROSS, new boolean[tracks]);
    }
}

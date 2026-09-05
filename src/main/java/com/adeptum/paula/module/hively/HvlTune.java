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

package com.adeptum.paula.module.hively;

import java.util.List;

/**
 * A parsed AHX or HivelyTracker tune: its arrangement, tracks and instruments, plus the stereo
 * mix derived from the module's separation. Playback state is kept by the engine, not here.
 */
record HvlTune(String name, int version, int channels, int positionCount, int restart,
               int speedMultiplier, int trackLength, int trackCount, int[] subsongs,
               List<HvlPosition> positions, HvlStep[][] tracks,
               List<HvlInstrument> instruments,
               int mixGain, int defaultPanLeft, int defaultPanRight, int stereo) {

    static final int MAX_CHANNELS = 16;
}

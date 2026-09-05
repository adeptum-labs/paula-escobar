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

/**
 * A synthesised instrument: its waveform and volume, the filter and square-wave sweeps applied
 * to it, its vibrato, its hard-cut release and its envelope and performance list.
 */
record HvlInstrument(String name, int volume, int waveLength, int filterLowerLimit,
                     int filterUpperLimit, int filterSpeed, int squareLowerLimit,
                     int squareUpperLimit, int squareSpeed, int vibratoDelay, int vibratoSpeed,
                     int vibratoDepth, boolean hardCutRelease, int hardCutReleaseFrames,
                     HvlEnvelope envelope, HvlPlaylist playlist) {
}

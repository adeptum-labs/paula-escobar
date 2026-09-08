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

package com.adeptum.paula.module.mo3;

import java.io.IOException;

/**
 * The vibrato a sample applies to itself, whatever the pattern asks for. Fast Tracker keeps it on the
 * instrument and the rest keep it on the sample, so both places read the same four bytes.
 */
record Mo3Vibrato(int type, int sweep, int depth, int rate) {

    static Mo3Vibrato read(Mo3Bytes bytes) throws IOException {
        return new Mo3Vibrato(bytes.u8(), bytes.u8(), bytes.u8(), bytes.u8());
    }
}

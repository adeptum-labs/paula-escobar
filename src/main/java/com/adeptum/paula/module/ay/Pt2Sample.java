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

/**
 * The shape of a note over time, a line an interrupt, looping back to where it says once it runs out.
 */
public record Pt2Sample(Pt2SampleLine[] lines, int loop) {

    public static final Pt2Sample EMPTY = new Pt2Sample(new Pt2SampleLine[0], 0);

    /**
     * A line the sample does not have sounds as nothing at all.
     */
    public Pt2SampleLine line(int at) {
        return at < lines.length ? lines[at] : Pt2SampleLine.SILENT;
    }
}

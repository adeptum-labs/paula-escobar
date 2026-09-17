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

import java.util.List;

/**
 * What one channel of one line says. Most fields say nothing most of the time, which is kept as a value of
 * its own rather than as the default a player would otherwise mistake for an instruction.
 */
public record Pt2Cell(int enabled, int note, int sample, int ornament, int volume, List<Pt2Command> commands) {

    public static final int KEEP = -1;
    public static final int OFF = 0;
    public static final int ON = 1;

    public static final Pt2Cell EMPTY = new Pt2Cell(KEEP, KEEP, KEEP, KEEP, KEEP, List.of());

    public Pt2Cell {
        commands = List.copyOf(commands);
    }
}

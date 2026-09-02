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

package com.adeptum.paula.demozoo;

import java.util.List;

/**
 * The party series Paula offers, identified by their Demozoo party series ids.
 */
public record CuratedSeries(int id, String name) {

    public static final List<CuratedSeries> ALL = List.of(
            new CuratedSeries(19, "The Party"),
            new CuratedSeries(2, "Assembly"),
            new CuratedSeries(62, "Mekka & Symposium"),
            new CuratedSeries(11, "The Gathering"),
            new CuratedSeries(39, "Saturne Party"),
            new CuratedSeries(112, "Wired"),
            new CuratedSeries(1, "Breakpoint"),
            new CuratedSeries(10, "Revision"),
            new CuratedSeries(111, "Evoke"),
            new CuratedSeries(236, "Icing"),
            new CuratedSeries(190, "Intel Outside"),
            new CuratedSeries(222, "Gravity"),
            new CuratedSeries(436, "Xenium"),
            new CuratedSeries(41, "Symphony"));
}

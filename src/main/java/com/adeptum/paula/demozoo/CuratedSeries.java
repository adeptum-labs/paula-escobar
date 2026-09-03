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

package com.adeptum.paula.demozoo;

import java.util.List;

/**
 * The party series Paula offers, identified by their Demozoo party series ids. Each one is a series whose
 * parties Demozoo holds music competition results for; the copy parties of the eighties are recorded there
 * without any, so a series of those would open on nothing to play.
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
            new CuratedSeries(41, "Symphony"),
            new CuratedSeries(32, "X"),
            new CuratedSeries(76, "Euskal"),
            new CuratedSeries(72, "Buenzli"),
            new CuratedSeries(64, "Dreamhack"),
            new CuratedSeries(23, "Kindergarden"),
            new CuratedSeries(8, "Solskogen"),
            new CuratedSeries(51, "TRSAC"),
            new CuratedSeries(53, "Outline"),
            new CuratedSeries(42, "Function"),
            new CuratedSeries(59, "Árok"),
            new CuratedSeries(85, "Chaos Constructions"),
            new CuratedSeries(87, "DiHalt"),
            new CuratedSeries(583, "Nordlicht"),
            new CuratedSeries(893, "Deadline"),
            new CuratedSeries(107, "Sundown"),
            new CuratedSeries(21, "Riverwash"),
            new CuratedSeries(48, "Alternative Party"),
            new CuratedSeries(175, "Instanssi"),
            new CuratedSeries(114, "Underground Conference"),
            new CuratedSeries(206, "Vammala Party"),
            new CuratedSeries(56, "Scene Event"),
            new CuratedSeries(33, "Forever"),
            new CuratedSeries(133, "Silly Venture"),
            new CuratedSeries(17, "Datastorm"),
            new CuratedSeries(13, "Sommarhack"),
            new CuratedSeries(781, "Gerp"),
            new CuratedSeries(1579, "Lovebyte"),
            new CuratedSeries(37, "Little Computer People"),
            new CuratedSeries(50, "Compusphere"),
            new CuratedSeries(54, "Birdie"),
            new CuratedSeries(46, "Remedy"),
            new CuratedSeries(141, "Abduction"),
            new CuratedSeries(131, "Bizarre"),
            new CuratedSeries(127, "Scenest"),
            new CuratedSeries(165, "Somewhere In Holland"),
            new CuratedSeries(38, "South Sealand"),
            new CuratedSeries(379, "Takeover"),
            new CuratedSeries(249, "Juhla"));
}

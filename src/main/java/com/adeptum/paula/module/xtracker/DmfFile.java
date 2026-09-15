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

package com.adeptum.paula.module.xtracker;

import java.util.List;

/**
 * An X-Tracker module as read: the song's text, its number of tracks, the order it plays its patterns in, and
 * the samples its instruments number from one.
 */
record DmfFile(int version, String title, String composer, int tracks, int[] orders, List<DmfPattern> patterns,
        List<DmfSample> samples) {

    DmfPattern patternAt(int order) {
        return order < orders.length ? patterns.get(orders[order]) : null;
    }

    DmfSample sample(int instrument) {
        return instrument >= 1 && instrument <= samples.size() ? samples.get(instrument - 1) : null;
    }
}

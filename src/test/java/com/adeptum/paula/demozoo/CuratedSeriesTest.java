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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CuratedSeriesTest {

    @Test
    void offersEachSeriesOnlyOnce() {
        final Set<Integer> ids = CuratedSeries.ALL.stream().map(CuratedSeries::id).collect(Collectors.toSet());
        final Set<String> names = CuratedSeries.ALL.stream().map(CuratedSeries::name).collect(Collectors.toSet());

        assertEquals(CuratedSeries.ALL.size(), ids.size(), "a series listed twice would be browsed twice");
        assertEquals(CuratedSeries.ALL.size(), names.size(), "two lines reading the same tell the two apart by nothing");
    }

    @Test
    void namesEverySeriesItAsksDemozooFor() {
        assertTrue(CuratedSeries.ALL.stream().allMatch(series -> series.id() > 0 && !series.name().isBlank()));
    }
}

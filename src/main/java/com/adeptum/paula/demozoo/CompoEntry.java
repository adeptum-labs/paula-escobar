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

import java.util.Set;

/**
 * One ranked result of a competition. The ranking is Demozoo's display string and is empty for unranked entries.
 */
public record CompoEntry(int position, String ranking, int productionId, String title, String author, Set<Integer> typeIds) {

    private static final int STREAMING_MUSIC = 30;
    private static final int EXECUTABLE_MUSIC = 31;
    private static final int EXECUTABLE_MUSIC_32K = 32;
    private static final int EXECUTABLE_MUSIC_64K = 38;
    private static final Set<Integer> UNPLAYABLE_TYPES = Set.of(STREAMING_MUSIC, EXECUTABLE_MUSIC, EXECUTABLE_MUSIC_32K, EXECUTABLE_MUSIC_64K);
    private static final String UNRANKED = "-";

    public boolean likelyPlayable() {
        return typeIds.stream().noneMatch(UNPLAYABLE_TYPES::contains);
    }

    public String placing() {
        return ranking.isEmpty() ? UNRANKED : ranking;
    }
}

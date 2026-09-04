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
import java.util.Locale;
import java.util.Set;

public record Competition(int id, String name, int typeId, String typeName, List<CompoEntry> entries) {

    /**
     * Competitions run for a music format nothing here can decode. Demozoo calls their entries streaming
     * music, the same as it calls an MP3, and the files sit inside archives, so the competition's own name is
     * the only word on it before anything is fetched. A ReBirth song holds no audio at all: it is a page of
     * knob settings for a synthesiser that went out of print, and only that synthesiser can sound it.
     */
    private static final Set<String> UNPLAYABLE = Set.of("rebirth");

    public boolean unsupportedFormat() {
        final String lower = name.toLowerCase(Locale.ROOT);
        return UNPLAYABLE.stream().anyMatch(lower::contains);
    }
}

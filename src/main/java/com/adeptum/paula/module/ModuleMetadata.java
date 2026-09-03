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

package com.adeptum.paula.module;

import java.util.List;
import lombok.Builder;

/**
 * What a module tells about itself. The song length is counted in the format's own unit, positions for tracker
 * modules and subtunes for SID files; credits are free-form lines such as author and release.
 */
@Builder
public record ModuleMetadata(
        String title,
        ModuleFormat format,
        int channels,
        int songLength,
        String lengthUnit,
        List<String> instruments,
        List<String> credits) {

    private static final String POSITIONS = "positions";

    public ModuleMetadata {
        title = title == null ? "" : title;
        lengthUnit = lengthUnit == null ? POSITIONS : lengthUnit;
        instruments = instruments == null ? List.of() : List.copyOf(instruments);
        credits = credits == null ? List.of() : List.copyOf(credits);
    }

    public String displayTitle() {
        return title.isBlank() ? "<untitled>" : title;
    }

    public String displayLength() {
        return songLength + " " + lengthUnit;
    }
}

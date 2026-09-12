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
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * What a module tells about itself. The song length is counted in the format's own unit, positions for tracker
 * modules and subtunes for SID files; credits are free-form lines such as author and release. All of it goes
 * straight to the terminal, so no control character a file carries in its names is let through.
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

    /**
     * An ANSI control sequence, whether introduced the seven-bit way or by the single C1 byte.
     */
    private static final Pattern CONTROL_SEQUENCE = Pattern.compile("(\\[|)[0-?]*[ -/]*[@-~]");

    public ModuleMetadata {
        title = printable(title == null ? "" : title);
        lengthUnit = lengthUnit == null ? POSITIONS : lengthUnit;
        instruments = instruments == null ? List.of() : instruments.stream().map(ModuleMetadata::printable).toList();
        credits = credits == null ? List.of() : credits.stream().map(ModuleMetadata::printable).toList();
    }

    private static String printable(String text) {
        return CONTROL_SEQUENCE.matcher(text).replaceAll("").codePoints().filter(c -> !Character.isISOControl(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    public String displayTitle() {
        return title.isBlank() ? "<untitled>" : title;
    }

    public String displayLength() {
        return songLength + " " + lengthUnit;
    }
}

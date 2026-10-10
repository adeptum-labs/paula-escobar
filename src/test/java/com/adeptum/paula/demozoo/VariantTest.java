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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariantTest {

    private static Variant variant(String format, boolean plays) {
        return new Variant(Path.of("x." + format.toLowerCase()), format, plays);
    }

    @Test
    void bracketsTheFormatThatPlays() {
        assertEquals("TAP/[XM]/MP3", Variant.tag(List.of(variant("TAP", false), variant("XM", true), variant("MP3", false))));
    }

    @Test
    void saysNothingForFewerThanTwoFormats() {
        assertEquals("", Variant.tag(List.of()));
        assertEquals("", Variant.tag(List.of(variant("XM", true))));
    }
}

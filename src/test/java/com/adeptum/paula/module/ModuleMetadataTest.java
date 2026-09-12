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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleMetadataTest {

    private static final String SHIFT_OUT = "";
    private static final String ESCAPE = "[31m";
    private static final String CONTROL_SEQUENCE_INTRODUCER = "";

    /**
     * Whatever a module says about itself is written straight into the terminal, and a control character in
     * it, a shift out from a code page 437 note sign say, leaves the terminal drawing letters for lines.
     */
    @Test
    void keepsControlCharactersOutOfWhatItShows() {
        final ModuleMetadata meta = ModuleMetadata.builder()
                .title(SHIFT_OUT + " Euphorium " + SHIFT_OUT)
                .instruments(List.of(ESCAPE + "bass", "lead" + CONTROL_SEQUENCE_INTRODUCER + "1"))
                .credits(List.of("by\tsomeone", ""))
                .build();

        assertEquals(" Euphorium ", meta.title());
        assertEquals(List.of("bass", "lead1"), meta.instruments());
        assertEquals(List.of("bysomeone", ""), meta.credits());
    }

    @Test
    void leavesTextWithoutControlCharactersAlone() {
        final ModuleMetadata meta = ModuleMetadata.builder().title("Paula ♫ Ünïcode").instruments(List.of("kick")).build();

        assertEquals("Paula ♫ Ünïcode", meta.title());
        assertEquals(List.of("kick"), meta.instruments());
    }
}

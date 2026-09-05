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

package com.adeptum.paula.module.sap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The engine reads its 6502 players off the classpath by name and length, so every one it asks for has to be
 * there at the size it expects.
 */
class AsapResourcesTest {

    private static final Map<String, Integer> PLAYERS = Map.ofEntries(
            Map.entry("cm3.obx", 2022), Map.entry("cmc.obx", 2019), Map.entry("cmr.obx", 2019),
            Map.entry("cms.obx", 2757), Map.entry("dlt.obx", 2125), Map.entry("fc.obx", 1220),
            Map.entry("mpt.obx", 2233), Map.entry("rmt4.obx", 2007), Map.entry("rmt8.obx", 2275),
            Map.entry("tm2.obx", 3698), Map.entry("tmc.obx", 2671), Map.entry("xexb.obx", 183),
            Map.entry("xexd.obx", 114), Map.entry("xexinfo.obx", 178));

    @Test
    void shipsEveryPlayerTheEngineAsksFor() throws IOException {
        for (final Map.Entry<String, Integer> player : PLAYERS.entrySet()) {
            try (InputStream in = AsapResourcesTest.class.getResourceAsStream("/net/sf/asap/" + player.getKey())) {
                assertNotNull(in, player.getKey() + " is missing");
                assertEquals(player.getValue(), in.readAllBytes().length, player.getKey());
            }
        }
    }
}

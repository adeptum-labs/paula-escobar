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

package com.adeptum.paula.favourites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonPreferredFormatsTest {

    private static final String FILE = "preferred-formats.json";

    @Test
    void rememberedChoicesSurviveARestart(@TempDir Path dir) throws IOException {
        new JsonPreferredFormats(new DataDirectory(dir)).set("109980", "extracted/The Invasion.tap");

        final PreferredFormats again = new JsonPreferredFormats(new DataDirectory(dir));

        assertEquals(Optional.of("extracted/The Invasion.tap"), again.of("109980"));
        assertEquals(Optional.empty(), again.of("1"));
    }

    @Test
    void aLaterChoiceReplacesAnEarlierOne(@TempDir Path dir) throws IOException {
        final PreferredFormats formats = new JsonPreferredFormats(new DataDirectory(dir));
        formats.set("7", "extracted/a.mp3");
        formats.set("7", "extracted/a.xm");

        assertEquals(Optional.of("extracted/a.xm"), new JsonPreferredFormats(new DataDirectory(dir)).of("7"));
    }

    @Test
    void aMissingFileMeansNoChoices(@TempDir Path dir) {
        assertEquals(Optional.empty(), new JsonPreferredFormats(new DataDirectory(dir)).of("7"));
    }

    @Test
    void aCorruptFileMeansNoChoicesAndIsReplacedByTheNextOne(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), "{not json");
        final PreferredFormats formats = new JsonPreferredFormats(new DataDirectory(dir));
        assertEquals(Optional.empty(), formats.of("7"));

        formats.set("7", "extracted/a.xm");

        assertEquals(Optional.of("extracted/a.xm"), new JsonPreferredFormats(new DataDirectory(dir)).of("7"));
        assertTrue(Files.readString(dir.resolve(FILE)).contains("a.xm"));
    }

    @Test
    void anEntryOfTheWrongTypeIsIgnoredWithoutLosingTheOthers(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), "{\"1\": 5, \"2\": \"extracted/b.tap\"}");

        final PreferredFormats formats = new JsonPreferredFormats(new DataDirectory(dir));

        assertEquals(Optional.empty(), formats.of("1"));
        assertEquals(Optional.of("extracted/b.tap"), formats.of("2"));
    }

    @Test
    void theInMemoryStoreHoldsChoicesForTheSessionOnly() throws IOException {
        final PreferredFormats formats = PreferredFormats.inMemory();
        formats.set("7", "extracted/a.xm");

        assertEquals(Optional.of("extracted/a.xm"), formats.of("7"));
        assertEquals(Optional.empty(), PreferredFormats.inMemory().of("7"));
    }
}

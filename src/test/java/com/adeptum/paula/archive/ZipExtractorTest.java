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

package com.adeptum.paula.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestArchives;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipExtractorTest {

    private static final byte[] README = "hello".getBytes(StandardCharsets.US_ASCII);

    private final ZipExtractor extractor = new ZipExtractor();

    @Test
    void extractsOnlyWantedEntriesKeepingTheirPaths(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("readme.txt", README);
        entries.put("music/", new byte[0]);
        entries.put("music/tune.mod", TestModules.proTracker());
        entries.put("mod.old", README);
        final Path archive = Files.write(dir.resolve("a.zip"), TestArchives.zip(entries));
        final Path into = dir.resolve("out");

        extractor.extract(archive, into, name -> name.endsWith(".mod") || name.startsWith("mod."));

        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(into.resolve("music/tune.mod")));
        assertArrayEquals(README, Files.readAllBytes(into.resolve("mod.old")));
        assertFalse(Files.exists(into.resolve("readme.txt")));
        try (Stream<Path> files = Files.walk(into)) {
            assertEquals(2, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void refusesEntriesEscapingTheTargetDirectory(@TempDir Path dir) throws IOException {
        final Path archive = Files.write(dir.resolve("a.zip"), TestArchives.zip(Map.of("../evil.mod", README)));
        assertThrows(IOException.class, () -> extractor.extract(archive, dir.resolve("out"), name -> true));
        assertFalse(Files.exists(dir.resolve("evil.mod")));
    }

    @Test
    void recognisesZipMagic() throws IOException {
        assertTrue(extractor.matches(TestArchives.zip(Map.of("a", README))));
        assertFalse(extractor.matches(README));
    }

    @Test
    void wantedNamesUseForwardSlashes(@TempDir Path dir) throws IOException {
        final Path archive = Files.write(dir.resolve("a.zip"), TestArchives.zip(Map.of("dir\\tune.mod", README)));
        extractor.extract(archive, dir.resolve("out"), name -> name.equals("dir/tune.mod"));
        assertEquals(List.of("tune.mod"), Files.list(dir.resolve("out/dir")).map(p -> p.getFileName().toString()).toList());
    }
}

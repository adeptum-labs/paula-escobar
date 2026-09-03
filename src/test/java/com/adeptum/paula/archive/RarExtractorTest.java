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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestArchives;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RarExtractorTest {

    private static final byte[] TUNE = "a module, of a sort".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NOTES = "read me".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RAR5_SIGNATURE = {'R', 'a', 'r', '!', 0x1A, 0x07, 0x01, 0x00};

    private final RarExtractor extractor = new RarExtractor();

    @Test
    void unpacksTheEntriesThatAreWanted(@TempDir Path dir) throws IOException {
        final Path into = Files.createDirectory(dir.resolve("into"));

        extractor.extract(archive(dir, Map.of("tune.mod", TUNE)), into, name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(into.resolve("tune.mod")));
    }

    @Test
    void leavesTheEntriesThatAreNotWanted(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("tune.mod", TUNE);
        entries.put("notes.txt", NOTES);
        final Path into = Files.createDirectory(dir.resolve("into"));

        extractor.extract(archive(dir, entries), into, name -> name.endsWith(".mod"));

        assertTrue(Files.exists(into.resolve("tune.mod")));
        assertFalse(Files.exists(into.resolve("notes.txt")));
    }

    @Test
    void keepsTheFoldersTheArchiveNames(@TempDir Path dir) throws IOException {
        final Path into = Files.createDirectory(dir.resolve("into"));

        extractor.extract(archive(dir, Map.of("music/tune.mod", TUNE)), into, name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(into.resolve("music").resolve("tune.mod")));
    }

    @Test
    void refusesAnEntryThatWouldClimbOutOfTheDirectory(@TempDir Path dir) throws IOException {
        final Path archive = archive(dir, Map.of("../escaped.mod", TUNE));
        final Path into = Files.createDirectory(dir.resolve("into"));

        assertThrows(IOException.class, () -> extractor.extract(archive, into, name -> true));
        assertFalse(Files.exists(dir.resolve("escaped.mod")));
    }

    @Test
    void isTheOneDetectedForARarFile(@TempDir Path dir) throws IOException {
        assertInstanceOf(RarExtractor.class, Archives.detect(archive(dir, Map.of("tune.mod", TUNE))).orElseThrow());
        assertTrue(Archives.looksLikeArchive("bundle.rar"));
    }

    /**
     * RAR 5 is a different format that junrar does not read, so it is left undetected rather than opened and
     * failed halfway through.
     */
    @Test
    void leavesARarFiveArchiveAlone(@TempDir Path dir) throws IOException {
        final Path five = Files.write(dir.resolve("five.rar"), RAR5_SIGNATURE);

        assertFalse(extractor.matches(Files.readAllBytes(five)));
    }

    private static Path archive(Path dir, Map<String, byte[]> entries) throws IOException {
        return Files.write(dir.resolve("bundle.rar"), TestArchives.rar(entries));
    }
}

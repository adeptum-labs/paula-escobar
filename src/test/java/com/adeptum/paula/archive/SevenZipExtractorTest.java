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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SevenZipExtractorTest {

    private static final byte[] TUNE = "a module, of a sort".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NOTES = "read me".getBytes(StandardCharsets.US_ASCII);

    private final SevenZipExtractor extractor = new SevenZipExtractor();

    @Test
    void unpacksTheEntriesThatAreWanted(@TempDir Path dir) throws IOException {
        final Path archive = archive(dir, Map.of("tune.mod", TUNE));
        final Path into = Files.createDirectory(dir.resolve("into"));

        extractor.extract(archive, into, name -> true);

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
        final Path archive = archive(dir, Map.of("music/tune.mod", TUNE));
        final Path into = Files.createDirectory(dir.resolve("into"));

        extractor.extract(archive, into, name -> true);

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
    void isTheOneDetectedForA7zFile(@TempDir Path dir) throws IOException {
        assertInstanceOf(SevenZipExtractor.class, Archives.detect(archive(dir, Map.of("tune.mod", TUNE))).orElseThrow());
        assertTrue(Archives.looksLikeArchive("bundle.7z"));
    }

    /**
     * An archive 7-Zip itself wrote, with its table of contents packed the way the tool does by default, which
     * the library's own writer leaves plain.
     */
    @Test
    void readsAnArchiveTheToolItselfWrote(@TempDir Path dir) throws IOException {
        final Path archive;
        try (InputStream in = SevenZipExtractorTest.class.getResourceAsStream("/7z/p7zip.7z")) {
            archive = Files.write(dir.resolve("p7zip.7z"), in.readAllBytes());
        }
        final Path into = Files.createDirectory(dir.resolve("into"));

        assertInstanceOf(SevenZipExtractor.class, Archives.detect(archive).orElseThrow());
        extractor.extract(archive, into, name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(into.resolve("music").resolve("tune.mod")));
        assertArrayEquals(NOTES, Files.readAllBytes(into.resolve("notes.txt")));
    }

    private static Path archive(Path dir, Map<String, byte[]> entries) throws IOException {
        return Files.write(dir.resolve("bundle.7z"), TestArchives.sevenZip(entries));
    }
}

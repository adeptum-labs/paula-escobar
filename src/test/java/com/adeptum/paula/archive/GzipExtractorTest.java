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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GzipExtractorTest {

    private static final byte[] README = "hello".getBytes(StandardCharsets.US_ASCII);

    private final GzipExtractor extractor = new GzipExtractor();

    private static byte[] gzip(byte[] content) throws IOException {
        final ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(packed)) {
            out.write(content);
        }
        return packed.toByteArray();
    }

    @Test
    void unwrapsToTheArchiveNameWithoutTheSuffix(@TempDir Path dir) throws IOException {
        final Path archive = Files.write(dir.resolve("XM.survival.gz"), gzip(TestModules.proTracker()));
        final Path into = dir.resolve("out");

        extractor.extract(archive, into, name -> true);

        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(into.resolve("XM.survival")));
    }

    @Test
    void leavesTheOneFileAloneWhenItIsNotWanted(@TempDir Path dir) throws IOException {
        final Path archive = Files.write(dir.resolve("notes.txt.gz"), gzip(README));
        final Path into = dir.resolve("out");

        extractor.extract(archive, into, name -> false);

        assertFalse(Files.exists(into.resolve("notes.txt")));
    }

    @Test
    void isFoundByItsMagicAndWrapsOneFile(@TempDir Path dir) throws IOException {
        final Path archive = Files.write(dir.resolve("tune.gz"), gzip(README));

        assertInstanceOf(GzipExtractor.class, Archives.detect(archive).orElseThrow());
        assertTrue(extractor.wrapsSingleFile());
        assertFalse(extractor.matches(README));
    }
}

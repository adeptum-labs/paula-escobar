/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestArchives;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchivesTest {

    private static final byte[] CONTENT = "hello".getBytes(StandardCharsets.US_ASCII);

    @Test
    void detectsFormatsByMagicBytes(@TempDir Path dir) throws IOException {
        assertInstanceOf(ZipExtractor.class, Archives.detect(Files.write(dir.resolve("a"), TestArchives.zip(Map.of("x", CONTENT)))).orElseThrow());
        assertInstanceOf(LhaExtractor.class, Archives.detect(Files.write(dir.resolve("b"), TestArchives.lha(Map.of("x", CONTENT), "-lh5-"))).orElseThrow());
        assertTrue(Archives.detect(Files.write(dir.resolve("c"), CONTENT)).isEmpty());
        assertTrue(Archives.detect(Files.write(dir.resolve("d"), new byte[0])).isEmpty());
    }

    @Test
    void targetsStayInsideTheDirectory(@TempDir Path dir) throws IOException {
        assertEquals(dir.resolve("dir/file.mod"), Archives.target(dir, "dir\\file.mod"));
        assertTrue(Files.isDirectory(dir.resolve("dir")));
        assertThrows(IOException.class, () -> Archives.target(dir, "a/../../x"));
    }
}

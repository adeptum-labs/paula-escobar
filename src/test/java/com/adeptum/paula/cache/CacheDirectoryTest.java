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

package com.adeptum.paula.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheDirectoryTest {

    private static final Path HOME = Path.of("/home/nobody");

    @Test
    void usesXdgCacheHomeWhenAbsolute(@TempDir Path dir) {
        final CacheDirectory cache = CacheDirectory.resolve(Map.of("XDG_CACHE_HOME", dir.toString()), HOME);
        assertEquals(dir.resolve("paula"), cache.root());
    }

    @Test
    void ignoresRelativeXdgCacheHome() {
        final CacheDirectory cache = CacheDirectory.resolve(Map.of("XDG_CACHE_HOME", "relative"), HOME);
        assertEquals(HOME.resolve(".cache/paula"), cache.root());
    }

    @Test
    void fallsBackToDotCache() {
        assertEquals(HOME.resolve(".cache/paula"), CacheDirectory.resolve(Map.of(), HOME).root());
    }

    @Test
    void createsParentDirectoriesForFiles(@TempDir Path dir) throws IOException {
        final Path file = new CacheDirectory(dir).file("api", "parties", "1.json");
        assertEquals(dir.resolve("api/parties/1.json"), file);
        assertTrue(Files.isDirectory(file.getParent()));
    }

    @Test
    void createsDirectories(@TempDir Path dir) throws IOException {
        assertTrue(Files.isDirectory(new CacheDirectory(dir).directory("files", "7")));
    }

    @Test
    void writesAtomicallyWithoutLeavingPartFiles(@TempDir Path dir) throws IOException {
        final CacheDirectory cache = new CacheDirectory(dir);
        final Path file = cache.file("x.json");
        cache.writeAtomically(file, "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals("{}", Files.readString(file));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(file), files.toList());
        }
    }
}

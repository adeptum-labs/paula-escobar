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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadCacheTest {

    private static final URI BUNDLE = URI.create("https://archive.scene.org/pub/parties/1995/assembly95/music.zip");
    private static final URI OTHER = URI.create("https://archive.scene.org/pub/parties/1995/assembly95/gfx.zip");

    @Test
    void keepsOneDirectoryPerAddress(@TempDir Path dir) {
        final DownloadCache downloads = new DownloadCache(new CacheDirectory(dir));

        assertEquals(downloads.directory(BUNDLE), downloads.directory(BUNDLE));
        assertNotEquals(downloads.directory(BUNDLE), downloads.directory(OTHER));
    }

    @Test
    void knowsNothingOfAProductionUntilItsDownloadIsInPlace(@TempDir Path dir) throws IOException {
        final DownloadCache downloads = new DownloadCache(new CacheDirectory(dir));
        final Path directory = downloads.directory(BUNDLE);

        assertEquals(Optional.empty(), downloads.of(7));
        downloads.remember(7, directory);
        assertEquals(Optional.empty(), downloads.of(7), "remembered, but nothing has landed there");

        downloads.commit(staged(downloads), directory);
        assertEquals(Optional.of(directory), downloads.of(7));
    }

    @Test
    void throwsAwayTheDownloadThatMovedInSecond(@TempDir Path dir) throws IOException {
        final DownloadCache downloads = new DownloadCache(new CacheDirectory(dir));
        final Path directory = downloads.directory(BUNDLE);
        final Path first = staged(downloads);
        final Path second = staged(downloads);

        downloads.commit(first, directory);
        downloads.commit(second, directory);

        assertTrue(Files.isRegularFile(directory.resolve("extracted/tune.mod")));
        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
    }

    private static Path staged(DownloadCache downloads) throws IOException {
        final Path staging = downloads.staging();
        Files.createDirectories(staging.resolve("extracted"));
        Files.writeString(staging.resolve("extracted/tune.mod"), "tune");
        return staging;
    }
}

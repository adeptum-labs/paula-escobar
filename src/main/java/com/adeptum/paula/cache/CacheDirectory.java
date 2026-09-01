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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Paula's cache root following the XDG base directory convention.
 */
public final class CacheDirectory {

    private static final String XDG_CACHE_HOME = "XDG_CACHE_HOME";
    private static final String DOT_CACHE = ".cache";
    private static final String APPLICATION = "paula";
    private static final String PART_SUFFIX = ".part";

    private final Path root;

    public CacheDirectory(Path root) {
        this.root = root;
    }

    public static CacheDirectory resolve() {
        return resolve(System.getenv(), Path.of(System.getProperty("user.home")));
    }

    static CacheDirectory resolve(Map<String, String> environment, Path home) {
        final String xdg = environment.get(XDG_CACHE_HOME);
        final Path base = xdg != null && Path.of(xdg).isAbsolute() ? Path.of(xdg) : home.resolve(DOT_CACHE);
        return new CacheDirectory(base.resolve(APPLICATION));
    }

    public Path root() {
        return root;
    }

    public Path file(String... segments) throws IOException {
        final Path file = resolve(segments);
        Files.createDirectories(file.getParent());
        return file;
    }

    public Path directory(String... segments) throws IOException {
        return Files.createDirectories(resolve(segments));
    }

    /**
     * Writes next to the target and moves into place so an interrupted write can never be mistaken for a cached file.
     */
    public void writeAtomically(Path file, byte[] content) throws IOException {
        final Path part = Files.createTempFile(file.getParent(), file.getFileName().toString(), PART_SUFFIX);
        Files.write(part, content);
        Files.move(part, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path resolve(String... segments) {
        Path path = root;
        for (final String segment : segments) {
            path = path.resolve(segment);
        }
        return path;
    }
}

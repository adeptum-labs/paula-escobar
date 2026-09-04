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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/**
 * Unwraps a gzipped file, which is how the Amiga Music Preservation archive serves every module it holds.
 * Gzip carries one file and no name for it, so the unpacked content keeps the archive's own name without the
 * suffix.
 */
public final class GzipExtractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {0x1f, (byte) 0x8b};
    private static final String SUFFIX = ".gz";

    @Override
    public boolean matches(byte[] head) {
        return Archives.startsWith(head, MAGIC);
    }

    @Override
    public boolean wrapsSingleFile() {
        return true;
    }

    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        final String name = unwrapped(archive.getFileName().toString());
        if (!wanted.test(name)) {
            return;
        }
        final byte[] unpacked;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(archive))) {
            unpacked = in.readAllBytes();
        }
        Files.write(Archives.target(into, name), unpacked);
    }

    private static String unwrapped(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(SUFFIX) ? name.substring(0, name.length() - SUFFIX.length()) : name;
    }
}

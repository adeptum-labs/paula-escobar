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

package com.adeptum.paula.archive.xpk;

import com.adeptum.paula.archive.ArchiveExtractor;
import com.adeptum.paula.archive.Archives;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Unwraps a file packed with the Amiga's XPK framework. XPK wraps a single file, so the unpacked content keeps the
 * archive's own name.
 */
public final class XpkExtractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {'X', 'P', 'K', 'F'};

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
        final String name = archive.getFileName().toString();
        if (!wanted.test(name)) {
            return;
        }
        final byte[] unpacked;
        try {
            unpacked = XpkContainer.unpack(Files.readAllBytes(archive));
        } catch (RuntimeException e) {
            throw new IOException("Corrupt XPK file " + name + ": " + e, e);
        }
        Files.write(Archives.target(into, name), unpacked);
    }
}

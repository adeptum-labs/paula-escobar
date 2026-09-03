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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

/**
 * Unpacks 7z archives. The format keeps its own table of contents at the end of the file rather than a header
 * before every entry, so the whole archive is opened at once instead of read as a stream.
 */
public final class SevenZipExtractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};

    @Override
    public boolean matches(byte[] head) {
        return Archives.startsWith(head, MAGIC);
    }

    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        try (SevenZFile sevenZip = SevenZFile.builder().setPath(archive).get()) {
            for (SevenZArchiveEntry entry = sevenZip.getNextEntry(); entry != null; entry = sevenZip.getNextEntry()) {
                final String name = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                if (!entry.isDirectory() && !name.isEmpty() && wanted.test(name)) {
                    write(sevenZip, entry, Archives.target(into, name));
                }
            }
        }
    }

    private static void write(SevenZFile sevenZip, SevenZArchiveEntry entry, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            final byte[] buffer = new byte[8192];
            for (int read = sevenZip.read(buffer); read > 0; read = sevenZip.read(buffer)) {
                out.write(buffer, 0, read);
            }
        }
    }
}

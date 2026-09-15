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

import com.adeptum.paula.text.CodePage437;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads zip archives. A name flagged as UTF-8 is read as that; one without the flag is read as code page 437,
 * the way the zippers of the DOS days wrote them and the way other readers take them.
 * <p>
 * The archive is read through its central directory rather than streamed, since only the stream checks each
 * entry against its checksum, and a party archive now and then holds a module whose checksum no longer
 * matches while it still plays whole; streamed, that one entry refused the entire archive.
 */
public final class ZipExtractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {'P', 'K', 3, 4};

    @Override
    public boolean matches(byte[] head) {
        return Archives.startsWith(head, MAGIC);
    }

    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile(), CodePage437.CHARSET)) {
            for (final ZipEntry entry : Collections.list(zip.entries())) {
                final String name = entry.getName().replace('\\', '/');
                if (!entry.isDirectory() && wanted.test(name)) {
                    try (InputStream content = zip.getInputStream(entry)) {
                        Files.copy(content, Archives.target(into, name), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }
}

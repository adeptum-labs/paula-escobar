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

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Unpacks RAR archives through junrar, which reads the format up to RAR 4; the RAR 5 signature is a byte
 * longer and is left undetected rather than opened and failed.
 */
@Slf4j
public final class RarExtractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {'R', 'a', 'r', '!', 0x1A, 0x07, 0x00};

    @Override
    public boolean matches(byte[] head) {
        return Archives.startsWith(head, MAGIC);
    }

    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        try (Archive rar = new Archive(archive.toFile())) {
            final boolean solid = rar.getFileHeaders().stream().anyMatch(FileHeader::isSolid);
            for (final FileHeader header : rar.getFileHeaders()) {
                unpack(rar, header, into, wanted, solid);
            }
        } catch (RarException e) {
            throw new IOException("Cannot read " + archive.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * A solid entry is packed against the ones before it, so in an archive holding any, every entry is unpacked
     * in turn and the ones nobody asked for are thrown away rather than stepped over.
     */
    private static void unpack(Archive rar, FileHeader header, Path into, Predicate<String> wanted, boolean solid)
            throws IOException, RarException {
        final String name = header.getFileName().replace('\\', '/');
        if (header.isDirectory() || name.isEmpty()) {
            return;
        }
        if (header.isEncrypted()) {
            log.info("Leaving the encrypted entry {} in the archive", name);
            return;
        }
        final boolean keep = wanted.test(name);
        if (!keep && !solid) {
            return;
        }
        try (OutputStream out = keep ? Files.newOutputStream(Archives.target(into, name)) : OutputStream.nullOutputStream()) {
            rar.extractFile(header, out);
        }
    }
}

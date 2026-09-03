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

import com.adeptum.paula.archive.lzx.LzxExtractor;
import com.adeptum.paula.archive.xpk.XpkExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Detects archives by their magic bytes, never by file name, and keeps extracted paths inside the target directory.
 */
public final class Archives {

    private static final int HEAD_LENGTH = 16;
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip", "lha", "lzh", "lzx", "d64", "7z", "rar");
    private static final List<ArchiveExtractor> EXTRACTORS =
            List.of(new ZipExtractor(), new LhaExtractor(), new LzxExtractor(), new XpkExtractor(),
                    new SevenZipExtractor(), new RarExtractor(), new D64Extractor());

    private Archives() {
    }

    public static Optional<ArchiveExtractor> detect(Path file) throws IOException {
        final byte[] head = head(file);
        final long size = Files.size(file);
        return EXTRACTORS.stream().filter(extractor -> extractor.matches(head, size)).findFirst();
    }

    /**
     * Names an archive is likely to carry; used where only the name of a file inside an archive is known and
     * its contents cannot be looked at yet.
     */
    public static boolean looksLikeArchive(String name) {
        final int dot = name.lastIndexOf('.');
        return dot >= 0 && ARCHIVE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static Path target(Path into, String entryName) throws IOException {
        final Path target = into.resolve(entryName.replace('\\', '/')).normalize();
        if (!target.startsWith(into)) {
            throw new IOException("Refusing archive entry " + entryName);
        }
        Files.createDirectories(target.getParent());
        return target;
    }

    public static boolean startsWith(byte[] head, byte[] magic) {
        return head.length >= magic.length && Arrays.equals(head, 0, magic.length, magic, 0, magic.length);
    }

    private static byte[] head(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(HEAD_LENGTH);
        }
    }
}

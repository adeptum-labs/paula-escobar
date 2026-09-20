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

package com.adeptum.paula.archive.adf;

import com.adeptum.paula.archive.ArchiveExtractor;
import com.adeptum.paula.archive.Archives;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads the files off an Amiga floppy image. An ADF has no magic bytes of its own, so it is known by its exact
 * size together with the boot block's "DOS" mark, which also keeps this from mistaking an extended ADF's raw
 * MFM tracks, or a same-sized module, for a filesystem.
 */
public final class AdfExtractor implements ArchiveExtractor {

    private static final byte[] DOS_MAGIC = {'D', 'O', 'S'};
    private static final Set<Long> SIZES = Set.of(901120L, 1802240L);

    /**
     * A disk image carries no magic bytes on its own; the boot block is checked together with the size instead.
     */
    @Override
    public boolean matches(byte[] head) {
        return false;
    }

    @Override
    public boolean matches(byte[] head, long size) {
        return SIZES.contains(size) && Archives.startsWith(head, DOS_MAGIC);
    }

    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        try {
            final AdfVolume volume = new AdfVolume(Files.readAllBytes(archive));
            for (final AdfFile file : volume.list()) {
                if (wanted.test(file.path())) {
                    Files.write(Archives.target(into, file.path()), volume.read(file));
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Corrupt ADF image " + archive.getFileName() + ": " + e, e);
        }
    }
}

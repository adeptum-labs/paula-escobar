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

package com.adeptum.paula.favourites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataDirectoryTest {

    private static final Path HOME = Path.of("/home/nobody");

    @Test
    void usesXdgDataHomeWhenAbsolute(@TempDir Path dir) {
        final DataDirectory data = DataDirectory.resolve(Map.of("XDG_DATA_HOME", dir.toString()), HOME);
        assertEquals(dir.resolve("paula"), data.root());
    }

    @Test
    void ignoresRelativeXdgDataHome() {
        final DataDirectory data = DataDirectory.resolve(Map.of("XDG_DATA_HOME", "relative"), HOME);
        assertEquals(HOME.resolve(".local/share/paula"), data.root());
    }

    @Test
    void fallsBackToDotLocalShare() {
        assertEquals(HOME.resolve(".local/share/paula"), DataDirectory.resolve(Map.of(), HOME).root());
    }

    @Test
    void createsParentDirectoriesForFiles(@TempDir Path dir) throws IOException {
        final Path file = new DataDirectory(dir).file("favourites.json");
        assertEquals(dir.resolve("favourites.json"), file);
        assertTrue(Files.isDirectory(file.getParent()));
    }

    @Test
    void writesAtomicallyWithoutLeavingPartFiles(@TempDir Path dir) throws IOException {
        final DataDirectory data = new DataDirectory(dir);
        final Path file = data.file("x.json");
        data.writeAtomically(file, "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals("{}", Files.readString(file));
    }
}

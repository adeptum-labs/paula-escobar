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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestArchives;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PowerPackerExtractorTest {

    /**
     * The three bytes before the last are the same, which is what lets the shortest back-reference stand in
     * for two of them.
     */
    private static final byte[] REPEATING = "paulaxxxy".getBytes(StandardCharsets.US_ASCII);

    private static final String CRUNCHED = "Mod.tune.PP";
    private static final int EFFICIENCY_AT = 4;

    private final PowerPackerExtractor extractor = new PowerPackerExtractor();

    private static Path crunched(Path dir, byte[] file) throws IOException {
        return Files.write(dir.resolve(CRUNCHED), file);
    }

    @Test
    void decrunchesALiteralOnlyStream(@TempDir Path dir) throws IOException {
        final byte[] module = TestModules.proTracker();

        extractor.extract(crunched(dir, TestArchives.powerPacker(module)), dir.resolve("out"), name -> true);

        assertArrayEquals(module, Files.readAllBytes(dir.resolve("out/Mod.tune")));
    }

    @Test
    void decrunchesABackReference(@TempDir Path dir) throws IOException {
        final Path archive = crunched(dir, TestArchives.powerPackerWithMatch(REPEATING));

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(REPEATING, Files.readAllBytes(dir.resolve("out/Mod.tune")));
    }

    @Test
    void unwrapsToTheArchiveNameWithoutTheSuffix() {
        assertEquals("Mod.tune", extractor.unwrappedName(CRUNCHED));
        assertEquals("mod.tune", extractor.unwrappedName("mod.tune.pp"));
        assertEquals("MOD.jazz", extractor.unwrappedName("MOD.jazz"), "most kept the name they were tracked as");
        assertTrue(extractor.wrapsSingleFile());
    }

    @Test
    void refusesAnUnknownEfficiencySet(@TempDir Path dir) throws IOException {
        final byte[] file = TestArchives.powerPacker(REPEATING);
        file[EFFICIENCY_AT] = 0x0F;

        assertEquals(Optional.empty(), Archives.detect(crunched(dir, file)));
        assertFalse(extractor.matches(file));
    }

    @Test
    void refusesAStreamThatRunsPastItsStart(@TempDir Path dir) throws IOException {
        final byte[] file = TestArchives.powerPacker(REPEATING);
        file[file.length - 1] = 0;
        file[file.length - 2] = (byte) 0xFF;
        final Path archive = crunched(dir, file);

        assertThrows(IOException.class, () -> extractor.extract(archive, dir.resolve("out"), name -> true));
    }

    @Test
    void isFoundByItsIdentifier(@TempDir Path dir) throws IOException {
        final Path archive = crunched(dir, TestArchives.powerPacker(REPEATING));

        assertInstanceOf(PowerPackerExtractor.class, Archives.detect(archive).orElseThrow());
    }
}

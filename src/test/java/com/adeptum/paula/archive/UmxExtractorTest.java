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
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmxExtractorTest {

    private static final String PACKAGE = "botpack.umx";
    private static final int IMPULSE_TRACKER_LENGTH = 256;

    private final UmxExtractor extractor = new UmxExtractor();

    private static Path packaged(Path dir, byte[] module) throws IOException {
        return Files.write(dir.resolve(PACKAGE), TestArchives.umx(module));
    }

    @Test
    void unwrapsTheModuleInsideThePackage(@TempDir Path dir) throws IOException {
        final byte[] module = TestModules.proTracker();

        extractor.extract(packaged(dir, module), dir.resolve("out"), name -> true);

        assertArrayEquals(module, Files.readAllBytes(dir.resolve("out/botpack.mod")));
    }

    @Test
    void namesTheEntryAfterTheFormatItFinds(@TempDir Path dir) throws IOException {
        final byte[] module = new byte[IMPULSE_TRACKER_LENGTH];
        System.arraycopy("IMPM".getBytes(StandardCharsets.US_ASCII), 0, module, 0, 4);

        extractor.extract(packaged(dir, module), dir.resolve("out"), name -> true);

        assertTrue(Files.exists(dir.resolve("out/botpack.it")), "the tune says which of the four it is");
    }

    @Test
    void leavesWhatFollowsTheModuleOnTheEnd(@TempDir Path dir) throws IOException {
        final byte[] module = TestModules.proTracker();
        final byte[] file = Arrays.copyOf(TestArchives.umx(module), TestArchives.umx(module).length + 16);

        extractor.extract(Files.write(dir.resolve(PACKAGE), file), dir.resolve("out"), name -> true);

        final byte[] written = Files.readAllBytes(dir.resolve("out/botpack.mod"));
        assertArrayEquals(module, Arrays.copyOf(written, module.length), "the tune is whole ahead of it");
    }

    @Test
    void refusesAPackageThatHoldsNoModule(@TempDir Path dir) throws IOException {
        final Path archive = packaged(dir, new byte[IMPULSE_TRACKER_LENGTH]);

        assertThrows(IOException.class, () -> extractor.extract(archive, dir.resolve("out"), name -> true));
    }

    @Test
    void isFoundByTheUnrealTag(@TempDir Path dir) throws IOException {
        final Path archive = packaged(dir, TestModules.proTracker());

        assertInstanceOf(UmxExtractor.class, Archives.detect(archive).orElseThrow());
        assertFalse(extractor.wrapsSingleFile(), "the name does not say which format is inside");
    }
}

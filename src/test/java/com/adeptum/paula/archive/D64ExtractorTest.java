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

package com.adeptum.paula.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestArchives;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class D64ExtractorTest {

    private static final byte[] TUNE = "sys2061".getBytes(StandardCharsets.US_ASCII);
    private static final int IMAGE_LENGTH = 174848;

    private final D64Extractor extractor = new D64Extractor();

    private static Path image(Path dir, Map<String, byte[]> programs) throws IOException {
        return Files.write(dir.resolve("compo.d64"), TestArchives.d64(programs));
    }

    @Test
    void writesOutTheProgramsTheDirectoryNames(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> programs = new LinkedHashMap<>();
        programs.put("FANTA /BASS", TUNE);
        programs.put("BIO /BASS", "sys4096".getBytes(StandardCharsets.US_ASCII));
        final Path archive = image(dir, programs);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(dir.resolve("out/FANTA -BASS.prg")));
        assertArrayEquals("sys4096".getBytes(StandardCharsets.US_ASCII), Files.readAllBytes(dir.resolve("out/BIO -BASS.prg")));
    }

    @Test
    void followsTheBlockChainOfLongerPrograms(@TempDir Path dir) throws IOException {
        final byte[] program = new byte[1500];
        new Random(11).nextBytes(program);
        final Path archive = image(dir, Map.of("LONG", program));

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(program, Files.readAllBytes(dir.resolve("out/LONG.prg")));
    }

    @Test
    void leavesOutWhatTheCallerDoesNotWant(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> programs = new LinkedHashMap<>();
        programs.put("WANTED", TUNE);
        programs.put("UNWANTED", TUNE);
        final Path archive = image(dir, programs);

        extractor.extract(archive, dir.resolve("out"), name -> name.startsWith("WANTED"));

        assertTrue(Files.exists(dir.resolve("out/WANTED.prg")));
        assertFalse(Files.exists(dir.resolve("out/UNWANTED.prg")));
    }

    @Test
    void isKnownByTheSizeOfTheDiskRatherThanItsFirstBytes(@TempDir Path dir) throws IOException {
        final Path archive = image(dir, Map.of("TUNE", TUNE));

        assertFalse(extractor.matches(new byte[16]), "a disk image has no magic bytes");
        assertTrue(extractor.matches(new byte[16], IMAGE_LENGTH));
        assertFalse(extractor.matches(new byte[16], IMAGE_LENGTH - 1));
        assertEquals(Optional.of(D64Extractor.class), Archives.detect(archive).map(Object::getClass));
    }

    @Test
    void haltsOnADiskWhoseChainsPointAtThemselves(@TempDir Path dir) throws IOException {
        final byte[] broken = TestArchives.d64(Map.of("LOOP", TUNE));
        Arrays.fill(broken, 17 * 21 * 256, 17 * 21 * 256 + 2, (byte) 17);
        final Path archive = Files.write(dir.resolve("broken.d64"), broken);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertTrue(Files.exists(dir.resolve("out/LOOP.prg")));
    }
}

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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestArchives;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class T64ExtractorTest {

    private static final byte[] TUNE = {0x01, 0x08, 's', 'y', 's', '2', '0', '6', '1'};
    private static final byte[] OTHER = {0x00, 0x10, 's', 'y', 's', '4', '0', '9', '6'};
    private static final int BOGUS_END = 0xC3C6;
    private static final int END_AT = 0x44;

    private final T64Extractor extractor = new T64Extractor();

    private static Path image(Path dir, Map<String, byte[]> programs) throws IOException {
        return Files.write(dir.resolve("compo.t64"), TestArchives.t64(programs));
    }

    @Test
    void writesOutTheProgramsTheDirectoryNames(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> programs = new LinkedHashMap<>();
        programs.put("FANTA /BASS", TUNE);
        programs.put("BIO", OTHER);

        extractor.extract(image(dir, programs), dir.resolve("out"), name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(dir.resolve("out/FANTA -BASS.prg")));
        assertArrayEquals(OTHER, Files.readAllBytes(dir.resolve("out/BIO.prg")));
    }

    @Test
    void putsTheLoadAddressBackAtTheHeadOfTheProgram(@TempDir Path dir) throws IOException {
        extractor.extract(image(dir, Map.of("TUNE", TUNE)), dir.resolve("out"), name -> true);

        final byte[] program = Files.readAllBytes(dir.resolve("out/TUNE.prg"));
        assertEquals(0x01, program[0], "a tape keeps the load address in the directory");
        assertEquals(0x08, program[1]);
    }

    @Test
    void cutsAnEntryWhoseEndAddressRunsPastTheImage(@TempDir Path dir) throws IOException {
        final byte[] image = TestArchives.t64(Map.of("TUNE", TUNE));
        image[END_AT] = (byte) BOGUS_END;
        image[END_AT + 1] = (byte) (BOGUS_END >> 8);

        extractor.extract(Files.write(dir.resolve("compo.t64"), image), dir.resolve("out"), name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(dir.resolve("out/TUNE.prg")));
    }

    @Test
    void tellsApartTwoEntriesOfTheSameName(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> programs = new LinkedHashMap<>();
        programs.put("TUNE", TUNE);
        programs.put("TUNE ", OTHER);

        extractor.extract(image(dir, programs), dir.resolve("out"), name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(dir.resolve("out/TUNE.prg")));
        assertArrayEquals(OTHER, Files.readAllBytes(dir.resolve("out/TUNE-2.prg")));
    }

    @Test
    void leavesOutWhatTheCallerDoesNotWant(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> programs = new LinkedHashMap<>();
        programs.put("TUNE", TUNE);
        programs.put("BIO", OTHER);

        extractor.extract(image(dir, programs), dir.resolve("out"), "TUNE.prg"::equals);

        assertTrue(Files.exists(dir.resolve("out/TUNE.prg")));
        assertFalse(Files.exists(dir.resolve("out/BIO.prg")));
    }

    @Test
    void isKnownByItsDescriptor(@TempDir Path dir) throws IOException {
        assertInstanceOf(T64Extractor.class, Archives.detect(image(dir, Map.of("TUNE", TUNE))).orElseThrow());
        assertFalse(extractor.matches("C64 program file".getBytes(StandardCharsets.US_ASCII)),
                "the machine alone does not make it a tape");
        assertFalse(extractor.wrapsSingleFile(), "a tape holds a directory, not one file");
    }
}

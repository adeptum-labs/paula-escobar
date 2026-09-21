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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.archive.Archives;
import com.adeptum.paula.testing.TestArchives;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdfExtractorTest {

    private static final int BLOCK = 512;
    private static final byte[] TUNE = "sys2061".getBytes(StandardCharsets.US_ASCII);
    private static final int DD_LENGTH = 901120;
    private static final int HD_LENGTH = 1802240;

    private final AdfExtractor extractor = new AdfExtractor();

    private static Path image(Path dir, Map<String, byte[]> files, boolean fastFileSystem) throws IOException {
        return Files.write(dir.resolve("disk.adf"), TestArchives.adf(files, fastFileSystem));
    }

    @Test
    void isKnownByTheSizeAndTheDosMarkRatherThanMagicBytes(@TempDir Path dir) throws IOException {
        final Path archive = image(dir, Map.of("tune.mod", TUNE), false);

        assertFalse(extractor.matches(new byte[16]), "a disk image has no magic bytes");
        assertTrue(extractor.matches(new byte[] {'D', 'O', 'S'}, DD_LENGTH));
        assertTrue(extractor.matches(new byte[] {'D', 'O', 'S'}, HD_LENGTH));
        assertFalse(extractor.matches(new byte[16], DD_LENGTH), "the right size alone is not enough");
        assertFalse(extractor.matches(new byte[] {'D', 'O', 'S'}, DD_LENGTH - 1));
        assertEquals(Optional.of(AdfExtractor.class), Archives.detect(archive).map(Object::getClass));
    }

    @Test
    void writesOutTheFilesTheDiskNames(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("tune.mod", TUNE);
        files.put("readme.txt", "read me".getBytes(StandardCharsets.US_ASCII));
        final Path archive = image(dir, files, false);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(dir.resolve("out/tune.mod")));
        assertArrayEquals("read me".getBytes(StandardCharsets.US_ASCII), Files.readAllBytes(dir.resolve("out/readme.txt")));
    }

    @Test
    void keepsDirectories(@TempDir Path dir) throws IOException {
        final Path archive = image(dir, Map.of("mods/deep/tune.mod", TUNE), false);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(dir.resolve("out/mods/deep/tune.mod")));
    }

    @Test
    void readsAFileWhoseBlocksRunPastTheHeader(@TempDir Path dir) throws IOException {
        final byte[] program = new byte[40000];
        new Random(11).nextBytes(program);
        final Path archive = image(dir, Map.of("long.mod", program), false);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(program, Files.readAllBytes(dir.resolve("out/long.mod")));
    }

    @Test
    void readsTheSameDiskWrittenTheFastWay(@TempDir Path dir) throws IOException {
        final byte[] program = new byte[3000];
        new Random(7).nextBytes(program);
        final Path archive = image(dir, Map.of("tune.mod", program), true);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(program, Files.readAllBytes(dir.resolve("out/tune.mod")));
    }

    @Test
    void leavesOutWhatTheCallerDoesNotWant(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("wanted.mod", TUNE);
        files.put("unwanted.mod", TUNE);
        final Path archive = image(dir, files, false);

        extractor.extract(archive, dir.resolve("out"), name -> name.startsWith("wanted"));

        assertTrue(Files.exists(dir.resolve("out/wanted.mod")));
        assertFalse(Files.exists(dir.resolve("out/unwanted.mod")));
    }

    @Test
    void haltsOnADirectoryThatListsItselfAsAnEntry(@TempDir Path dir) throws IOException {
        final byte[] image = TestArchives.adf(Map.of("sub/tune.mod", TUNE), false);
        final int root = image.length / BLOCK / 2;
        final int sub = firstHashEntry(image, root);
        writeInt(image, sub * BLOCK + emptyHashSlot(image, sub), sub);
        final Path archive = Files.write(dir.resolve("loop.adf"), image);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(TUNE, Files.readAllBytes(dir.resolve("out/sub/tune.mod")));
    }

    @Test
    void passesOverAnEntryPointingOutsideTheImage(@TempDir Path dir) throws IOException {
        final byte[] image = TestArchives.adf(Map.of("tune.mod", TUNE), false);
        final int root = image.length / BLOCK / 2;
        final int outside = image.length / BLOCK + 100;
        for (int slot = 0; slot < 72; slot++) {
            final int at = root * BLOCK + 24 + slot * 4;
            if (readInt(image, at) != 0) {
                writeInt(image, at, outside);
            }
        }
        final Path archive = Files.write(dir.resolve("dangling.adf"), image);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertFalse(Files.exists(dir.resolve("out/tune.mod")));
    }

    @Test
    void leavesAnExtendedAdfAlone(@TempDir Path dir) throws IOException {
        final byte[] extended = new byte[DD_LENGTH];
        System.arraycopy("UAE--ADF".getBytes(StandardCharsets.US_ASCII), 0, extended, 0, 8);
        final Path archive = Files.write(dir.resolve("extended.adf"), extended);

        assertFalse(extractor.matches(new byte[] {'U', 'A', 'E', '-', '-', 'A', 'D', 'F'}, DD_LENGTH));
        assertEquals(Optional.empty(), Archives.detect(archive));
    }

    private static int firstHashEntry(byte[] image, int block) {
        for (int slot = 0; slot < 72; slot++) {
            final int entry = readInt(image, block * BLOCK + 24 + slot * 4);
            if (entry != 0) {
                return entry;
            }
        }
        throw new IllegalStateException("directory has no entries");
    }

    private static int emptyHashSlot(byte[] image, int block) {
        for (int slot = 0; slot < 72; slot++) {
            final int at = 24 + slot * 4;
            if (readInt(image, block * BLOCK + at) == 0) {
                return at;
            }
        }
        throw new IllegalStateException("directory has no empty hash slot");
    }

    private static int readInt(byte[] image, int at) {
        return (image[at] & 0xFF) << 24 | (image[at + 1] & 0xFF) << 16
                | (image[at + 2] & 0xFF) << 8 | image[at + 3] & 0xFF;
    }

    private static void writeInt(byte[] image, int at, int value) {
        image[at] = (byte) (value >>> 24);
        image[at + 1] = (byte) (value >>> 16);
        image[at + 2] = (byte) (value >>> 8);
        image[at + 3] = (byte) value;
    }
}

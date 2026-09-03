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

package com.adeptum.paula.archive.xpk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.archive.Archives;
import com.adeptum.paula.testing.TestArchives;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The DUKE fixture is the first chunk of an Assembly 1992 entry, cut into its own container; the expected hash
 * comes from the ancient decompressor.
 */
class XpkExtractorTest {

    private static final String DUKE_CHUNK_SHA1 = "a975adc7cf93ae6429d1cc4c3a3a8524cc47dbfc";
    private static final int DUKE_CHUNK_LENGTH = 30000;

    private final XpkExtractor extractor = new XpkExtractor();

    @Test
    void unpacksDukeChunksIntoAFileNamedLikeTheArchive(@TempDir Path dir) throws Exception {
        final Path archive = fixture("duke.xpk", dir.resolve("mod.tune"));
        extractor.extract(archive, dir.resolve("out"), name -> name.equals("mod.tune"));

        final byte[] unpacked = Files.readAllBytes(dir.resolve("out/mod.tune"));
        assertEquals(DUKE_CHUNK_LENGTH, unpacked.length);
        assertEquals(DUKE_CHUNK_SHA1, sha1(unpacked));
    }

    @Test
    void copiesStoredChunksOfAnyPacker(@TempDir Path dir) throws IOException {
        final byte[] module = TestModules.proTracker();
        final Path archive = Files.write(dir.resolve("mod.tune"), TestArchives.xpk(module, "SQSH", 1000));
        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(module, Files.readAllBytes(dir.resolve("out/mod.tune")));
    }

    @Test
    void skipsFilesNobodyWants(@TempDir Path dir) throws IOException {
        final Path archive = Files.write(dir.resolve("mod.tune"), TestArchives.xpk(TestModules.proTracker(), "NUKE", 1000));
        extractor.extract(archive, dir.resolve("out"), name -> false);
        assertFalse(Files.exists(dir.resolve("out/mod.tune")));
    }

    @Test
    void namesUnsupportedPackers(@TempDir Path dir) throws IOException {
        final byte[] packed = TestArchives.xpk(TestModules.proTracker(), "MASH", 1000);
        packed[36] = 1;
        packed[37] ^= 1;
        final Path archive = Files.write(dir.resolve("mod.tune"), packed);
        final IOException error = assertThrows(IOException.class, () -> extractor.extract(archive, dir.resolve("out"), name -> true));
        assertTrue(error.getMessage().contains("MASH"), error.getMessage());
    }

    @Test
    void rejectsCorruptChecksums(@TempDir Path dir) throws IOException {
        final byte[] header = TestArchives.xpk(TestModules.proTracker(), "NUKE", 1000);
        header[20] ^= 0x55;
        assertThrows(IOException.class, () -> extractor.extract(Files.write(dir.resolve("a"), header), dir.resolve("out"), name -> true));

        final byte[] chunk = TestArchives.xpk(TestModules.proTracker(), "NUKE", 1000);
        chunk[36 + 8 + 3] ^= 0x55;
        assertThrows(IOException.class, () -> extractor.extract(Files.write(dir.resolve("b"), chunk), dir.resolve("out"), name -> true));
    }

    @Test
    void refusesPasswordProtectedFiles(@TempDir Path dir) throws IOException {
        final byte[] packed = TestArchives.xpk(TestModules.proTracker(), "NUKE", 1000);
        packed[32] |= 2;
        packed[33] ^= 2;
        final IOException error = assertThrows(IOException.class,
                () -> extractor.extract(Files.write(dir.resolve("a"), packed), dir.resolve("out"), name -> true));
        assertTrue(error.getMessage().contains("password"), error.getMessage());
    }

    @Test
    void archivesDetectsXpkAsASingleFileWrapper(@TempDir Path dir) throws Exception {
        assertInstanceOf(XpkExtractor.class, Archives.detect(fixture("duke.xpk", dir.resolve("x"))).orElseThrow());
        assertTrue(extractor.wrapsSingleFile());
    }

    private static Path fixture(String name, Path target) throws IOException {
        try (InputStream in = XpkExtractorTest.class.getResourceAsStream("/xpk/" + name)) {
            return Files.write(target, in.readAllBytes());
        }
    }

    private static String sha1(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
    }
}

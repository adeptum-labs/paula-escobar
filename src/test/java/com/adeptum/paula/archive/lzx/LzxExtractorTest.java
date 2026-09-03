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

package com.adeptum.paula.archive.lzx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.archive.Archives;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fixtures are trimmed releases from The Party 1996 on scene.org; the expected hashes come from unlzx.
 */
class LzxExtractorTest {

    private final LzxExtractor extractor = new LzxExtractor();

    @Test
    void extractsEveryFileOfAPlainArchive(@TempDir Path dir) throws Exception {
        final Path into = dir.resolve("out");
        extractor.extract(fixture("plain.lzx", dir), into, name -> true);

        assertEquals("c1b04204487ec2459a823320c99d3120b6da155f", sha1(into.resolve("try_this!.TxT")));
        assertEquals("3a12775e3852e59ebc859ce9f4c144d16e57332a", sha1(into.resolve("fiLE_iD.diZ")));
        assertEquals("40fc6f6d04f82aed087f4a897adee1c5fee2cbb6", sha1(into.resolve("LPDFW3432.TXT")));
        assertEquals("63b2b0e4f0a98aabe7532634ffd9a65d8cff65ce", sha1(into.resolve("!fUNtASt¡C0!.tXt")));
        assertEquals("228ee741d66591c2466b028cf26ff1cfd6463c7b", sha1(into.resolve("!cOME-øN!.nf0")));
        assertEquals("f573a618d3c54e1e0689924cc94143b286a73a2c", sha1(into.resolve("!yøUnEEdth¡S!.eXE")));
    }

    @Test
    void extractsMergedEntriesFromOneStream(@TempDir Path dir) throws Exception {
        final Path into = dir.resolve("out");
        extractor.extract(fixture("merged.lzx", dir), into, name -> true);

        assertEquals("14af0759890c2417b17852daf8649f29c606b53c", sha1(into.resolve("Sector_7/ReadMe.060")));
        assertEquals("cce5681cb812af6a7c106acd901902aa00a7173a", sha1(into.resolve("Sector_7/s7-Heat.exe.info")));
    }

    @Test
    void onlyWritesWantedEntries(@TempDir Path dir) throws Exception {
        final Path into = dir.resolve("out");
        extractor.extract(fixture("plain.lzx", dir), into, name -> name.endsWith(".diZ"));
        try (Stream<Path> files = Files.walk(into)) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void detectsCorruptData(@TempDir Path dir) throws Exception {
        final Path archive = fixture("plain.lzx", dir);
        final byte[] data = Files.readAllBytes(archive);
        data[data.length - 100] ^= 0x55;
        Files.write(archive, data);
        assertThrows(IOException.class, () -> extractor.extract(archive, dir.resolve("out"), name -> true));
    }

    /**
     * The first entry header starts at byte 10 and its CRC field sits 22 bytes into the header.
     */
    @Test
    void checksTheCrcOfWantedEntriesOnly(@TempDir Path dir) throws Exception {
        final Path archive = fixture("merged.lzx", dir);
        final byte[] data = Files.readAllBytes(archive);
        data[10 + 22] ^= 0x55;
        Files.write(archive, data);

        extractor.extract(archive, dir.resolve("out"), name -> name.endsWith(".info"));
        assertEquals("cce5681cb812af6a7c106acd901902aa00a7173a", sha1(dir.resolve("out/Sector_7/s7-Heat.exe.info")));
        assertThrows(IOException.class, () -> extractor.extract(archive, dir.resolve("all"), name -> true));
    }

    @Test
    void mergedEntriesWithoutTheirDataAreAnError(@TempDir Path dir) throws Exception {
        final Path archive = fixture("merged.lzx", dir);
        final byte[] data = Files.readAllBytes(archive);
        Files.write(archive, Arrays.copyOf(data, 10 + 31 + "Sector_7/ReadMe.060".length()));

        assertThrows(IOException.class, () -> extractor.extract(archive, dir.resolve("out"), name -> true));
    }

    @Test
    void corruptSizesFailWithAnIoException(@TempDir Path dir) throws Exception {
        final Path archive = fixture("plain.lzx", dir);
        final byte[] data = Files.readAllBytes(archive);
        Arrays.fill(data, 10 + 2, 10 + 6, (byte) 0xFF);
        Files.write(archive, data);

        assertThrows(IOException.class, () -> extractor.extract(archive, dir.resolve("out"), name -> true));
    }

    @Test
    void archivesDetectsLzx(@TempDir Path dir) throws Exception {
        assertInstanceOf(LzxExtractor.class, Archives.detect(fixture("merged.lzx", dir)).orElseThrow());
        assertTrue(extractor.matches("LZX".getBytes()));
    }

    private static Path fixture(String name, Path dir) throws IOException {
        try (InputStream in = LzxExtractorTest.class.getResourceAsStream("/lzx/" + name)) {
            return Files.write(dir.resolve(name), in.readAllBytes());
        }
    }

    private static String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file)));
    }
}

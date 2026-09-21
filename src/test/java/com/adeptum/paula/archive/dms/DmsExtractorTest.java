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

package com.adeptum.paula.archive.dms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.archive.Archives;
import com.adeptum.paula.testing.TestArchives;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The four fixtures under {@code src/test/resources/dms} are the C1 test disk from Teemu Suutari's ancient
 * (BSD 2-Clause Licence), one plain and one password-protected archive for each of HEAVY1 and HEAVY2; the
 * expected hash comes from unpacking the plain pair with xdms.
 */
class DmsExtractorTest {

    private static final byte[] TUNE = "sys2061".getBytes(StandardCharsets.US_ASCII);
    private static final int DD_LENGTH = 901120;
    private static final String EXPECTED_SHA1 = "9e8326456471f6f9189a9b18723d3f0029257412";

    private final DmsExtractor extractor = new DmsExtractor();

    private static Path image(Path dir, byte[] diskContent, byte[] fileId) throws IOException {
        return Files.write(dir.resolve("disk.dms"), TestArchives.dms(diskContent, fileId));
    }

    @Test
    void isKnownByItsMagic(@TempDir Path dir) throws IOException {
        final Path archive = image(dir, TUNE, null);

        assertFalse(extractor.matches(new byte[] {'D', 'M', 'S', 0}));
        assertTrue(extractor.matches(new byte[] {'D', 'M', 'S', '!'}));
        assertEquals(Optional.of(DmsExtractor.class), Archives.detect(archive).map(Object::getClass));
    }

    @Test
    void writesTheDiskOutAsAnImage(@TempDir Path dir) throws IOException {
        final Path archive = image(dir, TUNE, null);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        final byte[] written = Files.readAllBytes(dir.resolve("out/disk.adf"));
        assertEquals(DD_LENGTH, written.length);
        assertArrayEquals(TUNE, Arrays.copyOf(written, TUNE.length));
        assertEquals(0, written[TUNE.length], "the rest of the disk is left zero-filled");
    }

    @Test
    void writesOutTheFileIdDiz(@TempDir Path dir) throws IOException {
        final byte[] diz = "greetz to everyone".getBytes(StandardCharsets.US_ASCII);
        final Path archive = image(dir, TUNE, diz);

        extractor.extract(archive, dir.resolve("out"), name -> true);

        assertArrayEquals(diz, Files.readAllBytes(dir.resolve("out/file_id.diz")));
    }

    @Test
    void leavesOutWhatTheCallerDoesNotWant(@TempDir Path dir) throws IOException {
        final byte[] diz = "greetz".getBytes(StandardCharsets.US_ASCII);
        final Path archive = image(dir, TUNE, diz);

        extractor.extract(archive, dir.resolve("out"), name -> name.endsWith(".adf"));

        assertTrue(Files.exists(dir.resolve("out/disk.adf")));
        assertFalse(Files.exists(dir.resolve("out/file_id.diz")));
    }

    @Test
    void saysSoOnACompressionModeItDoesNotKnow(@TempDir Path dir) throws IOException {
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(new byte[] {'D', 'M', 'S', '!'});
        file.writeBytes(new byte[52]);
        TestArchives.dmsTrack(file, 0, 1, TUNE);
        final Path archive = Files.write(dir.resolve("simple.dms"), file.toByteArray());

        final IOException error = assertThrows(IOException.class,
                () -> extractor.extract(archive, dir.resolve("out"), name -> true));

        assertTrue(error.getMessage().contains("Unsupported DMS compression mode 1 for track 0"), error.getMessage());
    }

    @Test
    void unpacksTheHeavyModes(@TempDir Path dir) throws IOException, NoSuchAlgorithmException {
        for (final String fixture : new String[] {"test_C1_heavy1.dms", "test_C1_heavy2.dms",
                "test_C1_heavy1_pwd.dms", "test_C1_heavy2_pwd.dms"}) {
            final Path archive = fixture(fixture, dir);
            final Path out = dir.resolve("out-" + fixture);

            extractor.extract(archive, out, name -> true);

            final String base = fixture.substring(0, fixture.length() - ".dms".length());
            final byte[] unpacked = Files.readAllBytes(out.resolve(base + ".adf"));
            assertEquals(DD_LENGTH, unpacked.length, fixture);
            assertEquals(EXPECTED_SHA1, sha1(unpacked), fixture);
        }
    }

    private static Path fixture(String name, Path dir) throws IOException {
        try (InputStream in = DmsExtractorTest.class.getResourceAsStream("/dms/" + name)) {
            return Files.write(dir.resolve(name), in.readAllBytes());
        }
    }

    private static String sha1(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    }
}

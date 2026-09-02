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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestArchives;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LhaExtractorTest {

    private static final byte[] README = "hello".getBytes(StandardCharsets.US_ASCII);

    private final LhaExtractor extractor = new LhaExtractor();

    @ParameterizedTest
    @ValueSource(strings = {"-lh0-", "-lh1-", "-lh5-", "-lh6-", "-lh7-"})
    void extractsWantedEntriesForEveryCommonMethod(String method, @TempDir Path dir) throws IOException {
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("readme.txt", README);
        entries.put("music/tune.mod", TestModules.proTracker());
        entries.put("last.mod", README);
        final Path archive = Files.write(dir.resolve("a.lha"), TestArchives.lha(entries, method));
        final Path into = dir.resolve("out");

        extractor.extract(archive, into, name -> name.endsWith(".mod"));

        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(into.resolve("music/tune.mod")));
        assertArrayEquals(README, Files.readAllBytes(into.resolve("last.mod")));
        assertFalse(Files.exists(into.resolve("readme.txt")));
    }

    @Test
    void extractsEntriesAfterALargeUnwantedOne(@TempDir Path dir) throws IOException {
        final byte[] large = new byte[64 * 1024];
        new Random(7).nextBytes(large);
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("readme.txt", large);
        entries.put("tune.mod", TestModules.proTracker());
        final Path archive = Files.write(dir.resolve("a.lha"), TestArchives.lha(entries, "-lh5-"));

        extractor.extract(archive, dir.resolve("out"), name -> name.endsWith(".mod"));

        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(dir.resolve("out/tune.mod")));
    }

    @Test
    void recognisesLhaMagic() throws IOException {
        assertTrue(extractor.matches(TestArchives.lha(Map.of("a", README), "-lh5-")));
        assertFalse(extractor.matches(README));
    }

    @Test
    void namesEndAtTheNoteSomePackersLeaveInTheNameField(@TempDir Path dir) throws IOException {
        final byte[] archive = TestArchives.lha(Map.of("tune.mod\0spread by: keldon / giants", TestModules.proTracker()), "-lh5-");
        final Path file = Files.write(dir.resolve("a.lha"), archive);

        extractor.extract(file, dir.resolve("out"), name -> true);

        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(dir.resolve("out/tune.mod")));
    }

    @Test
    void truncatedArchivesFailWithAnIoException(@TempDir Path dir) throws IOException {
        final byte[] whole = TestArchives.lha(Map.of("tune.mod", TestModules.proTracker()), "-lh5-");
        final Path archive = Files.write(dir.resolve("a.lha"), Arrays.copyOf(whole, whole.length / 2));
        assertThrows(IOException.class, () -> extractor.extract(archive, dir.resolve("out"), name -> true));
    }
}

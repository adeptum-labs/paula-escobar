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

package com.adeptum.paula.module.ape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApeLoaderTest {

    private static final String FIXTURE = "paula-test.ape";
    private static final int RATE = 44100;

    private final ApeLoader loader = new ApeLoader();

    @Test
    void supportsTheExtensionsItAdvertises() {
        assertTrue(loader.supports(Path.of("tune.ape")));
        assertTrue(loader.supports(Path.of("TUNE.APE")));
        assertTrue(loader.supports(Path.of("tune.apl")));
        assertTrue(loader.supports(Path.of("tune.mac")));
        assertFalse(loader.supports(Path.of("tune.flac")));
    }

    @Test
    void readsWhatTheFileSaysAboutItself(@TempDir Path dir) throws IOException {
        final Module module = loader.load(fixture(dir));
        final ModuleMetadata meta = module.metadata();

        assertEquals("Monkey's Audio", meta.format().name());
        assertEquals(2, meta.channels());
        assertEquals(1, meta.songLength(), "a second of tone");
        assertEquals("seconds", meta.lengthUnit());
        assertTrue(meta.credits().contains("44100 Hz, 16 bit, stereo"), "was " + meta.credits());
    }

    @Test
    void readsTheStreamDescription(@TempDir Path dir) throws IOException {
        final ApeAudio audio = ((ApeModule) loader.load(fixture(dir))).audio();

        assertEquals(RATE, audio.rate());
        assertEquals(2, audio.channels());
        assertEquals(16, audio.bits());
        assertEquals(RATE, audio.totalBlocks(), "a second at forty-four kilohertz");
        assertEquals(1000, audio.length().orElseThrow().toMillis());
    }

    @Test
    void refusesAFileThatHoldsNoStream(@TempDir Path dir) throws IOException {
        final Path notAudio = Files.write(dir.resolve("empty.ape"), new byte[512]);

        assertThrows(UnsupportedModuleException.class, () -> loader.load(notAudio));
    }

    static Path fixture(Path dir) throws IOException {
        try (InputStream in = ApeLoaderTest.class.getResourceAsStream("/ape/" + FIXTURE)) {
            return Files.write(dir.resolve(FIXTURE), in.readAllBytes());
        }
    }
}

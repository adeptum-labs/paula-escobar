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

package com.adeptum.paula.module.flac;

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

class FlacLoaderTest {

    private static final String FIXTURE = "paula-test.flac";
    private static final int RATE = 44100;

    private final FlacLoader loader = new FlacLoader();

    @Test
    void supportsTheFlacExtension() {
        assertTrue(loader.supports(Path.of("tune.flac")));
        assertTrue(loader.supports(Path.of("TUNE.FLAC")));
        assertFalse(loader.supports(Path.of("tune.mp3")));
    }

    @Test
    void readsWhatTheFileSaysAboutItself(@TempDir Path dir) throws IOException {
        final Module module = loader.load(fixture(dir));
        final ModuleMetadata meta = module.metadata();

        assertEquals("Paula Test Tone", meta.title(), "the title comes from the Vorbis comment");
        assertEquals(2, meta.channels());
        assertEquals("FLAC", meta.format().name());
        assertTrue(meta.credits().contains("Adeptum"), "the artist is credited, was " + meta.credits());
        assertTrue(meta.credits().stream().anyMatch(line -> line.contains("44100 Hz")), "and the stream described");
        assertEquals(1, meta.songLength(), "a second of tone");
        assertEquals("seconds", meta.lengthUnit());
    }

    @Test
    void readsTheStreamInfoBlock(@TempDir Path dir) throws IOException {
        final FlacAudio audio = ((FlacModule) loader.load(fixture(dir))).audio();

        assertEquals(RATE, audio.rate());
        assertEquals(2, audio.channels());
        assertEquals(16, audio.bits());
        assertEquals(RATE, audio.totalSamples(), "a second at forty-four kilohertz");
        assertEquals(1000, audio.length().orElseThrow().toMillis());
        assertEquals("44100 Hz, 16 bit, stereo", audio.describe());
    }

    @Test
    void refusesAFileThatHoldsNoStream(@TempDir Path dir) throws IOException {
        final Path notAudio = Files.write(dir.resolve("empty.flac"), new byte[512]);

        assertThrows(UnsupportedModuleException.class, () -> loader.load(notAudio));
    }

    static Path fixture(Path dir) throws IOException {
        try (InputStream in = FlacLoaderTest.class.getResourceAsStream("/flac/" + FIXTURE)) {
            return Files.write(dir.resolve(FIXTURE), in.readAllBytes());
        }
    }
}

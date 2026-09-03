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

package com.adeptum.paula.module.mp3;

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

class Mp3LoaderTest {

    private static final String FIXTURE = "paula-test.mp3";
    private static final int RATE = 44100;

    private final Mp3Loader loader = new Mp3Loader();

    @Test
    void supportsTheMpegAudioExtensions() {
        assertTrue(loader.supports(Path.of("tune.mp3")));
        assertTrue(loader.supports(Path.of("TUNE.MP3")));
        assertTrue(loader.supports(Path.of("tune.mp2")));
        assertFalse(loader.supports(Path.of("tune.mod")));
    }

    @Test
    void readsWhatTheFileSaysAboutItself(@TempDir Path dir) throws IOException {
        final Module module = loader.load(fixture(dir));
        final ModuleMetadata meta = module.metadata();

        assertEquals("Paula Test Tone", meta.title(), "the title comes from the ID3 tag");
        assertEquals(2, meta.channels());
        assertEquals("MPEG audio", meta.format().name());
        assertTrue(meta.credits().contains("Adeptum"), "the artist is credited, was " + meta.credits());
        assertTrue(meta.credits().stream().anyMatch(line -> line.contains("44100 Hz")), "and the stream described");
        assertEquals(1, meta.songLength(), "a second of tone");
        assertEquals("seconds", meta.lengthUnit());
    }

    @Test
    void measuresTheStreamFrameByFrame(@TempDir Path dir) throws IOException {
        final Mp3Audio audio = ((Mp3Module) loader.load(fixture(dir))).audio();

        assertEquals(RATE, audio.rate());
        assertEquals(2, audio.channels());
        assertTrue(audio.frames() > 30, "a second at forty-four kilohertz is dozens of frames, was " + audio.frames());
        assertTrue(Math.abs(audio.length().toMillis() - 1000) < 60, "about a second, was " + audio.length());
        assertTrue(audio.bitrate() > 0);
    }

    @Test
    void refusesAFileWithNoFramesInIt(@TempDir Path dir) throws IOException {
        final Path notAudio = Files.write(dir.resolve("empty.mp3"), new byte[512]);

        assertThrows(UnsupportedModuleException.class, () -> loader.load(notAudio));
    }

    static Path fixture(Path dir) throws IOException {
        try (InputStream in = Mp3LoaderTest.class.getResourceAsStream("/mp3/" + FIXTURE)) {
            return Files.write(dir.resolve(FIXTURE), in.readAllBytes());
        }
    }
}

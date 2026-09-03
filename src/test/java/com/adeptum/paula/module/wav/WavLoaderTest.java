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

package com.adeptum.paula.module.wav;

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
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WavLoaderTest {

    static final String WAVE = "paula-test.wav";
    static final String AIFF = "paula-test.aif";
    static final String AU = "paula-test.au";

    private static final int RATE = 44100;

    /**
     * The wave fixture carries a LIST chunk of the software that wrote it between the format and the samples,
     * which a reader that assumed where the samples start would walk straight into.
     */
    private static final int DATA_AFTER_LIST = 78;

    private static final int HEADER_ONLY = 36;

    private final WavLoader loader = new WavLoader();

    @Test
    void supportsTheExtensionsItAdvertises() {
        assertTrue(loader.supports(Path.of("tune.wav")));
        assertTrue(loader.supports(Path.of("TUNE.WAV")));
        assertTrue(loader.supports(Path.of("tune.aif")));
        assertTrue(loader.supports(Path.of("tune.au")));
        assertFalse(loader.supports(Path.of("tune.mp3")));
    }

    @Test
    void readsWhatTheWaveFileSaysAboutItself(@TempDir Path dir) throws IOException {
        final Module module = loader.load(fixture(dir, WAVE));
        final ModuleMetadata meta = module.metadata();

        assertEquals("Wave audio", meta.format().name());
        assertEquals(2, meta.channels());
        assertEquals(1, meta.songLength(), "a second of tone");
        assertEquals("seconds", meta.lengthUnit());
        assertTrue(meta.credits().contains("16-bit PCM, 44100 Hz, stereo"), "was " + meta.credits());
    }

    @Test
    void walksPastTheChunksBetweenTheFormatAndTheSamples(@TempDir Path dir) throws IOException {
        final WavAudio audio = ((WavModule) loader.load(fixture(dir, WAVE))).audio();

        assertEquals(DATA_AFTER_LIST, audio.from());
        assertEquals(RATE, audio.rate());
        assertEquals(2, audio.channels());
        assertEquals(RATE, audio.frames(), "a second at forty-four kilohertz");
        assertEquals(1000, audio.length().toMillis());
    }

    @Test
    void readsTheCommChunkOfAnAiffFile(@TempDir Path dir) throws IOException {
        final WavAudio audio = ((WavModule) loader.load(fixture(dir, AIFF))).audio();

        assertEquals(22050, audio.rate(), "the rate is written as an eighty-bit extended float");
        assertEquals(1, audio.channels());
        assertEquals(22050, audio.frames());
        assertEquals("16-bit PCM, 22050 Hz, mono", audio.describe());
    }

    @Test
    void readsTheHeaderOfAnAuFile(@TempDir Path dir) throws IOException {
        final WavAudio audio = ((WavModule) loader.load(fixture(dir, AU))).audio();

        assertEquals(8000, audio.rate());
        assertEquals(1, audio.channels());
        assertEquals(8000, audio.frames());
        assertEquals("8-bit µ-law, 8000 Hz, mono", audio.describe());
    }

    @Test
    void refusesAFileThatHoldsNoSamples(@TempDir Path dir) throws IOException {
        final Path notAudio = Files.write(dir.resolve("empty.wav"), new byte[512]);

        assertThrows(UnsupportedModuleException.class, () -> loader.load(notAudio));
    }

    @Test
    void refusesAWaveFileWhoseSamplesNeverArrive(@TempDir Path dir) throws IOException {
        final byte[] file = Files.readAllBytes(fixture(dir, WAVE));
        final Path headerOnly = Files.write(dir.resolve("headless.wav"), Arrays.copyOf(file, HEADER_ONLY));

        assertThrows(UnsupportedModuleException.class, () -> loader.load(headerOnly));
    }

    static Path fixture(Path dir, String name) throws IOException {
        try (InputStream in = WavLoaderTest.class.getResourceAsStream("/wav/" + name)) {
            return Files.write(dir.resolve(name), in.readAllBytes());
        }
    }
}

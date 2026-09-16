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

package com.adeptum.paula.module.ay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.UnsupportedModuleException;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.testing.TestArchives;
import com.adeptum.paula.testing.TestAys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AyLoaderTest {

    private static final int RATE = 44100;
    private static final int BUFFER_FRAMES = 4096;

    private final AyLoader loader = new AyLoader();

    @Test
    void knowsTheRecordingsByName() {
        assertTrue(loader.supports(Path.of("tune.psg")));
        assertTrue(loader.supports(Path.of("TUNE.YM")));
        assertTrue(loader.supports(Path.of("tune.vtx")));
        assertTrue(loader.supports(Path.of("ym.beastbusters - 1")));
        assertFalse(loader.supports(Path.of("tune.mod")));
    }

    @Test
    void playsAPsgRecording(@TempDir Path dir) throws IOException {
        final Path file = Files.write(dir.resolve("tune.psg"), TestAys.psg());

        final Module module = loader.load(file);

        assertEquals(AyLoader.FORMAT, module.metadata().format());
        assertEquals(AyChip.CHANNELS, module.metadata().channels());
        assertTrue(module.createRenderer(RATE).render(new short[BUFFER_FRAMES * 2]) > 0);
    }

    /**
     * A YM recording is nearly always packed into an LHA archive of its own, which is unwrapped before the
     * registers are read.
     */
    @Test
    void playsAYmRecordingOutOfItsWrapper(@TempDir Path dir) throws IOException {
        final byte[] wrapped = TestArchives.lha(Map.of("SONG.YM", TestAys.ym()), "-lh5-");
        final Path file = Files.write(dir.resolve("tune.ym"), wrapped);

        final Module module = loader.load(file);

        assertEquals(TestAys.TITLE, module.metadata().title());
        assertEquals(List.of(TestAys.AUTHOR), module.metadata().credits());
        assertTrue(module.createRenderer(RATE).render(new short[BUFFER_FRAMES * 2]) > 0);
    }

    @Test
    void playsAYmRecordingThatWasNeverPacked(@TempDir Path dir) throws IOException {
        final Path file = Files.write(dir.resolve("tune.ym"), TestAys.ym());

        assertEquals(TestAys.TITLE, loader.load(file).metadata().title());
    }

    @Test
    void playsAVtxRecording(@TempDir Path dir) throws IOException {
        final Path file = Files.write(dir.resolve("tune.vtx"), TestAys.vtx());

        final Module module = loader.load(file);

        assertEquals(TestAys.TITLE, module.metadata().title());
        assertTrue(module.createRenderer(RATE).render(new short[BUFFER_FRAMES * 2]) > 0);
    }

    @Test
    void theRegistryRoutesARecordingHere(@TempDir Path dir) throws IOException {
        final Path file = Files.write(dir.resolve("tune.psg"), TestAys.psg());
        final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none());

        assertTrue(registry.formats().contains(AyLoader.FORMAT));
        assertEquals(AyLoader.FORMAT, registry.loaderFor(file).orElseThrow().format());
    }

    @Test
    void refusesAFileThatOnlyLooksLikeARecording(@TempDir Path dir) throws IOException {
        final Path file = Files.write(dir.resolve("tune.ym"),
                "not a recording at all, nor an archive".getBytes(StandardCharsets.US_ASCII));

        assertThrows(UnsupportedModuleException.class, () -> loader.load(file));
    }
}

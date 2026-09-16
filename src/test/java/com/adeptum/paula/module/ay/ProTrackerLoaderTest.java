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
import com.adeptum.paula.testing.TestPt3s;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProTrackerLoaderTest {

    private static final int RATE = 44100;
    private static final int BUFFER_FRAMES = 4096;
    private static final int AUDIBLE = 1000;

    private final ProTrackerLoader loader = new ProTrackerLoader();

    @Test
    void knowsTheModulesByName() {
        assertTrue(loader.supports(Path.of("tune.pt3")));
        assertTrue(loader.supports(Path.of("TUNE.PT3")));
        assertTrue(loader.supports(Path.of("pt3.aerodrome")), "as Modland names its files");
        assertFalse(loader.supports(Path.of("tune.mod")));
    }

    @Test
    void playsAModule(@TempDir Path dir) throws IOException {
        final Path file = Files.write(dir.resolve("tune.pt3"), TestPt3s.pt3());

        final Module module = loader.load(file);

        assertEquals(ProTrackerLoader.FORMAT, module.metadata().format());
        assertEquals(Pt3File.CHANNELS, module.metadata().channels());
        assertEquals(TestPt3s.TITLE, module.metadata().title());
        assertEquals(java.util.List.of(TestPt3s.AUTHOR), module.metadata().credits());
        assertTrue(loudest(module) > AUDIBLE, "the module should be heard");
    }

    @Test
    void theRegistryRoutesAModuleHere(@TempDir Path dir) throws IOException {
        final Path file = Files.write(dir.resolve("tune.pt3"), TestPt3s.pt3());
        final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none());

        assertTrue(registry.formats().contains(ProTrackerLoader.FORMAT));
        assertEquals(ProTrackerLoader.FORMAT, registry.loaderFor(file).orElseThrow().format());
    }

    @Test
    void refusesAFileThatOnlyLooksLikeAModule(@TempDir Path dir) throws IOException {
        final Path file = Files.write(dir.resolve("tune.pt3"),
                "not a module at all".getBytes(StandardCharsets.US_ASCII));

        assertThrows(UnsupportedModuleException.class, () -> loader.load(file));
    }

    private static int loudest(Module module) {
        final var renderer = module.createRenderer(RATE);
        final short[] buffer = new short[BUFFER_FRAMES * 2];
        int loudest = 0;
        for (int at = 0; at < 8; at++) {
            final int frames = renderer.render(buffer);
            for (int sample = 0; sample < frames * 2; sample++) {
                loudest = Math.max(loudest, Math.abs(buffer[sample]));
            }
        }
        return loudest;
    }
}

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

package com.adeptum.paula.module.composer669;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Composer669LoaderTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 4096;
    private static final int CHANNELS = 8;

    private final Composer669Loader loader = new Composer669Loader();

    @Test
    void supportsTheExtensionItAdvertises() {
        assertTrue(loader.supports(Path.of("tune.669")));
        assertTrue(loader.supports(Path.of("TUNE.669")));
        assertTrue(loader.supports(Path.of("669.doctor who")), "as Modland names its files");
        assertFalse(loader.supports(Path.of("tune.mod")));
    }

    @Test
    void readsWhatTheModuleSaysAboutItself(@TempDir Path dir) throws IOException {
        final Module module = loader.load(TestModules.writeComposer669(dir));
        final ModuleMetadata meta = module.metadata();

        assertEquals(TestModules.C669_TITLE, meta.title(), "the first line of the message stands in for a title");
        assertEquals(List.of(TestModules.C669_CREDIT), meta.credits(), "the rest of it for the credits, blanks left out");
        assertEquals(List.of(TestModules.SAMPLE_NAME), meta.instruments());
        assertEquals("Composer 669 modules", meta.format().name());
        assertEquals(CHANNELS, meta.channels());
        assertEquals(TestModules.C669_PATTERNS, meta.songLength());
        assertEquals("positions", meta.lengthUnit());
    }

    @Test
    void playsTheModuleItLoaded(@TempDir Path dir) throws IOException {
        final Module module = loader.load(TestModules.writeComposer669(dir));

        assertTrue(module.createRenderer(SAMPLE_RATE).render(new short[FRAMES * 2]) > 0, "the song plays");
    }

    @Test
    void isTheLoaderTheRegistryReachesForA669File(@TempDir Path dir) throws IOException {
        final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none());

        assertTrue(registry.formats().contains(Composer669Loader.FORMAT));
        assertEquals("Composer 669 modules",
                registry.load(TestModules.writeComposer669(dir)).metadata().format().name());
    }

    @Test
    void refusesAFileThatIsNotAModule(@TempDir Path dir) throws IOException {
        final Path garbage = Files.write(dir.resolve("x.669"), "if not a tune".getBytes(StandardCharsets.ISO_8859_1));

        assertThrows(UnsupportedModuleException.class, () -> loader.load(garbage));
    }
}

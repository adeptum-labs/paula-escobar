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

package com.adeptum.paula.module.med;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MedLoaderTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 4096;

    private final MedLoader loader = new MedLoader();

    @Test
    void supportsTheExtensionsItAdvertises() {
        assertTrue(loader.supports(Path.of("tune.med")));
        assertTrue(loader.supports(Path.of("TUNE.MED")));
        assertTrue(loader.supports(Path.of("tune.mmd0")));
        assertTrue(loader.supports(Path.of("tune.mmd3")));
        assertTrue(loader.supports(Path.of("MED.crystal hammer")), "as Modland names its files");
        assertFalse(loader.supports(Path.of("tune.mod")));
    }

    @Test
    void readsWhatTheModuleSaysAboutItself(@TempDir Path dir) throws IOException {
        final Module module = loader.load(TestModules.writeMed(dir));
        final ModuleMetadata meta = module.metadata();

        assertEquals(TestModules.SONG_NAME, meta.title());
        assertEquals("OctaMED modules", meta.format().name());
        assertEquals(TestModules.MED_TRACKS, meta.channels());
        assertEquals("blocks", meta.lengthUnit());
    }

    @Test
    void playsTheModuleItLoaded(@TempDir Path dir) throws IOException {
        final Module module = loader.load(TestModules.writeMed(dir));

        assertTrue(module.createRenderer(SAMPLE_RATE).render(new short[FRAMES * 2]) > 0, "the song plays");
    }

    @Test
    void isTheLoaderTheRegistryReachesForAMedFile(@TempDir Path dir) throws IOException {
        final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none());

        assertTrue(registry.formats().contains(MedLoader.FORMAT));
        assertEquals("OctaMED modules", registry.load(TestModules.writeMed(dir)).metadata().format().name());
    }

    @Test
    void refusesAFileThatIsNotAModule(@TempDir Path dir) throws IOException {
        final Path garbage = Files.write(dir.resolve("x.med"), "not a tune".getBytes(StandardCharsets.ISO_8859_1));

        assertThrows(UnsupportedModuleException.class, () -> loader.load(garbage));
    }
}

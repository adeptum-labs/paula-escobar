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

package com.adeptum.paula.module.xtracker;

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

class XTrackerLoaderTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 4096;

    private final XTrackerLoader loader = new XTrackerLoader();

    @Test
    void supportsTheExtensionItAdvertises() {
        assertTrue(loader.supports(Path.of("tune.dmf")));
        assertTrue(loader.supports(Path.of("TOX-EWIG.DMF")));
        assertTrue(loader.supports(Path.of("dmf.sad but true")), "as Modland names its files");
        assertFalse(loader.supports(Path.of("tune.669")));
    }

    @Test
    void readsWhatTheModuleSaysAboutItself(@TempDir Path dir) throws IOException {
        final ModuleMetadata meta = loader.load(TestModules.writeDmf(dir)).metadata();

        assertEquals(TestModules.DMF_TITLE, meta.title());
        assertEquals(List.of(TestModules.DMF_COMPOSER), meta.credits());
        assertEquals(List.of(TestModules.SAMPLE_NAME, TestModules.DMF_PACKED_NAME), meta.instruments());
        assertEquals("X-Tracker modules", meta.format().name());
        assertEquals(TestModules.DMF_TRACKS, meta.channels());
        assertEquals(2, meta.songLength());
        assertEquals("positions", meta.lengthUnit());
    }

    @Test
    void playsTheModuleItLoaded(@TempDir Path dir) throws IOException {
        final Module module = loader.load(TestModules.writeDmf(dir));

        assertTrue(module.createRenderer(SAMPLE_RATE).render(new short[FRAMES * 2]) > 0, "the song plays");
    }

    @Test
    void isTheLoaderTheRegistryReachesForADmfFile(@TempDir Path dir) throws IOException {
        final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none());

        assertTrue(registry.formats().contains(XTrackerLoader.FORMAT));
        assertEquals("X-Tracker modules", registry.load(TestModules.writeDmf(dir)).metadata().format().name());
    }

    /**
     * DSMI Compact and DefleMask files carry the same extension.
     */
    @Test
    void refusesAnotherTrackersDmfFile(@TempDir Path dir) throws IOException {
        final Path other = Files.write(dir.resolve("x.dmf"), ".DelekDefleMask.".getBytes(StandardCharsets.ISO_8859_1));

        assertThrows(UnsupportedModuleException.class, () -> loader.load(other));
    }
}

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


package com.adeptum.paula.module.ult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.UnsupportedModuleException;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.testing.TestModules;
import com.adeptum.paula.testing.TestUlts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UltLoaderTest {

    private final UltLoader loader = new UltLoader();

    @Test
    void claimsTheExtensionItReads() {
        assertTrue(loader.supports(Path.of("tune.ult")));
        assertTrue(loader.supports(Path.of("TUNE.ULT")));
        assertFalse(loader.supports(Path.of("tune.mod")));
    }

    @Test
    void claimsTheFormatNamedBeforeTheDot() {
        assertTrue(loader.supports(Path.of("ULT.passage")), "as Modland names its files");
    }

    @Test
    void isTheLoaderTheRegistryPicks() {
        final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none());

        assertEquals(UltLoader.FORMAT, registry.loaderFor(Path.of("tune.ult")).orElseThrow().format());
    }

    @Test
    void loadsAModuleOffTheDisk(@TempDir Path directory) throws IOException {
        final Module module = loader.load(Files.write(directory.resolve("tune.ult"), TestUlts.ult()));

        assertEquals(TestUlts.TITLE, module.metadata().title());
        assertEquals(TestUlts.CHANNELS, module.metadata().channels());
        assertEquals(TestUlts.SAMPLE_NAME, module.metadata().instruments().getFirst());
    }

    @Test
    void refusesAFileThatIsNotAnUltraTrackerModule(@TempDir Path directory) throws IOException {
        final Path notAModule = Files.write(directory.resolve("tune.ult"), TestModules.proTracker());

        assertThrows(UnsupportedModuleException.class, () -> loader.load(notAModule));
    }
}

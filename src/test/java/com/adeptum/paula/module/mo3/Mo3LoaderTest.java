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

package com.adeptum.paula.module.mo3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.UnsupportedModuleException;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Mo3LoaderTest {

    private final Mo3Loader loader = new Mo3Loader();

    @Test
    void claimsTheExtensionItReads() {
        assertTrue(loader.supports(Path.of("tune.mo3")));
        assertTrue(loader.supports(Path.of("TUNE.MO3")));
        assertFalse(loader.supports(Path.of("tune.mod")));
    }

    @Test
    void claimsTheFormatNamedBeforeTheDot() {
        assertTrue(loader.supports(Path.of("MO3.crystal hammer")), "as Modland names its files");
    }

    @Test
    void isTheLoaderTheRegistryPicks() {
        final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none());

        assertEquals(Mo3Loader.FORMAT, registry.loaderFor(Path.of("tune.mo3")).orElseThrow().format());
    }

    @Test
    void loadsAModuleOffTheDisk(@TempDir Path directory) throws IOException {
        final Module module = loader.load(TestModules.writeMo3(directory));

        assertEquals(TestModules.TITLE, module.metadata().title());
        assertEquals(TestModules.MO3_CHANNELS, module.metadata().channels());
        assertEquals("mo3", module.metadata().format().id());
        assertEquals("ProTracker", module.metadata().format().name());
    }

    @Test
    void namesTheSamplesTheModuleCarries(@TempDir Path directory) throws IOException {
        final Module module = loader.load(TestModules.writeMo3(directory));

        assertEquals(TestModules.INSTRUMENT_NAME, module.metadata().instruments().getFirst());
    }

    @Test
    void refusesAFileThatIsNotAnMo3(@TempDir Path directory) throws IOException {
        final Path notAModule = Files.write(directory.resolve("tune.mo3"), TestModules.proTracker());

        assertThrows(UnsupportedModuleException.class, () -> loader.load(notAModule));
    }
}

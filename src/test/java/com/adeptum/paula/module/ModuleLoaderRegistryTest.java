/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

package com.adeptum.paula.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.protracker.ProTrackerLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModuleLoaderRegistryTest {

    private final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders();

    @Test
    void listsBuiltInFormats() {
        assertEquals(java.util.List.of(ProTrackerLoader.FORMAT), registry.formats());
    }

    @Test
    void findsLoaderByFileName() {
        assertTrue(registry.loaderFor(Path.of("x.mod")).isPresent());
        assertTrue(registry.loaderFor(Path.of("x.sid")).isEmpty());
    }

    @Test
    void loadingUnknownFormatFails() {
        assertThrows(UnsupportedModuleException.class, () -> registry.load(Path.of("x.sid")));
    }
}

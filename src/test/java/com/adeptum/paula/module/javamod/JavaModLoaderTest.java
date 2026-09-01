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

package com.adeptum.paula.module.javamod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.UnsupportedModuleException;
import com.adeptum.paula.testing.TestModules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaModLoaderTest {

    private final JavaModLoader loader = new JavaModLoader();

    @Test
    void supportsTheClassicTrackerExtensions() {
        assertTrue(JavaModLoader.FORMAT.extensions().containsAll(List.of("mod", "xm", "s3m", "it", "stm")));
        assertTrue(loader.supports(Path.of("tune.XM")));
        assertTrue(loader.supports(Path.of("mod.tune")));
        assertFalse(loader.supports(Path.of("tune.sid")));
    }

    @Test
    void loadsMetadataFromAProTrackerModule(@TempDir Path dir) throws Exception {
        final Module module = loader.load(TestModules.writeProTracker(dir));

        assertEquals(TestModules.TITLE, module.metadata().title());
        assertEquals(4, module.metadata().channels());
        assertEquals(1, module.metadata().songLength());
        assertTrue(module.metadata().format().name().startsWith("ProTracker"));
        assertEquals(TestModules.SAMPLE_NAME, module.metadata().instruments().get(0));
    }

    @Test
    void reportsGarbageAsUnsupported(@TempDir Path dir) throws Exception {
        final Path garbage = Files.write(dir.resolve("garbage.mod"), new byte[100]);
        assertThrows(UnsupportedModuleException.class, () -> loader.load(garbage));
    }
}

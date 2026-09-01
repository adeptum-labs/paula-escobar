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

package com.adeptum.paula.module.sid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import com.adeptum.paula.testing.TestSids;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SidLoaderTest {

    private final SidLoader loader = new SidLoader(SongLengths.none());

    @Test
    void supportsSidExtensions() {
        assertTrue(loader.supports(Path.of("a.sid")));
        assertTrue(loader.supports(Path.of("b.PSID")));
        assertTrue(loader.supports(Path.of("c.rsid")));
        assertFalse(loader.supports(Path.of("d.mod")));
        assertEquals("sid", loader.format().id());
    }

    @Test
    void readsMetadataFromThePsidHeader(@TempDir Path dir) throws Exception {
        final Module module = loader.load(TestSids.writePsid(dir));
        final ModuleMetadata meta = module.metadata();

        assertEquals(TestSids.NAME, meta.title());
        assertTrue(meta.format().name().contains("PSID"), meta.format().name());
        assertEquals(3, meta.channels());
        assertEquals(TestSids.SUBTUNES, meta.songLength());
        assertEquals(List.of(TestSids.AUTHOR, TestSids.RELEASED), meta.credits());
        assertEquals(List.of(), meta.instruments());
        assertEquals(dir.resolve("test.sid"), module.source());
    }

    @Test
    void rejectsFilesTheEngineCannotParse(@TempDir Path dir) throws Exception {
        final Path garbage = Files.write(dir.resolve("x.sid"), "hello".getBytes(StandardCharsets.US_ASCII));
        assertThrows(UnsupportedModuleException.class, () -> loader.load(garbage));
    }
}

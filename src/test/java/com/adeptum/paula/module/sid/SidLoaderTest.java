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

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 2048;

    private final SidLoader loader = new SidLoader(SongLengths.none());

    @Test
    void supportsSidExtensions() {
        assertTrue(loader.supports(Path.of("a.sid")));
        assertTrue(loader.supports(Path.of("b.PSID")));
        assertTrue(loader.supports(Path.of("c.rsid")));
        assertTrue(loader.supports(Path.of("d.prg")), "programs run on the same emulation");
        assertTrue(loader.supports(Path.of("e.c64")));
        assertFalse(loader.supports(Path.of("f.mod")));
        assertFalse(loader.supports(Path.of("sid")), "a bare name is not an extension");
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
        assertEquals("subtunes", meta.lengthUnit());
        assertEquals(List.of(TestSids.AUTHOR, TestSids.RELEASED), meta.credits());
        assertEquals(List.of(), meta.instruments());
        assertEquals(dir.resolve("test.sid"), module.source());
    }

    @Test
    void runsC64ProgramsOnTheSameEmulation(@TempDir Path dir) throws Exception {
        final Module module = loader.load(TestSids.writeProgram(dir));

        assertTrue(module.metadata().format().name().contains("PRG"), module.metadata().format().name());
        assertEquals(1, module.metadata().songLength());
        assertTrue(module.createRenderer(SAMPLE_RATE).render(new short[FRAMES * 2]) > 0, "the program plays");
    }

    @Test
    void rejectsFilesTheEngineCannotParse(@TempDir Path dir) throws Exception {
        final Path garbage = Files.write(dir.resolve("x.sid"), "hello".getBytes(StandardCharsets.US_ASCII));
        final Path stub = Files.write(dir.resolve("x.prg"), "hello".getBytes(StandardCharsets.US_ASCII));

        assertThrows(UnsupportedModuleException.class, () -> loader.load(garbage));
        assertThrows(UnsupportedModuleException.class, () -> loader.load(stub), "a program too short to be one");
    }
}

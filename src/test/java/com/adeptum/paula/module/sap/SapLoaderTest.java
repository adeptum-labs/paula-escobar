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

package com.adeptum.paula.module.sap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import com.adeptum.paula.testing.TestSaps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SapLoaderTest {

    private final SapLoader loader = new SapLoader();

    @Test
    void supportsEveryExtensionAsapReads() {
        assertTrue(loader.supports(Path.of("tune.sap")));
        assertTrue(loader.supports(Path.of("b.RMT")));
        assertTrue(loader.supports(Path.of("SAP.tune")), "the Modland way round");
        assertTrue(loader.supports(Path.of("song.tm2")));
        assertFalse(loader.supports(Path.of("sap")), "a bare name is not an extension");
        assertFalse(loader.supports(Path.of("tune.mod")));
        assertEquals("sap", loader.format().id());
    }

    @Test
    void turnsAModlandNameRoundForAsap() {
        assertEquals("tune.sap", SapLoader.nameForAsap("SAP.tune"));
        assertEquals("tune.sap", SapLoader.nameForAsap("tune.sap"));
        assertEquals("tune", SapLoader.nameForAsap("tune"));
    }

    @Test
    void readsTheMetadata(@TempDir Path dir) throws IOException {
        final Module module = loader.load(TestSaps.writeSap(dir));
        final ModuleMetadata meta = module.metadata();

        assertEquals(TestSaps.NAME, meta.title());
        assertEquals("Slight Atari Player (ASAP)", meta.format().name());
        assertEquals("sap", meta.format().id());
        assertEquals(4, meta.channels(), "one POKEY");
        assertEquals(TestSaps.SUBTUNES, meta.songLength());
        assertEquals("subtunes", meta.lengthUnit());
        assertEquals(List.of(TestSaps.AUTHOR, TestSaps.DATE), meta.credits());
        assertTrue(meta.instruments().isEmpty());
        assertEquals(dir.resolve("test.sap"), module.source());
    }

    @Test
    void playsTheDefaultSubtuneForItsTaggedLength(@TempDir Path dir) throws IOException {
        final SapModule module = (SapModule) loader.load(TestSaps.writeSap(dir));

        assertEquals(0, module.song());
        assertEquals(TestSaps.LENGTH, module.length());
    }

    @Test
    void rejectsWhatAsapCannotRead(@TempDir Path dir) throws IOException {
        final Path garbage = Files.write(dir.resolve("garbage.sap"), "not a tune".getBytes(StandardCharsets.US_ASCII));
        assertThrows(UnsupportedModuleException.class, () -> loader.load(garbage));
    }
}

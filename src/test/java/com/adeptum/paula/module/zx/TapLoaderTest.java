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

package com.adeptum.paula.module.zx;

import static com.adeptum.paula.testing.TestTaps.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.UnsupportedModuleException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TapLoaderTest {

    @TempDir
    Path dir;

    private TapLoader loader() {
        return new TapLoader(new TapeLengths(new CacheDirectory(dir.resolve("cache"))));
    }

    private Path write(String name, byte[] content) throws Exception {
        return Files.write(dir.resolve(name), content);
    }

    @Test
    void supportsTheTapExtension() {
        assertTrue(loader().supports(Path.of("a.tap")));
        assertTrue(loader().supports(Path.of("TAP.tune")), "Modland prefixes its names");
        assertFalse(loader().supports(Path.of("a.tzx")));
        assertEquals("tap", loader().format().id());
    }

    @Test
    void describesABeeperTape() throws Exception {
        final Module module = loader().load(write("a.tap", loaderTape(ONE_AND_A_HALF_SECONDS_THEN_RETURN)));

        assertEquals("loader", module.metadata().title());
        assertEquals("ZX Spectrum tape (48K beeper)", module.metadata().format().name());
        assertEquals(1, module.metadata().channels());
        assertEquals("seconds", module.metadata().lengthUnit());
        assertEquals(1, module.metadata().songLength());
    }

    @Test
    void refusesATapeWhoseSongEndsAtOnce() throws Exception {
        final Path tape = write("a.tap", loaderTape(ONE_PULSE_THEN_RETURN));

        final UnsupportedModuleException e = assertThrows(UnsupportedModuleException.class, () -> loader().load(tape));
        assertTrue(e.getMessage().contains("ends at once") && e.getMessage().contains("BASIC"), e.getMessage());
    }

    @Test
    void describesAnAyTape() throws Exception {
        final Module module = loader().load(write("a.tap", loaderTape(AY_TONE)));

        assertEquals("ZX Spectrum tape (128K AY and beeper)", module.metadata().format().name());
        assertEquals(4, module.metadata().channels());
    }

    @Test
    void refusesATapeThatMakesNoSound() throws Exception {
        final Path tape = write("a.tap", loaderTape(WAIT_FOREVER));

        final UnsupportedModuleException e = assertThrows(UnsupportedModuleException.class, () -> loader().load(tape));
        assertTrue(e.getMessage().contains("no sound"), e.getMessage());
    }

    @Test
    void refusesATapeThatNeedsTheRom() throws Exception {
        final Path tape = write("a.tap", loaderTape(SILENT_JUMP_TO_ROM));

        final UnsupportedModuleException e = assertThrows(UnsupportedModuleException.class, () -> loader().load(tape));
        assertTrue(e.getMessage().contains("ROM"), e.getMessage());
    }

    @Test
    void refusesAFileThatIsNotATape() throws Exception {
        final Path raw = write("a.tap", "C64-TAPE-RAW\u0001\0\0\0\u0010\0\0\0".getBytes(StandardCharsets.ISO_8859_1));

        assertThrows(UnsupportedModuleException.class, () -> loader().load(raw));
    }
}


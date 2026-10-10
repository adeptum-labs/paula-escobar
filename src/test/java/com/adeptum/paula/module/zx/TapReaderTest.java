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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.adeptum.paula.module.UnsupportedModuleException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TapReaderTest {

    private static final Path PATH = Path.of("x.tap");

    @Test
    void pairsEachHeaderWithTheDataThatFollowsIt() throws Exception {
        final TapFile tape = TapReader.read(PATH, loaderTape(SQUARE_WAVE));

        final List<TapPart> parts = tape.parts();

        assertEquals(2, parts.size());
        assertEquals(TapHeader.PROGRAM, parts.get(0).header().type());
        assertEquals("loader", parts.get(0).header().name());
        assertEquals(TapHeader.CODE, parts.get(1).header().type());
        assertEquals("music", parts.get(1).header().name());
        assertEquals(ENTRY, parts.get(1).header().param1());
        assertArrayEquals(SQUARE_WAVE, parts.get(1).data());
    }

    @Test
    void toleratesABadChecksum() throws Exception {
        final byte[] tape = loaderTape(SQUARE_WAVE);
        tape[tape.length - 1] ^= 0x55;

        assertEquals(2, TapReader.read(PATH, tape).parts().size());
    }

    @Test
    void refusesACommodoreRawTape() {
        final byte[] c64 = "C64-TAPE-RAW\u0001\0\0\0\u0010\0\0\0".getBytes(StandardCharsets.ISO_8859_1);

        assertThrows(UnsupportedModuleException.class, () -> TapReader.read(PATH, c64));
    }

    @Test
    void refusesABlockThatRunsPastTheEndOfTheFile() {
        final byte[] tape = loaderTape(SQUARE_WAVE);

        assertThrows(UnsupportedModuleException.class,
                () -> TapReader.read(PATH, java.util.Arrays.copyOf(tape, tape.length - 3)));
    }

    @Test
    void refusesAnEmptyFileAndASingleStrayByte() {
        assertThrows(UnsupportedModuleException.class, () -> TapReader.read(PATH, new byte[0]));
        assertThrows(UnsupportedModuleException.class, () -> TapReader.read(PATH, new byte[] {1}));
    }
}

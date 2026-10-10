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
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TapProgramTest {

    private static TapProgram program(byte[] tape) throws Exception {
        return TapProgram.of(Path.of("x.tap"), TapReader.read(Path.of("x.tap"), tape));
    }

    @Test
    void startsWhereTheLoaderJumpsAndLoadsWhereTheHeaderSays() throws Exception {
        final TapProgram program = program(loaderTape(SQUARE_WAVE));

        assertEquals(ENTRY, program.entry());
        assertEquals(32767, program.ramTop());
        assertEquals(ENTRY, program.segments().get(0).address());
        assertArrayEquals(SQUARE_WAVE, program.segments().get(0).data());
    }

    @Test
    void letsTheLoaderOverrideTheHeaderAddress() throws Exception {
        final byte[] tape = tape(programPart("loader", line(1, tokenised("{LOAD}\"\" {CODE} {NUM:40000}: {RANDOMIZE} {USR} {NUM:40000}"))),
                codePart("music", ENTRY, SQUARE_WAVE));

        assertEquals(40000, program(tape).segments().get(0).address());
    }

    @Test
    void startsATapeWithoutBasicAtItsFirstCodeBlock() throws Exception {
        final TapProgram program = program(tape(codePart("music", ENTRY, SQUARE_WAVE)));

        assertEquals(ENTRY, program.entry());
        assertEquals(TapProgram.DEFAULT_RAM_TOP, program.ramTop());
    }

    @Test
    void refusesALoaderThatNeverStartsMachineCode() {
        final byte[] tape = tape(programPart("loader", line(1, tokenised("{LOAD}\"\" {CODE}"))),
                codePart("music", ENTRY, SQUARE_WAVE));

        assertThrows(UnsupportedModuleException.class, () -> program(tape));
    }

    @Test
    void refusesATapeWithNoMachineCode() {
        final byte[] tape = tape(programPart("loader", line(1, tokenised("{CLEAR} 100"))));

        assertThrows(UnsupportedModuleException.class, () -> program(tape));
    }
}

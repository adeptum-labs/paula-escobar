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

import static com.adeptum.paula.testing.TestTaps.line;
import static com.adeptum.paula.testing.TestTaps.tokenised;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class BasicProgramTest {

    private static BasicProgram parse(String... sources) {
        int number = 1;
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (final String source : sources) {
            out.writeBytes(line(number++, tokenised(source)));
        }
        return BasicProgram.parse(out.toByteArray());
    }

    @Test
    void readsTheLoaderOfARealTape() {
        final BasicProgram basic = parse("{CLEAR} {VAL}\"4E4\": {LOAD}\"tritone\" {CODE}: {RANDOMIZE} {USR} {VAL}\"49152\"");

        assertEquals(OptionalInt.of(40000), basic.clear());
        assertEquals(OptionalInt.of(49152), basic.entry());
        assertEquals(1, basic.loads().size());
        assertTrue(basic.loads().get(0).isEmpty());
    }

    @Test
    void readsNumbersTheEditorStoresInTheirHiddenForm() {
        final BasicProgram basic = parse("{CLEAR} {NUM:32767}: {LOAD}\"\" {CODE} {NUM:32768}: {RANDOMIZE} {USR} {NUM:32768}");

        assertEquals(OptionalInt.of(32767), basic.clear());
        assertEquals(OptionalInt.of(32768), basic.entry());
        assertEquals(OptionalInt.of(32768), basic.loads().get(0));
    }

    @Test
    void countsEveryCodeAndScreenLoadInOrder() {
        final BasicProgram basic = parse("{LOAD}\"\" {SCREEN}: {LOAD}\"a\" {CODE} {NUM:40000}: {LOAD}\"b\" {CODE}");

        assertEquals(3, basic.loads().size());
        assertTrue(basic.loads().get(0).isEmpty());
        assertEquals(OptionalInt.of(40000), basic.loads().get(1));
        assertTrue(basic.loads().get(2).isEmpty());
    }

    @Test
    void takesTheLastUsrOfTheProgram() {
        final BasicProgram basic = parse("{RANDOMIZE} {USR} 30000", "{RANDOMIZE} {USR} 40000");

        assertEquals(OptionalInt.of(40000), basic.entry());
    }

    @Test
    void ignoresTokenBytesInsideStringsRemarksAndHiddenNumbers() {
        final BasicProgram basic = parse("PRINT \"{CLEAR} {USR}\"", "{REM} {USR} 1", "LET a=5: {CLEAR} 9000");

        assertTrue(basic.entry().isEmpty());
        assertEquals(OptionalInt.of(9000), basic.clear());
    }

    @Test
    void doesNotReadTheHiddenFloatAsATokenSequence() {
        final byte[] floatWithTokenBytes = {'5', 0x0E, 0, 0, (byte) 0xFD, (byte) 0xC0, 0};
        final byte[] program = line(1, floatWithTokenBytes);

        final BasicProgram basic = BasicProgram.parse(program);

        assertTrue(basic.clear().isEmpty());
        assertTrue(basic.entry().isEmpty());
    }

    @Test
    void givesUpOnExpressionsItCannotEvaluate() {
        final BasicProgram basic = parse("{RANDOMIZE} {USR} (a+1)");

        assertTrue(basic.entry().isEmpty());
    }

    @Test
    void survivesATruncatedProgram() {
        final byte[] whole = line(1, tokenised("{CLEAR} 100"));

        BasicProgram.parse(java.util.Arrays.copyOf(whole, whole.length - 4));
    }
}

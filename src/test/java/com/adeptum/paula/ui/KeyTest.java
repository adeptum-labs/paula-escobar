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

package com.adeptum.paula.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyTest {

    private static final int READ_TIMED_OUT = -2;
    private static final int END_OF_STREAM = -1;

    @Test
    void mapsControlBytesToSpecials() {
        assertEquals(Key.of(Key.Special.ENTER), Key.forByte(13));
        assertEquals(Key.of(Key.Special.ENTER), Key.forByte(10));
        assertEquals(Key.of(Key.Special.BACKSPACE), Key.forByte(127));
        assertEquals(Key.of(Key.Special.BACKSPACE), Key.forByte(8));
        assertEquals(Key.of(Key.Special.ESCAPE), Key.forByte(27));
        assertEquals(Key.of(Key.Special.EOF), Key.forByte(END_OF_STREAM));
        assertEquals(Key.of(Key.Special.TIMEOUT), Key.forByte(READ_TIMED_OUT));
    }

    @Test
    void mapsPrintableBytesToCharacters() {
        assertEquals(Key.of('q'), Key.forByte('q'));
        assertTrue(Key.forByte(' ').is(' '));
        assertTrue(Key.forByte(13).is(Key.Special.ENTER));
    }

    @Test
    void mapsCursorSequencesInCsiAndSs3Forms() {
        assertEquals(Key.of(Key.Special.UP), Key.forEscapeSequence("[A"));
        assertEquals(Key.of(Key.Special.DOWN), Key.forEscapeSequence("OB"));
        assertEquals(Key.of(Key.Special.RIGHT), Key.forEscapeSequence("[C"));
        assertEquals(Key.of(Key.Special.LEFT), Key.forEscapeSequence("OD"));
        assertEquals(Key.of(Key.Special.HOME), Key.forEscapeSequence("[H"));
        assertEquals(Key.of(Key.Special.HOME), Key.forEscapeSequence("[1~"));
        assertEquals(Key.of(Key.Special.END), Key.forEscapeSequence("OF"));
        assertEquals(Key.of(Key.Special.END), Key.forEscapeSequence("[4~"));
        assertEquals(Key.of(Key.Special.PAGE_UP), Key.forEscapeSequence("[5~"));
        assertEquals(Key.of(Key.Special.PAGE_DOWN), Key.forEscapeSequence("[6~"));
    }

    @Test
    void ignoresModifiersAndUnknownSequences() {
        assertEquals(Key.of(Key.Special.RIGHT), Key.forEscapeSequence("[1;5C"));
        assertEquals(Key.of(Key.Special.RIGHT), Key.forEscapeSequence("[1;13C"));
        assertEquals(Key.NONE, Key.forEscapeSequence("[Z"));
        assertEquals(Key.NONE, Key.forEscapeSequence(""));
    }
}

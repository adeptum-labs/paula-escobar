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

package com.adeptum.paula.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ActionTest {

    private static final int ESCAPE = 27;
    private static final int READ_TIMED_OUT = -2;

    @Test
    void mapsKeysToActions() {
        assertEquals(Action.QUIT, Action.forKey('q'));
        assertEquals(Action.QUIT, Action.forKey(ESCAPE));
        assertEquals(Action.TOGGLE_PAUSE, Action.forKey(' '));
        assertEquals(Action.NEXT, Action.forKey('n'));
        assertEquals(Action.PREVIOUS, Action.forKey('p'));
    }

    @Test
    void unknownKeysAndTimeoutsAreIgnored() {
        assertEquals(Action.NONE, Action.forKey('x'));
        assertEquals(Action.NONE, Action.forKey(READ_TIMED_OUT));
    }

    @Test
    void mapsCursorSequencesToSeekActions() {
        assertEquals(Action.SEEK_FORWARD, Action.forEscapeSequence("[C"));
        assertEquals(Action.SEEK_BACKWARD, Action.forEscapeSequence("[D"));
        assertEquals(Action.SEEK_FORWARD, Action.forEscapeSequence("OC"));
        assertEquals(Action.SEEK_BACKWARD, Action.forEscapeSequence("OD"));
    }

    @Test
    void unknownEscapeSequencesAreIgnored() {
        assertEquals(Action.NONE, Action.forEscapeSequence("[A"));
        assertEquals(Action.NONE, Action.forEscapeSequence(""));
    }
}

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

    @Test
    void mapsKeysToActions() {
        assertEquals(Action.QUIT, Action.of(Key.of('q')));
        assertEquals(Action.QUIT, Action.of(Key.of('Q')));
        assertEquals(Action.QUIT, Action.of(Key.of(Key.Special.ESCAPE)));
        assertEquals(Action.QUIT, Action.of(Key.of(Key.Special.EOF)));
        assertEquals(Action.TOGGLE_PAUSE, Action.of(Key.of(' ')));
        assertEquals(Action.NEXT, Action.of(Key.of('n')));
        assertEquals(Action.PREVIOUS, Action.of(Key.of('p')));
        assertEquals(Action.SEEK_FORWARD, Action.of(Key.of(Key.Special.RIGHT)));
        assertEquals(Action.SEEK_BACKWARD, Action.of(Key.of(Key.Special.LEFT)));
    }

    @Test
    void unknownKeysAndTimeoutsAreIgnored() {
        assertEquals(Action.NONE, Action.of(Key.of('x')));
        assertEquals(Action.NONE, Action.of(Key.of(Key.Special.TIMEOUT)));
        assertEquals(Action.NONE, Action.of(Key.of(Key.Special.UP)));
    }
}

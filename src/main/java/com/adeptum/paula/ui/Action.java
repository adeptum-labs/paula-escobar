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

public enum Action {
    NONE, QUIT, TOGGLE_PAUSE, NEXT, PREVIOUS, SEEK_BACKWARD, SEEK_FORWARD, BROWSE;

    public static Action of(Key key) {
        return switch (key.special()) {
            case ESCAPE, EOF -> QUIT;
            case RIGHT -> SEEK_FORWARD;
            case LEFT -> SEEK_BACKWARD;
            case NONE -> forCharacter(key.character());
            default -> NONE;
        };
    }

    private static Action forCharacter(char character) {
        return switch (Character.toLowerCase(character)) {
            case 'q' -> QUIT;
            case ' ' -> TOGGLE_PAUSE;
            case 'n' -> NEXT;
            case 'p' -> PREVIOUS;
            case 'b' -> BROWSE;
            default -> NONE;
        };
    }
}

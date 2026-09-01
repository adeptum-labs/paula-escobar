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

public enum Action {
    NONE, QUIT, TOGGLE_PAUSE, NEXT, PREVIOUS;

    private static final int ESCAPE = 27;

    public static Action forKey(int key) {
        return switch (key) {
            case 'q', 'Q', ESCAPE -> QUIT;
            case ' ' -> TOGGLE_PAUSE;
            case 'n', 'N' -> NEXT;
            case 'p', 'P' -> PREVIOUS;
            default -> NONE;
        };
    }
}

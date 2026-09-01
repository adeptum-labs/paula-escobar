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

/**
 * One key press as the terminal delivered it: either a printable character or a special key.
 */
public record Key(Special special, char character) {

    public enum Special { NONE, UP, DOWN, LEFT, RIGHT, ENTER, BACKSPACE, ESCAPE, PAGE_UP, PAGE_DOWN, HOME, END, EOF, TIMEOUT }

    public static final Key NONE = new Key(Special.NONE, '\0');

    private static final int END_OF_STREAM = -1;
    private static final int LINE_FEED = 10;
    private static final int CARRIAGE_RETURN = 13;
    private static final int BACKSPACE_CODE = 8;
    private static final int DELETE_CODE = 127;
    private static final int ESCAPE_CODE = 27;
    private static final String MODIFIER_PARAMETER = "^\\[1;\\d";

    public static Key of(char character) {
        return new Key(Special.NONE, character);
    }

    public static Key of(Special special) {
        return new Key(special, '\0');
    }

    public static Key forByte(int value) {
        return switch (value) {
            case END_OF_STREAM -> of(Special.EOF);
            case LINE_FEED, CARRIAGE_RETURN -> of(Special.ENTER);
            case BACKSPACE_CODE, DELETE_CODE -> of(Special.BACKSPACE);
            case ESCAPE_CODE -> of(Special.ESCAPE);
            default -> value < 0 ? of(Special.TIMEOUT) : of((char) value);
        };
    }

    /**
     * Maps a sequence without its leading escape; cursor keys arrive as CSI while an application mode terminal
     * sends the SS3 form, and modifiers such as ctrl are dropped.
     */
    public static Key forEscapeSequence(String sequence) {
        return switch (sequence.replaceFirst(MODIFIER_PARAMETER, "[")) {
            case "[A", "OA" -> of(Special.UP);
            case "[B", "OB" -> of(Special.DOWN);
            case "[C", "OC" -> of(Special.RIGHT);
            case "[D", "OD" -> of(Special.LEFT);
            case "[H", "OH", "[1~", "[7~" -> of(Special.HOME);
            case "[F", "OF", "[4~", "[8~" -> of(Special.END);
            case "[5~" -> of(Special.PAGE_UP);
            case "[6~" -> of(Special.PAGE_DOWN);
            default -> NONE;
        };
    }

    public boolean is(char expected) {
        return special == Special.NONE && character == expected;
    }

    public boolean is(Special expected) {
        return special == expected;
    }
}

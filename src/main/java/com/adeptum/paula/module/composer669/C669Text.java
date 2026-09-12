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

package com.adeptum.paula.module.composer669;

/**
 * The text of a 669 file as the DOS screen showed it: code page 437, where the bytes below a space are the
 * smileys, card suits, arrows and note signs of the IBM PC's character ROM rather than the control characters
 * a terminal would take them for, and the high half is the accented letters and box drawing of ANSI art.
 */
final class C669Text {

    private static final String BELOW_SPACE = "☺☻♥♦♣♠•◘○◙♂♀♪♫☼►◄↕‼¶§▬↨↑↓→←∟↔▲▼";
    private static final char DELETE = '⌂';
    private static final String HIGH_HALF = "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀"
            + "αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ ";
    private static final int SPACE = 0x20;
    private static final int DELETE_BYTE = 0x7F;
    private static final int HIGH = 0x80;
    private static final int BYTE = 0xFF;

    private C669Text() {
    }

    /**
     * The text up to its first zero byte, without the spaces the tracker pads its fields with.
     */
    static String decode(byte[] bytes) {
        final StringBuilder text = new StringBuilder(bytes.length);
        for (final byte b : bytes) {
            final int value = b & BYTE;
            if (value == 0) {
                break;
            }
            text.append(glyph(value));
        }
        return text.toString().strip();
    }

    private static char glyph(int value) {
        if (value < SPACE) {
            return BELOW_SPACE.charAt(value - 1);
        }
        if (value == DELETE_BYTE) {
            return DELETE;
        }
        return value < HIGH ? (char) value : HIGH_HALF.charAt(value - HIGH);
    }
}

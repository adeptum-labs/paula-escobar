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

package com.adeptum.paula.text;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.HashMap;
import java.util.Map;

/**
 * Code page 437, the character set of the IBM PC, in which the DOS trackers wrote their text and the zippers
 * of the day their file names. The bytes below a space are the smileys, card suits, arrows and note signs of
 * the PC's character ROM rather than the control characters a terminal takes them for, and the high half is
 * the accented letters and box drawing of ANSI art. The JDK has the code page too, but not in a native image.
 */
public final class CodePage437 extends Charset {

    public static final Charset CHARSET = new CodePage437();

    private static final String BELOW_SPACE = "☺☻♥♦♣♠•◘○◙♂♀♪♫☼►◄↕‼¶§▬↨↑↓→←∟↔▲▼";
    private static final char DELETE = '⌂';
    private static final String HIGH_HALF = "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀"
            + "αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ ";
    private static final int SPACE = 0x20;
    private static final int DELETE_BYTE = 0x7F;
    private static final int HIGH = 0x80;
    private static final int BYTES = 256;
    private static final int BYTE = 0xFF;
    private static final char[] TO_CHAR = new char[BYTES];
    private static final Map<Character, Byte> TO_BYTE = new HashMap<>();

    static {
        for (int value = 0; value < BYTES; value++) {
            TO_CHAR[value] = glyph(value);
            TO_BYTE.put(TO_CHAR[value], (byte) value);
        }
    }

    private CodePage437() {
        super("IBM437", new String[] {"CP437"});
    }

    private static char glyph(int value) {
        if (value == 0) {
            return 0;
        }
        if (value < SPACE) {
            return BELOW_SPACE.charAt(value - 1);
        }
        if (value == DELETE_BYTE) {
            return DELETE;
        }
        return value < HIGH ? (char) value : HIGH_HALF.charAt(value - HIGH);
    }

    @Override
    public boolean contains(Charset other) {
        return other == this;
    }

    @Override
    public CharsetDecoder newDecoder() {
        return new CharsetDecoder(this, 1, 1) {
            @Override
            protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
                while (in.hasRemaining()) {
                    if (!out.hasRemaining()) {
                        return CoderResult.OVERFLOW;
                    }
                    out.put(TO_CHAR[in.get() & BYTE]);
                }
                return CoderResult.UNDERFLOW;
            }
        };
    }

    @Override
    public CharsetEncoder newEncoder() {
        return new CharsetEncoder(this, 1, 1) {
            @Override
            protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
                while (in.hasRemaining()) {
                    final Byte value = TO_BYTE.get(in.get(in.position()));
                    if (value == null) {
                        return CoderResult.unmappableForLength(1);
                    }
                    if (!out.hasRemaining()) {
                        return CoderResult.OVERFLOW;
                    }
                    in.get();
                    out.put(value);
                }
                return CoderResult.UNDERFLOW;
            }
        };
    }
}

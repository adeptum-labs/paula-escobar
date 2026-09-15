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

package com.adeptum.paula.module.xtracker;

import com.adeptum.paula.text.CodePage437;

/**
 * The text of a DMF file as the DOS screen showed it, in code page 437, where the bytes below a space are glyphs
 * rather than the control characters a terminal would take them for.
 */
final class DmfText {

    private static final byte ZERO = 0;
    private static final byte SPACE = ' ';

    private DmfText() {
    }

    /**
     * The text of a field: its zero bytes read as spaces, the literal space bytes the tracker pads its end with
     * then dropped, and what remains decoded as code page 437. The trimming happens on the raw bytes, before
     * decoding, so a byte that only happens to decode into something space-like stays. A leading space is kept,
     * since the tracker lets an author put one there on purpose.
     */
    static String decode(byte[] bytes) {
        final byte[] spaced = bytes.clone();
        for (int at = 0; at < spaced.length; at++) {
            if (spaced[at] == ZERO) {
                spaced[at] = SPACE;
            }
        }
        int end = spaced.length;
        while (end > 0 && spaced[end - 1] == SPACE) {
            end--;
        }
        return new String(spaced, 0, end, CodePage437.CHARSET);
    }
}

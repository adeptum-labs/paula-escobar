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

import java.nio.charset.StandardCharsets;

record TapHeader(int type, String name, int length, int param1, int param2) {

    static final int PROGRAM = 0;
    static final int CODE = 3;
    static final int SIZE = 17;

    private static final int NAME_AT = 1;
    private static final int NAME_LENGTH = 10;

    static TapHeader of(byte[] fields) {
        return new TapHeader(fields[0],
                new String(fields, NAME_AT, NAME_LENGTH, StandardCharsets.ISO_8859_1).stripTrailing(),
                word(fields, 11), word(fields, 13), word(fields, 15));
    }

    private static int word(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) | (bytes[at + 1] & 0xFF) << 8;
    }
}

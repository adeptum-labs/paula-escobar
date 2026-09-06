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

package com.adeptum.paula.archive;

/**
 * What the Commodore file names in a disk or tape image have in common: sixteen bytes padded with shifted
 * spaces or nothing at all, holding whatever a scene handle was spelled with, none of which belongs in a name
 * a file system has to take.
 */
final class C64Names {

    static final int LENGTH = 16;

    private static final int PADDING = 0xA0;
    private static final char REPLACEMENT = '-';
    private static final String UNNAMED = "program";

    private C64Names() {
    }

    static String of(byte[] bytes, int at) {
        final StringBuilder name = new StringBuilder(LENGTH);
        for (int index = 0; index < LENGTH; index++) {
            final int character = bytes[at + index] & 0xFF;
            if (character == PADDING || character == 0) {
                break;
            }
            name.append(character < ' ' || character > '~' || character == '/' || character == '\\'
                    ? REPLACEMENT : (char) character);
        }
        final String trimmed = name.toString().strip();
        return trimmed.isEmpty() ? UNNAMED : trimmed;
    }
}

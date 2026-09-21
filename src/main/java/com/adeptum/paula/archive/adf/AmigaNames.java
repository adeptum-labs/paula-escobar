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

package com.adeptum.paula.archive.adf;

/**
 * What an AmigaDOS name is on disk: a length byte followed by that many characters, none of which is guaranteed
 * to be one a file system on another machine can take.
 */
final class AmigaNames {

    static final int MAX_LENGTH = 30;

    private static final char REPLACEMENT = '-';
    private static final String UNNAMED = "file";

    private AmigaNames() {
    }

    static String of(byte[] image, int lengthAt) {
        final int length = Math.min(image[lengthAt] & 0xFF, MAX_LENGTH);
        final StringBuilder name = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            final int character = image[lengthAt + 1 + index] & 0xFF;
            name.append(character < ' ' || character > '~' || character == '/' || character == '\\'
                    ? REPLACEMENT : (char) character);
        }
        final String trimmed = name.toString().strip();
        return trimmed.isEmpty() ? UNNAMED : trimmed;
    }
}

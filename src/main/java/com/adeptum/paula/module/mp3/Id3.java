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

package com.adeptum.paula.module.mp3;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.Builder;

/**
 * The title, artist, album and year an MP3 carries about itself. A file may hold an ID3v2 tag at its head, the
 * older ID3v1 in its last hundred and twenty-eight bytes, both, or neither.
 */
@Builder
public record Id3(String title, String artist, String album, String year) {

    private static final Id3 NONE = Id3.builder().build();
    private static final byte[] V2_MAGIC = {'I', 'D', '3'};
    private static final byte[] V1_MAGIC = {'T', 'A', 'G'};
    private static final int V2_HEADER = 10;
    private static final int V1_LENGTH = 128;
    private static final int V1_FIELD = 30;
    private static final int SYNCHSAFE_BITS = 7;
    private static final int SYNCHSAFE_MASK = 0x7F;
    private static final int UNSYNCHRONISED_SIZE_VERSION = 4;
    private static final String TITLE = "TIT2";
    private static final String ARTIST = "TPE1";
    private static final String ALBUM = "TALB";
    private static final String YEAR = "TDRC";
    private static final String YEAR_V3 = "TYER";
    private static final Charset[] ENCODINGS = {
        StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16, StandardCharsets.UTF_16BE, StandardCharsets.UTF_8};

    public Id3 {
        title = trimmed(title);
        artist = trimmed(artist);
        album = trimmed(album);
        year = trimmed(year);
    }

    public static Id3 of(byte[] file) {
        final Id3 second = version2(file);
        final Id3 first = version1(file);
        return Id3.builder()
                .title(either(second.title(), first.title()))
                .artist(either(second.artist(), first.artist()))
                .album(either(second.album(), first.album()))
                .year(either(second.year(), first.year()))
                .build();
    }

    /**
     * Where the audio starts, which is past an ID3v2 tag when the file opens with one.
     */
    public static int audioStart(byte[] file) {
        return hasVersion2(file) ? V2_HEADER + synchsafe(file, 6) : 0;
    }

    public boolean isEmpty() {
        return equals(NONE);
    }

    private static Id3 version1(byte[] file) {
        final int at = file.length - V1_LENGTH;
        if (at < 0 || !matches(file, at, V1_MAGIC)) {
            return NONE;
        }
        return Id3.builder()
                .title(latin(file, at + 3, V1_FIELD))
                .artist(latin(file, at + 33, V1_FIELD))
                .album(latin(file, at + 63, V1_FIELD))
                .year(latin(file, at + 93, 4))
                .build();
    }

    /**
     * Only the handful of text frames the player shows are read; every other frame is stepped over by its size.
     */
    private static Id3 version2(byte[] file) {
        if (!hasVersion2(file)) {
            return NONE;
        }
        final int version = file[3] & 0xFF;
        final int end = Math.min(file.length, V2_HEADER + synchsafe(file, 6));
        final Id3Builder tags = Id3.builder();
        int at = V2_HEADER;
        while (at + V2_HEADER <= end) {
            final String frame = new String(file, at, 4, StandardCharsets.ISO_8859_1);
            final int size = version >= UNSYNCHRONISED_SIZE_VERSION ? synchsafe(file, at + 4) : big(file, at + 4);
            if (frame.isBlank() || size <= 0 || at + V2_HEADER + size > end) {
                break;
            }
            final String text = text(file, at + V2_HEADER, size);
            switch (frame) {
                case TITLE -> tags.title(text);
                case ARTIST -> tags.artist(text);
                case ALBUM -> tags.album(text);
                case YEAR, YEAR_V3 -> tags.year(text);
                default -> { }
            }
            at += V2_HEADER + size;
        }
        return tags.build();
    }

    /**
     * A text frame opens with the byte naming the encoding of the rest.
     */
    private static String text(byte[] file, int at, int size) {
        final int encoding = file[at] & 0xFF;
        final Charset charset = encoding < ENCODINGS.length ? ENCODINGS[encoding] : StandardCharsets.ISO_8859_1;
        return new String(file, at + 1, size - 1, charset).replace("\0", "");
    }

    private static boolean hasVersion2(byte[] file) {
        return file.length >= V2_HEADER && matches(file, 0, V2_MAGIC);
    }

    private static boolean matches(byte[] file, int at, byte[] magic) {
        for (int i = 0; i < magic.length; i++) {
            if (file[at + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static int synchsafe(byte[] file, int at) {
        int size = 0;
        for (int i = 0; i < 4; i++) {
            size = size << SYNCHSAFE_BITS | file[at + i] & SYNCHSAFE_MASK;
        }
        return size;
    }

    private static int big(byte[] file, int at) {
        int size = 0;
        for (int i = 0; i < 4; i++) {
            size = size << Byte.SIZE | file[at + i] & 0xFF;
        }
        return size;
    }

    private static String latin(byte[] file, int at, int length) {
        return new String(file, at, length, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    private static String either(String preferred, String fallback) {
        return preferred.isBlank() ? fallback : preferred;
    }

    private static String trimmed(String value) {
        return Optional.ofNullable(value).map(String::trim).orElse("");
    }
}

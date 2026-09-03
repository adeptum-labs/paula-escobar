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

package com.adeptum.paula.module.ogg;

import de.quippy.ogg.jorbis.Comment;
import java.util.Optional;

/**
 * The title, artist, album and date a Vorbis comment header carries. Every field is free-form and any of them
 * may be missing, which an encoder writing nothing but a vendor string, or no comment header at all, leaves
 * them all.
 */
public record OggTags(String title, String artist, String album, String date) {

    private static final String TITLE = "TITLE";
    private static final String ARTIST = "ARTIST";
    private static final String ALBUM = "ALBUM";
    private static final String DATE = "DATE";

    public OggTags {
        title = trimmed(title);
        artist = trimmed(artist);
        album = trimmed(album);
        date = trimmed(date);
    }

    static OggTags of(Comment comment) {
        return new OggTags(query(comment, TITLE), query(comment, ARTIST), query(comment, ALBUM),
                query(comment, DATE));
    }

    private static String query(Comment comment, String tag) {
        return comment == null ? "" : trimmed(comment.query(tag));
    }

    private static String trimmed(String value) {
        return Optional.ofNullable(value).map(String::trim).orElse("");
    }
}

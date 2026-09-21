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

package com.adeptum.paula.favourites;

import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.LocalTrack;
import com.adeptum.paula.playlist.ModArchiveTrack;
import com.adeptum.paula.playlist.MusicianTrack;
import com.adeptum.paula.playlist.Track;

/**
 * What makes a track the same song as another, for favouriting: a Demozoo production id, a ModArchive
 * module id or a local file's absolute path, never the record's own {@code equals}. A {@code DemozooTrack}
 * carries the whole competition it was placed in, so a party reloaded between two lookups yields an
 * unequal but identical track; a track read back from storage would never equal the one browsed either.
 * A musician's work and the same production met in a competition share the Demozoo namespace, since both
 * name the one song.
 */
public final class FavouriteKey {

    private static final String DEMOZOO = "demozoo:";
    private static final String MODARCHIVE = "modarchive:";
    private static final String FILE = "file:";

    private FavouriteKey() {
    }

    public static String of(Track track) {
        return switch (track) {
            case DemozooTrack remote -> DEMOZOO + remote.entry().productionId();
            case MusicianTrack work -> DEMOZOO + work.work().entry().productionId();
            case ModArchiveTrack module -> MODARCHIVE + module.entry().moduleId();
            case LocalTrack local -> FILE + local.path().toAbsolutePath().normalize();
        };
    }
}

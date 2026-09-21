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

import com.adeptum.paula.playlist.Track;
import java.io.IOException;
import java.util.List;

/**
 * The songs a user has kept, sorted by title. Toggling a favourite writes it back at once, so a favourite
 * survives however the player is closed.
 */
public interface Favourites {

    Favourites NONE = new Favourites() {

        @Override
        public List<Track> tracks() {
            return List.of();
        }

        @Override
        public boolean holds(Track track) {
            return false;
        }

        @Override
        public boolean toggle(Track track) {
            return false;
        }
    };

    List<Track> tracks();

    boolean holds(Track track);

    /**
     * Adds the track if it was not already kept, or drops it if it was. Returns whether it is now kept.
     */
    boolean toggle(Track track) throws IOException;
}

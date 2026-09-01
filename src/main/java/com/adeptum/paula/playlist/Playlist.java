/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

package com.adeptum.paula.playlist;

import java.util.List;

public final class Playlist {

    private final List<Track> entries;
    private int index;

    public Playlist(List<Track> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("A playlist needs at least one entry");
        }
        this.entries = List.copyOf(entries);
    }

    public Track current() {
        return entries.get(index);
    }

    public int position() {
        return index + 1;
    }

    public int size() {
        return entries.size();
    }

    public boolean hasNext() {
        return index < entries.size() - 1;
    }

    public boolean hasPrevious() {
        return index > 0;
    }

    public boolean next() {
        return step(hasNext(), 1);
    }

    public boolean previous() {
        return step(hasPrevious(), -1);
    }

    private boolean step(boolean possible, int delta) {
        if (possible) {
            index += delta;
        }
        return possible;
    }
}

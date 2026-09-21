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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Favourites kept as JSON under the data directory, read once and held sorted by title; a file that is
 * missing or cannot be parsed is silently treated as empty, since it is easier to lose the list than to
 * greet an empty install with an error.
 */
@Slf4j
public final class JsonFavourites implements Favourites {

    private static final String FILE = "favourites.json";
    private static final Comparator<Track> BY_TITLE = Comparator.comparing(track -> track.label().toLowerCase(Locale.ROOT));

    private final DataDirectory data;
    private final List<Track> tracks;
    private final Set<String> keys;

    public JsonFavourites(DataDirectory data) {
        this.data = data;
        this.tracks = new ArrayList<>(read());
        this.keys = new HashSet<>(tracks.stream().map(FavouriteKey::of).toList());
    }

    @Override
    public List<Track> tracks() {
        return List.copyOf(tracks);
    }

    @Override
    public boolean holds(Track track) {
        return keys.contains(FavouriteKey.of(track));
    }

    @Override
    public boolean toggle(Track track) throws IOException {
        final String key = FavouriteKey.of(track);
        final boolean added = keys.add(key);
        if (added) {
            tracks.add(track);
            tracks.sort(BY_TITLE);
        } else {
            keys.remove(key);
            tracks.removeIf(kept -> FavouriteKey.of(kept).equals(key));
        }
        write();
        return added;
    }

    private List<Track> read() {
        try {
            final Path file = data.file(FILE);
            return Files.exists(file) ? FavouritesJson.read(Files.readAllBytes(file)) : List.of();
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read favourites: {}", e.getMessage());
            return List.of();
        }
    }

    private void write() throws IOException {
        data.writeAtomically(data.file(FILE), FavouritesJson.write(tracks));
    }
}

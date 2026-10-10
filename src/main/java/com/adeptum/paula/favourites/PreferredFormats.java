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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which file of a release a user wants played when it offers several versions of the same tune, kept per
 * production. The file is named by its path inside the release's download directory.
 */
public interface PreferredFormats {

    Optional<String> of(String key);

    void set(String key, String file) throws IOException;

    /**
     * For a session that keeps nothing on disk, and for tests.
     */
    static PreferredFormats inMemory() {
        final Map<String, String> choices = new ConcurrentHashMap<>();
        return new PreferredFormats() {

            @Override
            public Optional<String> of(String key) {
                return Optional.ofNullable(choices.get(key));
            }

            @Override
            public void set(String key, String file) {
                choices.put(key, file);
            }
        };
    }
}

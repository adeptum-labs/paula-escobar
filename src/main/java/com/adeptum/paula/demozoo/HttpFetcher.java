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

package com.adeptum.paula.demozoo;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/**
 * Fetches a whole resource; the file name is the one the server suggested, if any.
 */
public interface HttpFetcher {

    record Response(byte[] body, Optional<String> fileName) {
    }

    /**
     * Told how much of a body has arrived and how much was promised, or nothing promised where the server
     * would not say.
     */
    @FunctionalInterface
    interface Watcher {

        Watcher NONE = (read, total) -> { };

        void read(long bytes, long total);
    }

    Response get(URI uri) throws IOException;

    /**
     * The same, with someone watching the body arrive; a fetcher that cannot say is free to ignore it.
     */
    default Response get(URI uri, Watcher watcher) throws IOException {
        return get(uri);
    }
}

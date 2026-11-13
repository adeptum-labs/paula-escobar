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
import java.nio.file.Path;
import java.util.Optional;

/**
 * Fetches a whole resource; the file name is the one the server suggested, if any.
 */
public interface HttpFetcher {

    record Response(byte[] body, Optional<String> fileName) {
    }

    /**
     * What a server says of a body without sending any of it: the handle it knows the body by and the date it was
     * last written, where the server keeps them, and how long the body is; the length is -1 where it did not say.
     */
    record Head(Optional<String> etag, Optional<String> lastModified, long length) {
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

    /**
     * Asks after the body without fetching any of it, which tells whether what was fetched before has changed.
     */
    Head head(URI uri) throws IOException;

    /**
     * Fetches the body straight into the given file block by block, since an export of a couple of hundred
     * megabytes is not to be held in memory; someone watching is told how much of it has arrived.
     */
    void download(URI uri, Path target, Watcher watcher) throws IOException;
}

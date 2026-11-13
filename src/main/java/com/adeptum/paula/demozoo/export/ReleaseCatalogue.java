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
package com.adeptum.paula.demozoo.export;

import com.adeptum.paula.demozoo.HttpFetcher;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * The catalogue published with Paula's latest release, rebuilt from Demozoo's export every week. Its checksum
 * is a line of text beside it, so finding out whether it changed costs a hundred bytes rather than eight
 * megabytes.
 */
final class ReleaseCatalogue {

    static final URI LATEST = URI.create("https://github.com/adeptum-labs/paula-escobar/releases/latest/download/");

    private final HttpFetcher http;
    private final URI base;

    /**
     * A catalogue as it came over the wire: its bytes, to be cached as they are, and what was read from them.
     */
    record Fetched(byte[] bytes, String sha256, MusicCatalogue catalogue) {
    }

    ReleaseCatalogue(HttpFetcher http, URI base) {
        this.http = http;
        this.base = base;
    }

    /**
     * The checksum of the catalogue now published.
     */
    String publishedChecksum() throws IOException {
        return Checksums.parse(new String(http.get(base.resolve(CatalogueFormat.checksumName())).body(), StandardCharsets.US_ASCII));
    }

    /**
     * Fetches the published catalogue and takes it only when its bytes match the checksum and read as a
     * catalogue; a half-uploaded or damaged file is refused rather than cached.
     */
    Fetched fetch(String sha256, HttpFetcher.Watcher watcher) throws IOException {
        final byte[] bytes = http.get(base.resolve(CatalogueFormat.fileName()), watcher).body();
        final String actual = Checksums.sha256(bytes);
        if (!actual.equals(sha256)) {
            throw new IOException("The published Demozoo catalogue does not match its checksum");
        }
        return new Fetched(bytes, actual, CatalogueFormat.read(new ByteArrayInputStream(bytes)));
    }
}

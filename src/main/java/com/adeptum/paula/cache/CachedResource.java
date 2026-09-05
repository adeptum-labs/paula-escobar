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

package com.adeptum.paula.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * A file kept in the cache and refreshed from its source once it has aged. A copy is only written once it has
 * parsed, one that no longer parses is thrown away so it cannot block the source until it expires, and one
 * that cannot be refreshed is served stale rather than not at all.
 */
@Slf4j
public final class CachedResource {

    public interface Fetch {
        byte[] fetch() throws IOException;
    }

    public interface Parser<T> {
        T parse(byte[] body) throws IOException;
    }

    private final CacheDirectory cache;
    private final Duration ttl;
    private final Clock clock;

    public CachedResource(CacheDirectory cache, Duration ttl, Clock clock) {
        this.cache = cache;
        this.ttl = ttl;
        this.clock = clock;
    }

    public <T> T read(Path file, Fetch fetch, Parser<T> parser) throws IOException {
        if (isFresh(file)) {
            try {
                return parser.parse(Files.readAllBytes(file));
            } catch (IOException e) {
                log.warn("Discarding unreadable cache file {}: {}", file, e.getMessage());
                Files.deleteIfExists(file);
            }
        }
        try {
            final byte[] body = fetch.fetch();
            final T parsed = parser.parse(body);
            Files.createDirectories(file.getParent());
            cache.writeAtomically(file, body);
            return parsed;
        } catch (IOException e) {
            if (!Files.exists(file)) {
                throw e;
            }
            log.warn("Using the cached copy of {} after a failed fetch: {}", file.getFileName(), e.getMessage());
            return parser.parse(Files.readAllBytes(file));
        }
    }

    private boolean isFresh(Path file) throws IOException {
        return Files.exists(file) && Files.getLastModifiedTime(file).toInstant().plus(ttl).isAfter(clock.instant());
    }
}

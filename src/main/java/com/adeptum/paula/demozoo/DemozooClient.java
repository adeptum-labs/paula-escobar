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

package com.adeptum.paula.demozoo;

import com.adeptum.paula.cache.CacheDirectory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the Demozoo API through an on-disk cache; a cached copy is refreshed after the time to live but still
 * served when the network is down.
 */
@Slf4j
public final class DemozooClient {

    private static final URI API = URI.create("https://demozoo.org/api/v1/");
    private static final String JSON_FORMAT = "/?format=json";
    private static final String JSON_SUFFIX = ".json";
    private static final String CACHE_SEGMENT = "api";
    private static final String SERIES = "party_series";
    private static final String PARTIES = "parties";
    private static final String PRODUCTIONS = "productions";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final HttpFetcher http;
    private final CacheDirectory cache;
    private final Duration ttl;
    private final Clock clock;

    public DemozooClient(HttpFetcher http, CacheDirectory cache) {
        this(http, cache, DEFAULT_TTL, Clock.systemUTC());
    }

    DemozooClient(HttpFetcher http, CacheDirectory cache, Duration ttl, Clock clock) {
        this.http = http;
        this.cache = cache;
        this.ttl = ttl;
        this.clock = clock;
    }

    public PartySeries series(int id) throws IOException {
        return DemozooJson.series(fetch(SERIES, id));
    }

    public List<Competition> competitions(int partyId) throws IOException {
        return DemozooJson.competitions(fetch(PARTIES, partyId));
    }

    public Production production(int id) throws IOException {
        return DemozooJson.production(fetch(PRODUCTIONS, id));
    }

    private byte[] fetch(String resource, int id) throws IOException {
        final Path cached = cache.file(CACHE_SEGMENT, resource, id + JSON_SUFFIX);
        if (isFresh(cached)) {
            return Files.readAllBytes(cached);
        }
        try {
            final byte[] body = http.get(API.resolve(resource + "/" + id + JSON_FORMAT)).body();
            cache.writeAtomically(cached, body);
            return body;
        } catch (IOException e) {
            if (!Files.exists(cached)) {
                throw e;
            }
            log.warn("Using cached {} {} after a failed fetch: {}", resource, id, e.getMessage());
            return Files.readAllBytes(cached);
        }
    }

    private boolean isFresh(Path file) throws IOException {
        return Files.exists(file) && Files.getLastModifiedTime(file).toInstant().plus(ttl).isAfter(clock.instant());
    }
}

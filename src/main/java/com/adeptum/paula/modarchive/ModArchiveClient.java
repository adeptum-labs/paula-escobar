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

package com.adeptum.paula.modarchive;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.cache.CachedResource;
import com.adeptum.paula.demozoo.HttpFetcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches the chart pages of modarchive.org one at a time and keeps them for a day, since a chart moves
 * slowly and the site is a volunteer's.
 */
@Slf4j
public final class ModArchiveClient {

    private static final String CACHE_SEGMENT = "modarchive";
    private static final String PAGE_SUFFIX = ".html";
    private static final Duration DEFAULT_TTL = Duration.ofDays(1);

    private final HttpFetcher http;
    private final CacheDirectory cache;
    private final CachedResource pages;

    public ModArchiveClient(HttpFetcher http, CacheDirectory cache) {
        this(http, cache, DEFAULT_TTL, Clock.systemUTC());
    }

    ModArchiveClient(HttpFetcher http, CacheDirectory cache, Duration ttl, Clock clock) {
        this.http = http;
        this.cache = cache;
        this.pages = new CachedResource(cache, ttl, clock);
    }

    public ChartPage chart(Chart chart, int page) throws IOException {
        return pages.read(cache.file(CACHE_SEGMENT, chart.id(), page + PAGE_SUFFIX),
                () -> http.get(chart.page(page)).body(), body -> ModArchiveHtml.parse(chart, page, body));
    }

    /**
     * Drops every page kept for a chart, so it is read off the site again from its first page.
     */
    public void forget(Chart chart) {
        final Path directory = cache.root().resolve(CACHE_SEGMENT).resolve(chart.id());
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            for (final Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            log.warn("Could not forget the {} chart: {}", chart.title(), e.getMessage());
        }
    }
}

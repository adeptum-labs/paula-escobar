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

package com.adeptum.paula.module.sid;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.demozoo.HttpFetcher;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Song lengths from the High Voltage SID Collection, keyed by the MD5 of the whole SID file. The database is
 * fetched into the cache on first use and parsed once; tunes it does not know play for a default length.
 */
@Slf4j
public final class SongLengths {

    public static final Duration DEFAULT_LENGTH = Duration.ofMinutes(3);

    private static final URI DATABASE = URI.create("https://hvsc.c64.org/download/C64Music/DOCUMENTS/Songlengths.md5");
    private static final String CACHE_SEGMENT = "hvsc";
    private static final String FILE_NAME = "Songlengths.md5";
    private static final Duration DEFAULT_TTL = Duration.ofDays(30);
    private static final Pattern ENTRY = Pattern.compile("^([0-9a-fA-F]{32})=(.*)$");
    private static final Pattern TIME = Pattern.compile("(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?");
    private static final String MD5 = "MD5";

    private final Supplier<Map<String, List<Duration>>> loader;
    private Map<String, List<Duration>> database;

    private SongLengths(Supplier<Map<String, List<Duration>>> loader) {
        this.loader = loader;
    }

    public SongLengths(HttpFetcher http, CacheDirectory cache) {
        this(http, cache, DEFAULT_TTL, Clock.systemUTC());
    }

    SongLengths(HttpFetcher http, CacheDirectory cache, Duration ttl, Clock clock) {
        this(() -> load(http, cache, ttl, clock));
    }

    /**
     * For commands that only inspect files and must never touch the network.
     */
    public static SongLengths none() {
        return new SongLengths(Map::of);
    }

    public Duration lengthOf(byte[] sidFile, int subtune) {
        final List<Duration> durations = database().getOrDefault(md5(sidFile), List.of());
        return subtune >= 1 && subtune <= durations.size() ? durations.get(subtune - 1) : DEFAULT_LENGTH;
    }

    static Map<String, List<Duration>> parse(String database) {
        final Map<String, List<Duration>> lengths = new HashMap<>();
        database.lines().map(ENTRY::matcher).filter(Matcher::matches).forEach(entry -> {
            final List<Duration> durations = durations(entry.group(2));
            if (!durations.isEmpty()) {
                lengths.put(entry.group(1).toLowerCase(), durations);
            }
        });
        return lengths;
    }

    private static List<Duration> durations(String times) {
        return TIME.matcher(times).results()
                .map(time -> Duration.ofMinutes(Long.parseLong(time.group(1)))
                        .plusSeconds(Long.parseLong(time.group(2)))
                        .plusMillis(time.group(3) == null ? 0 : Long.parseLong((time.group(3) + "00").substring(0, 3))))
                .toList();
    }

    private synchronized Map<String, List<Duration>> database() {
        if (database == null) {
            database = loader.get();
        }
        return database;
    }

    private static Map<String, List<Duration>> load(HttpFetcher http, CacheDirectory cache, Duration ttl, Clock clock) {
        try {
            return parse(new String(databaseText(http, cache, ttl, clock), StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            log.warn("No HVSC song length database, using the default length: {}", e.getMessage());
            return Map.of();
        }
    }

    private static byte[] databaseText(HttpFetcher http, CacheDirectory cache, Duration ttl, Clock clock) throws IOException {
        final Path cached = cache.file(CACHE_SEGMENT, FILE_NAME);
        final boolean fresh = Files.exists(cached) && Files.getLastModifiedTime(cached).toInstant().plus(ttl).isAfter(clock.instant());
        if (fresh) {
            return Files.readAllBytes(cached);
        }
        try {
            final byte[] body = http.get(DATABASE).body();
            cache.writeAtomically(cached, body);
            return body;
        } catch (IOException e) {
            if (!Files.exists(cached)) {
                throw e;
            }
            log.warn("Using the cached HVSC song lengths after a failed fetch: {}", e.getMessage());
            return Files.readAllBytes(cached);
        }
    }

    private static String md5(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(MD5).digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is part of every Java runtime", e);
        }
    }
}

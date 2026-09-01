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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemozooClientTest {

    private static final String SERIES_URL = "https://demozoo.org/api/v1/party_series/2/?format=json";
    private static final String PARTY_URL = "https://demozoo.org/api/v1/parties/103/?format=json";
    private static final String PRODUCTION_URL = "https://demozoo.org/api/v1/productions/7/?format=json";
    private static final Duration TTL = Duration.ofDays(7);

    private final FakeHttp http = new FakeHttp();
    private Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);

    private DemozooClient client(Path dir) {
        return new DemozooClient(http, new CacheDirectory(dir), TTL, clock);
    }

    @Test
    void fetchesEveryResourceKind(@TempDir Path dir) throws IOException {
        http.put(SERIES_URL, DemozooJsonTest.SERIES);
        http.put(PARTY_URL, DemozooJsonTest.PARTY);
        http.put(PRODUCTION_URL, DemozooJsonTest.PRODUCTION);
        assertEquals("Assembly", client(dir).series(2).name());
        assertEquals("4 Channel Music", client(dir).competitions(103).get(0).name());
        assertEquals("Funkyeeh", client(dir).production(7).title());
    }

    @Test
    void servesFromCacheAfterTheFirstFetch(@TempDir Path dir) throws IOException {
        http.put(SERIES_URL, DemozooJsonTest.SERIES);
        client(dir).series(2);
        client(dir).series(2);
        assertEquals(1, http.requests());
        assertTrue(Files.exists(dir.resolve("api/party_series/2.json")));
    }

    @Test
    void refetchesAfterTheTtl(@TempDir Path dir) throws IOException {
        http.put(SERIES_URL, DemozooJsonTest.SERIES);
        client(dir).series(2);
        clock = Clock.offset(clock, TTL.plusDays(1));
        client(dir).series(2);
        assertEquals(2, http.requests());
    }

    @Test
    void servesStaleCacheWhenTheFetchFails(@TempDir Path dir) throws IOException {
        http.put(SERIES_URL, DemozooJsonTest.SERIES);
        client(dir).series(2);
        clock = Clock.offset(clock, TTL.plusDays(1));
        http.goOffline();
        assertEquals("Assembly", client(dir).series(2).name());
    }

    @Test
    void discardsAndRefetchesAnUnreadableCacheFile(@TempDir Path dir) throws IOException {
        http.put(SERIES_URL, DemozooJsonTest.SERIES);
        client(dir).series(2);
        Files.writeString(dir.resolve("api/party_series/2.json"), "{");

        assertEquals("Assembly", client(dir).series(2).name());
        assertEquals(2, http.requests());
        assertEquals(DemozooJsonTest.SERIES, Files.readString(dir.resolve("api/party_series/2.json")));
    }

    @Test
    void doesNotCacheAMalformedResponse(@TempDir Path dir) {
        http.put(SERIES_URL, "{");
        assertThrows(IOException.class, () -> client(dir).series(2));
        assertFalse(Files.exists(dir.resolve("api/party_series/2.json")));
    }

    @Test
    void failsWithoutCacheWhenOffline(@TempDir Path dir) {
        http.goOffline();
        assertThrows(IOException.class, () -> client(dir).series(2));
        assertFalse(Files.exists(dir.resolve("api/party_series/2.json")), "failures are never cached");
    }
}

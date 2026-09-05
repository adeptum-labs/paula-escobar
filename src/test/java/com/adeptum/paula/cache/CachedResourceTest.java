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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachedResourceTest {

    private static final Duration TTL = Duration.ofDays(1);
    private static final Instant NOW = Instant.now();

    private final List<String> fetched = new ArrayList<>();
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private String answer = "fresh";
    private boolean offline;

    private CachedResource resource(Path dir) {
        return new CachedResource(new CacheDirectory(dir), TTL, clock);
    }

    private byte[] fetch() throws IOException {
        fetched.add(answer);
        if (offline) {
            throw new IOException("offline");
        }
        return answer.getBytes(StandardCharsets.UTF_8);
    }

    private static String parse(byte[] body) throws IOException {
        final String text = new String(body, StandardCharsets.UTF_8);
        if (text.startsWith("bad")) {
            throw new IOException("does not parse");
        }
        return text;
    }

    @Test
    void fetchesOnceAndServesTheCopyWhileFresh(@TempDir Path dir) throws IOException {
        final Path file = dir.resolve("a/b.html");
        assertEquals("fresh", resource(dir).read(file, this::fetch, CachedResourceTest::parse));
        assertEquals("fresh", resource(dir).read(file, this::fetch, CachedResourceTest::parse));
        assertEquals(1, fetched.size());
        assertEquals("fresh", Files.readString(file));
    }

    @Test
    void fetchesAgainOnceTheCopyHasAged(@TempDir Path dir) throws IOException {
        final Path file = dir.resolve("b.html");
        resource(dir).read(file, this::fetch, CachedResourceTest::parse);
        clock = Clock.fixed(NOW.plus(TTL).plusSeconds(1), ZoneOffset.UTC);
        answer = "newer";
        assertEquals("newer", resource(dir).read(file, this::fetch, CachedResourceTest::parse));
        assertEquals(2, fetched.size());
    }

    @Test
    void servesTheStaleCopyWhenTheFetchFails(@TempDir Path dir) throws IOException {
        final Path file = dir.resolve("c.html");
        resource(dir).read(file, this::fetch, CachedResourceTest::parse);
        clock = Clock.fixed(NOW.plus(TTL).plusSeconds(1), ZoneOffset.UTC);
        offline = true;
        assertEquals("fresh", resource(dir).read(file, this::fetch, CachedResourceTest::parse));
    }

    @Test
    void failsWhenThereIsNoCopyToFallBackOn(@TempDir Path dir) {
        offline = true;
        assertThrows(IOException.class, () -> resource(dir).read(dir.resolve("d.html"), this::fetch, CachedResourceTest::parse));
    }

    @Test
    void throwsAwayACopyThatNoLongerParses(@TempDir Path dir) throws IOException {
        final Path file = dir.resolve("e.html");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "bad copy");
        assertEquals("fresh", resource(dir).read(file, this::fetch, CachedResourceTest::parse));
        assertEquals("fresh", Files.readString(file));
    }

    @Test
    void keepsNothingThatDoesNotParse(@TempDir Path dir) {
        final Path file = dir.resolve("f.html");
        answer = "bad answer";
        assertThrows(IOException.class, () -> resource(dir).read(file, this::fetch, CachedResourceTest::parse));
        assertFalse(Files.exists(file));
    }
}

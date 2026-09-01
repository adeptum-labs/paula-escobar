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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.demozoo.FakeHttp;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongLengthsTest {

    private static final String DATABASE_URL = "https://hvsc.c64.org/download/C64Music/DOCUMENTS/Songlengths.md5";
    private static final byte[] TUNE = "hello sid".getBytes(StandardCharsets.US_ASCII);
    private static final String TUNE_MD5 = "92ad032d6c987442c880610ec41b8674";
    private static final String DATABASE = "[Database]\r\n"
            + "; /DEMOS/0-9/10_Orbyte.sid\r\n"
            + "5f08a730b280e54fd1e75a7046b93fdc=1:17\r\n"
            + "; /MUSICIANS/H/Hello.sid\r\n"
            + TUNE_MD5 + "=3:02 0:45.500 1:00(G)\r\n"
            + "garbage line without a hash\r\n"
            + "abcdef=not:a:time\r\n";
    private static final Duration TTL = Duration.ofDays(30);

    private final FakeHttp http = new FakeHttp();
    private Clock clock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);

    private SongLengths lengths(Path dir) {
        return new SongLengths(http, new CacheDirectory(dir), TTL, clock);
    }

    @Test
    void parsesDurationsPerSubtuneAndSkipsWhatItCannotRead() {
        final Map<String, List<Duration>> parsed = SongLengths.parse(DATABASE);
        assertEquals(List.of(Duration.ofSeconds(77)), parsed.get("5f08a730b280e54fd1e75a7046b93fdc"));
        assertEquals(List.of(Duration.ofSeconds(182), Duration.ofMillis(45500), Duration.ofSeconds(60)), parsed.get(TUNE_MD5));
        assertEquals(2, parsed.size());
    }

    @Test
    void looksUpTheWholeFileMd5(@TempDir Path dir) {
        http.put(DATABASE_URL, DATABASE);
        assertEquals(Duration.ofSeconds(182), lengths(dir).lengthOf(TUNE, 1));
        assertEquals(Duration.ofMillis(45500), lengths(dir).lengthOf(TUNE, 2));
    }

    @Test
    void unknownTunesAndSubtunesGetTheDefault(@TempDir Path dir) {
        http.put(DATABASE_URL, DATABASE);
        assertEquals(SongLengths.DEFAULT_LENGTH, lengths(dir).lengthOf("unknown".getBytes(StandardCharsets.US_ASCII), 1));
        assertEquals(SongLengths.DEFAULT_LENGTH, lengths(dir).lengthOf(TUNE, 4));
        assertEquals(SongLengths.DEFAULT_LENGTH, SongLengths.none().lengthOf(TUNE, 1));
    }

    @Test
    void downloadsOnceThenReadsTheCache(@TempDir Path dir) throws IOException {
        http.put(DATABASE_URL, DATABASE);
        lengths(dir).lengthOf(TUNE, 1);
        lengths(dir).lengthOf(TUNE, 1);
        assertEquals(1, http.requests());
        assertTrue(Files.exists(dir.resolve("hvsc/Songlengths.md5")));
    }

    @Test
    void refreshesAfterTheTtlAndServesAStaleCopyOffline(@TempDir Path dir) {
        http.put(DATABASE_URL, DATABASE);
        lengths(dir).lengthOf(TUNE, 1);
        clock = Clock.offset(clock, TTL.plusDays(1));
        http.goOffline();
        assertEquals(Duration.ofSeconds(182), lengths(dir).lengthOf(TUNE, 1));
        assertEquals(2, http.requests());
    }

    @Test
    void offlineWithoutACopyGivesTheDefault(@TempDir Path dir) {
        http.goOffline();
        assertEquals(SongLengths.DEFAULT_LENGTH, lengths(dir).lengthOf(TUNE, 1));
    }

    @Test
    void retriesAFailedLoadAfterABackoff(@TempDir Path dir) {
        final SteppingClock stepping = new SteppingClock(clock.instant());
        final SongLengths lengths = new SongLengths(http, new CacheDirectory(dir), TTL, stepping);
        http.goOffline();
        assertEquals(SongLengths.DEFAULT_LENGTH, lengths.lengthOf(TUNE, 1));

        http.goOnline();
        http.put(DATABASE_URL, DATABASE);
        assertEquals(SongLengths.DEFAULT_LENGTH, lengths.lengthOf(TUNE, 1), "no retry storm right after a failure");
        assertEquals(1, http.requests());

        stepping.advance(SongLengths.RETRY_BACKOFF.plusSeconds(1));
        assertEquals(Duration.ofSeconds(182), lengths.lengthOf(TUNE, 1));
        assertEquals(2, http.requests());
    }

    @Test
    void primingLoadsTheDatabaseOnce(@TempDir Path dir) {
        http.put(DATABASE_URL, DATABASE);
        final SongLengths lengths = lengths(dir);
        lengths.prime();
        lengths.prime();
        assertEquals(Duration.ofSeconds(182), lengths.lengthOf(TUNE, 1));
        assertEquals(1, http.requests());
    }

    private static final class SteppingClock extends Clock {

        private Instant now;

        private SteppingClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration delta) {
            now = now.plus(delta);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

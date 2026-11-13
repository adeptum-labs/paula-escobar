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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.FakeHttp;
import com.adeptum.paula.demozoo.Link;
import com.adeptum.paula.demozoo.Nick;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.PartySeries;
import com.adeptum.paula.demozoo.Production;
import com.adeptum.paula.demozoo.Work;
import com.adeptum.paula.playback.Progress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemozooExportTest {

    private static final String URL = DemozooExport.EXPORT.toString();
    private static final String ETAG = "\"v1\"";
    private static final String NEW_ETAG = "\"v2\"";
    private static final String LAST_MODIFIED = "Wed, 12 Nov 2025 00:00:00 GMT";
    private static final Duration MORE_THAN_A_WEEK = Duration.ofDays(8);

    private final FakeHttp http = new FakeHttp();
    private final MutableClock clock = new MutableClock();

    @Test
    void firstUseFetchesConvertsAndCachesTheCatalogue(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));

        assertEquals(converter.catalogue(), export(dir, converter).catalogue());
        assertEquals(1, converter.calls());
        assertEquals(2, http.requests(), "one HEAD and one download");
        assertTrue(Files.exists(dir.resolve("demozoo/catalogue.bin.gz")));
        assertTrue(Files.exists(dir.resolve("demozoo/catalogue.sha256")));
        assertTrue(Files.exists(dir.resolve("demozoo/export.properties")));
        assertFalse(Files.exists(dir.resolve("demozoo/export.sql.gz.part")), "the download is gone once converted");
    }

    @Test
    void aSecondExportLoadsTheCachedCatalogueWithoutARequest(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final MusicCatalogue loaded = export(dir, new StubConverter(catalogue("Assembly 1995"))).catalogue();

        final StubConverter second = new StubConverter(catalogue("Assembly 1998"));
        assertEquals(loaded, export(dir, second).catalogue());
        assertEquals(2, http.requests(), "nothing is asked of the network for a catalogue already cached");
        assertEquals(0, second.calls(), "nothing is converted again");
    }

    @Test
    void aCorruptedChecksumLeadsToAFreshConversion(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        export(dir, new StubConverter(catalogue("Assembly 1995"))).catalogue();
        Files.writeString(dir.resolve("demozoo/catalogue.sha256"), "not the checksum");

        final StubConverter converter = new StubConverter(catalogue("Assembly 1998"));
        assertEquals(converter.catalogue(), export(dir, converter).catalogue());
        assertEquals(1, converter.calls());
        assertEquals(4, http.requests(), "the export is fetched again");
    }

    @Test
    void looksForANewExportAfterAWeekWithOneHeadWhenTheEtagIsTheSame(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));
        final DemozooExport export = export(dir, converter);
        final MusicCatalogue loaded = export.catalogue();

        clock.advance(MORE_THAN_A_WEEK);

        assertEquals(loaded, export.catalogue());
        assertEquals(3, http.requests(), "the weekly look is a HEAD alone");
        assertEquals(1, converter.calls(), "an unchanged export is not converted again");
    }

    @Test
    void fetchesAndConvertsANewExportBehindTheOldOne(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"), catalogue("Assembly 1998"));
        final DemozooExport export = export(dir, converter);
        final MusicCatalogue old = export.catalogue();

        serve(NEW_ETAG, "export two");
        clock.advance(MORE_THAN_A_WEEK);

        assertEquals(old, export.catalogue(), "the old catalogue is served while the new one is converted");
        assertEquals(2, converter.calls());
        assertEquals(4, http.requests(), "a new ETag means a HEAD and a download");
        assertEquals(converter.latest(), export.catalogue(), "the next look sees the new catalogue");
        assertEquals(4, http.requests(), "within the week it is served without another look");
    }

    @Test
    void keepsTheOldCatalogueWhenTheWeeklyLookFindsTheNetworkDown(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));
        final DemozooExport export = export(dir, converter);
        final MusicCatalogue loaded = export.catalogue();

        http.goOffline();
        clock.advance(MORE_THAN_A_WEEK);

        assertEquals(loaded, export.catalogue());
        assertEquals(1, converter.calls());
    }

    @Test
    void checkSoonAsksForTheExportBeforeTheWeekIsUp(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));
        final DemozooExport export = export(dir, converter);
        export.catalogue();

        export.checkSoon();
        export.catalogue();

        assertEquals(3, http.requests(), "one HEAD, as asked for");
        assertEquals(1, converter.calls());
    }

    @Test
    void offlineOnFirstUseIsAnIoException(@TempDir Path dir) {
        http.goOffline();
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));

        assertThrows(IOException.class, () -> export(dir, converter).catalogue());
        assertEquals(0, converter.calls());
    }

    @Test
    void preparingIsEmptyAfterwardsButShowsTheConversionWhileItRuns(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));
        final DemozooExport export = export(dir, converter);
        converter.watch(export);

        export.catalogue();

        assertEquals(Optional.of("Reading the Demozoo database"), converter.preparingDuringConversion().map(Progress.Step::text));
        assertTrue(export.preparing().isEmpty());
    }

    private void serve(String etag, String dump) {
        http.putHead(URL, etag, LAST_MODIFIED);
        http.put(URL, dump);
    }

    private DemozooExport export(Path dir, StubConverter converter) {
        return new DemozooExport(http, new CacheDirectory(dir), clock, Runnable::run, converter);
    }

    private static MusicCatalogue catalogue(String seriesName) {
        final Nick musician = new Nick("Theseus", 5887, false);
        final CompoEntry entry = new CompoEntry(1, "1", 7, "Funkyeeh", List.of(musician), Set.of(29));
        final Competition competition = new Competition(9, "4 Channel Music", 29, "Tracked Music", List.of(entry));
        final PartySeries series = new PartySeries(2, seriesName,
                List.of(new Party(103, "Assembly 1995", "1995-08-10")));
        final Production production = new Production(7, "Funkyeeh",
                List.of(new Link("SceneOrgFile", "https://files.scene.org/view/funky.mod")), List.of(), null);
        return new MusicCatalogue(Map.of(2, series), Map.of(103, List.of(competition)),
                Map.of(103, "https://files.scene.org/browse/parties/1995/asm95/"),
                Map.of(7, production), Map.of(5887, List.of(new Work(entry, "1995", "Amiga"))));
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        private void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final class StubConverter implements DemozooExport.Converter {

        private final List<MusicCatalogue> catalogues;
        private int calls;
        private DemozooExport watched;
        private Optional<Progress.Step> preparingDuringConversion = Optional.empty();

        private StubConverter(MusicCatalogue... catalogues) {
            this.catalogues = List.of(catalogues);
        }

        private void watch(DemozooExport export) {
            watched = export;
        }

        private MusicCatalogue catalogue() {
            return catalogues.get(0);
        }

        private MusicCatalogue latest() {
            return catalogues.get(Math.min(calls, catalogues.size()) - 1);
        }

        private int calls() {
            return calls;
        }

        private Optional<Progress.Step> preparingDuringConversion() {
            return preparingDuringConversion;
        }

        @Override
        public MusicCatalogue convert(Path export, ExportConverter.Watcher watcher) {
            calls++;
            preparingDuringConversion = watched == null ? Optional.empty() : watched.preparing();
            return latest();
        }
    }
}

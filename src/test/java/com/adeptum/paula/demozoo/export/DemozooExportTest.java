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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemozooExportTest {

    private static final String URL = DemozooExport.EXPORT.toString();
    private static final String RELEASES = "https://releases.example/latest/download/";
    private static final String CHECKSUM_URL = RELEASES + CatalogueFormat.checksumName();
    private static final String CATALOGUE_URL = RELEASES + CatalogueFormat.fileName();
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
        assertEquals(3, http.requests(), "a release checksum look, a HEAD and a download");
        assertTrue(Files.exists(dir.resolve("demozoo/catalogue.bin.gz")));
        assertTrue(Files.exists(dir.resolve("demozoo/catalogue.sha256")));
        assertTrue(Files.exists(dir.resolve("demozoo/export.properties")));
        try (Stream<Path> files = Files.list(dir.resolve("demozoo"))) {
            assertFalse(files.anyMatch(file -> file.toString().endsWith(".part")), "the download is gone once converted");
        }
    }

    @Test
    void aSecondExportLoadsTheCachedCatalogueWithoutARequest(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final MusicCatalogue loaded = export(dir, new StubConverter(catalogue("Assembly 1995"))).catalogue();

        final StubConverter second = new StubConverter(catalogue("Assembly 1998"));
        assertEquals(loaded, export(dir, second).catalogue());
        assertEquals(3, http.requests(), "nothing is asked of the network for a catalogue already cached");
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
        assertEquals(6, http.requests(), "a release checksum look, then a HEAD and a download");
    }

    @Test
    void looksForANewExportAfterAWeekWithOneHeadWhenTheEtagIsTheSame(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));
        final DemozooExport export = export(dir, converter);
        final MusicCatalogue loaded = export.catalogue();

        clock.advance(MORE_THAN_A_WEEK);

        assertEquals(loaded, export.catalogue());
        assertEquals(5, http.requests(), "the weekly look is a release checksum look and a HEAD");
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
        assertEquals(6, http.requests(), "a new ETag means a release checksum look, a HEAD and a download");
        assertEquals(converter.latest(), export.catalogue(), "the next look sees the new catalogue");
        assertEquals(6, http.requests(), "within the week it is served without another look");
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
    void aFailedLookIsTriedAgainADayLaterRatherThanAtEveryQuestion(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));
        final DemozooExport export = export(dir, converter);
        final MusicCatalogue loaded = export.catalogue();
        final int fetched = http.requests();

        http.goOffline();
        clock.advance(MORE_THAN_A_WEEK);
        export.catalogue();
        export.catalogue();
        assertEquals(fetched + 2, http.requests(), "the failed look is not repeated at once");

        http.goOnline();
        clock.advance(Duration.ofHours(25));
        assertEquals(loaded, export.catalogue());
        assertEquals(fetched + 4, http.requests(), "a day later it is tried again");
        assertEquals(1, converter.calls());
    }

    @Test
    void checkSoonAsksForTheExportBeforeTheWeekIsUp(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));
        final DemozooExport export = export(dir, converter);
        export.catalogue();

        clock.advance(Duration.ofHours(25));
        export.checkSoon();
        export.catalogue();

        assertEquals(5, http.requests(), "a release checksum look and one HEAD, as asked for");
        assertEquals(1, converter.calls());
    }

    @Test
    void checkSoonIsIgnoredWithinADayOfTheLastLook(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));
        export(dir, converter).catalogue();
        final int fetched = http.requests();

        final DemozooExport restarted = export(dir, converter);
        clock.advance(Duration.ofHours(23));
        restarted.checkSoon();
        restarted.catalogue();
        restarted.checkSoon();
        restarted.catalogue();

        assertEquals(fetched, http.requests(), "the export was looked at less than a day ago, even before a restart");
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

    @Test
    void firstUseTakesTheCataloguePublishedWithTheRelease(@TempDir Path dir) throws IOException {
        final MusicCatalogue published = catalogue("Assembly 2026");
        final String sha256 = publish(published);
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));

        assertEquals(published, export(dir, converter).catalogue());
        assertEquals(2, http.requests(), "a release checksum look and a catalogue fetch");
        assertEquals(0, converter.calls(), "the export is neither downloaded nor converted");
        assertEquals(sha256, Files.readString(dir.resolve("demozoo/catalogue.sha256"), StandardCharsets.US_ASCII));
    }

    @Test
    void aWeekLaterTheSamePublishedChecksumCostsOneRequest(@TempDir Path dir) throws IOException {
        final MusicCatalogue published = catalogue("Assembly 2026");
        publish(published);
        final DemozooExport export = export(dir, new StubConverter(catalogue("Assembly 1995")));
        assertEquals(published, export.catalogue());

        clock.advance(MORE_THAN_A_WEEK);

        assertEquals(published, export.catalogue());
        assertEquals(3, http.requests(), "the weekly look is the release checksum alone");
    }

    @Test
    void aWeekLaterANewPublishedCatalogueIsFetchedBehindTheOldOne(@TempDir Path dir) throws IOException {
        final MusicCatalogue old = catalogue("Assembly 2026");
        publish(old);
        final DemozooExport export = export(dir, new StubConverter(catalogue("Assembly 1995")));
        assertEquals(old, export.catalogue());

        final MusicCatalogue latest = catalogue("Assembly 2027");
        publish(latest);
        clock.advance(MORE_THAN_A_WEEK);

        assertEquals(old, export.catalogue(), "the old catalogue is served while the new one is fetched");
        assertEquals(4, http.requests(), "a new checksum means a checksum look and a catalogue fetch");
        assertEquals(latest, export.catalogue(), "the next look sees the new catalogue");
        assertEquals(4, http.requests(), "within the week it is served without another look");
    }

    @Test
    void aPublishedCatalogueThatDoesNotMatchItsChecksumFallsBackToTheExport(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        final byte[] released = bytesOf(catalogue("Assembly 2026"));
        http.put(CHECKSUM_URL, Checksums.line(Checksums.sha256(released), CatalogueFormat.fileName()));
        http.put(CATALOGUE_URL, "a damaged catalogue".getBytes(StandardCharsets.UTF_8), Optional.empty());
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));

        assertEquals(converter.catalogue(), export(dir, converter).catalogue());
        assertEquals(1, converter.calls(), "the export is converted once");
        assertEquals(4, http.requests(), "a checksum look, a catalogue fetch, a HEAD and a download");
    }

    @Test
    void aPublishedChecksumThatIsNotAChecksumFallsBackToTheExport(@TempDir Path dir) throws IOException {
        serve(ETAG, "export one");
        http.put(CHECKSUM_URL, "not the checksum");
        final StubConverter converter = new StubConverter(catalogue("Assembly 1995"));

        assertEquals(converter.catalogue(), export(dir, converter).catalogue());
        assertEquals(1, converter.calls(), "the export is converted once");
        assertEquals(3, http.requests(), "a checksum look, a HEAD and a download");
    }

    @Test
    void aSecondExportOnTheSameCacheLoadsTheReleaseCatalogueWithoutARequest(@TempDir Path dir) throws IOException {
        final MusicCatalogue published = catalogue("Assembly 2026");
        publish(published);
        assertEquals(published, export(dir, new StubConverter(catalogue("Assembly 1995"))).catalogue());

        final StubConverter second = new StubConverter(catalogue("Assembly 1998"));
        assertEquals(published, export(dir, second).catalogue());
        assertEquals(2, http.requests(), "nothing is asked of the network for a catalogue already cached");
        assertEquals(0, second.calls());
    }

    private void serve(String etag, String dump) {
        http.putHead(URL, etag, LAST_MODIFIED);
        http.put(URL, dump);
    }

    /**
     * Serves the catalogue as the release publishes it: the bytes as CatalogueFormat writes them and the
     * checksum line beside them.
     */
    private String publish(MusicCatalogue catalogue) throws IOException {
        final byte[] bytes = bytesOf(catalogue);
        final String sha256 = Checksums.sha256(bytes);
        http.put(CHECKSUM_URL, Checksums.line(sha256, CatalogueFormat.fileName()));
        http.put(CATALOGUE_URL, bytes, Optional.of(CatalogueFormat.fileName()));
        return sha256;
    }

    private static byte[] bytesOf(MusicCatalogue catalogue) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CatalogueFormat.write(catalogue, bytes);
        return bytes.toByteArray();
    }

    private DemozooExport export(Path dir, StubConverter converter) {
        return new DemozooExport(http, new CacheDirectory(dir), clock, Runnable::run, converter, URI.create(RELEASES));
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

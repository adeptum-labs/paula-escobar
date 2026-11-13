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

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.demozoo.HttpFetcher;
import com.adeptum.paula.playback.Progress;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Demozoo's music, as the catalogue published with Paula's latest release or, where none can be had, converted
 * from the database export Demozoo publishes every night.
 *
 * <p>The first time it is asked for, the published catalogue is fetched while the caller waits, a few megabytes;
 * only when there is none is Demozoo's export of a couple of hundred megabytes fetched and converted instead.
 * After that the catalogue is read from the cache, and once a week the published checksum is asked after, or
 * Demozoo's handle for its export: only when it has changed is the catalogue fetched again, in the background,
 * with the old one served meanwhile. A catalogue whose checksum no longer matches is fetched afresh, and a network
 * that is down leaves the old one in use and is asked after again a day later rather than at every look.
 */
@Slf4j
public final class DemozooExport implements CatalogueSource {

    public static final URI EXPORT = URI.create("https://data.demozoo.org/demozoo-export.sql.gz");

    private static final Duration CHECK_INTERVAL = Duration.ofDays(7);
    private static final Duration RETRY_INTERVAL = Duration.ofDays(1);
    private static final Duration ASKED_INTERVAL = Duration.ofDays(1);
    private static final String SEGMENT = "demozoo";
    private static final String CATALOGUE = "catalogue.bin.gz";
    private static final String CHECKSUM = "catalogue.sha256";
    private static final String STATE = "export.properties";
    private static final String DOWNLOAD_PREFIX = "export-";
    private static final String DOWNLOAD_SUFFIX = ".sql.gz.part";
    private static final String ETAG = "etag";
    private static final String LAST_MODIFIED = "last-modified";
    private static final String CHECKED = "checked";
    private static final String CONVERTED = "converted";
    private static final String NEXT_CHECK = "next-check";
    private static final String LOOKED = "looked";
    private static final String SOURCE = "source";
    private static final String FROM_RELEASE = "release";
    private static final String FROM_EXPORT = "export";
    private static final String FETCHING_CATALOGUE = "Fetching the Demozoo catalogue";
    private static final String FETCHING = "Fetching the Demozoo database";
    private static final String CONVERTING = "Reading the Demozoo database";

    /**
     * Turns a downloaded export into the catalogue; the converter in use, or a stand-in for a test.
     */
    @FunctionalInterface
    public interface Converter {
        MusicCatalogue convert(Path export, ExportConverter.Watcher watcher) throws IOException;
    }

    private final HttpFetcher http;
    private final CacheDirectory cache;
    private final Clock clock;
    private final Executor background;
    private final Converter converter;
    private final ReleaseCatalogue release;
    private final Progress progress = new Progress();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile MusicCatalogue catalogue;
    private volatile boolean checkNow;
    private volatile Instant nextCheck;
    private volatile Instant looked;

    public DemozooExport(HttpFetcher http, CacheDirectory cache, Executor background) {
        this(http, cache, Clock.systemUTC(), background, ExportConverter::convert);
    }

    public DemozooExport(HttpFetcher http, CacheDirectory cache, Clock clock, Executor background, Converter converter) {
        this(http, cache, clock, background, converter, ReleaseCatalogue.LATEST);
    }

    DemozooExport(HttpFetcher http, CacheDirectory cache, Clock clock, Executor background, Converter converter, URI releases) {
        this.http = http;
        this.cache = cache;
        this.clock = clock;
        this.background = background;
        this.converter = converter;
        this.release = new ReleaseCatalogue(http, releases);
    }

    /**
     * The catalogue, fetched and converted first if there is none yet; a check for a newer export is set off
     * behind it when one is due.
     */
    @Override
    public MusicCatalogue catalogue() throws IOException {
        MusicCatalogue current = catalogue;
        if (current == null) {
            current = firstCatalogue();
        }
        if (checkDue()) {
            refreshInBackground();
        }
        return current;
    }

    /**
     * Asks after the export at the next look rather than waiting out the week, for when the listener asked for
     * fresh answers. Demozoo exports anew every night, so a look already made within the day is not repeated:
     * each one that finds a new export fetches a couple of hundred megabytes.
     */
    @Override
    public void checkSoon() {
        checkNow = true;
    }

    /**
     * What a first fetch or conversion is busy with, for the screen to show while it waits.
     */
    @Override
    public Optional<Progress.Step> preparing() {
        return progress.step();
    }

    private synchronized MusicCatalogue firstCatalogue() throws IOException {
        if (catalogue == null) {
            catalogue = stored().orElse(null);
        }
        if (catalogue == null) {
            catalogue = fromRelease().orElse(null);
        }
        if (catalogue == null) {
            catalogue = fetchAndConvert(http.head(EXPORT));
        }
        return catalogue;
    }

    /**
     * The catalogue published with the latest release, when there is one to be had.
     */
    private Optional<MusicCatalogue> fromRelease() {
        try {
            return Optional.of(takeRelease(release.publishedChecksum()));
        } catch (IOException e) {
            log.info("No Demozoo catalogue from the latest release ({}); converting Demozoo's export instead", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The catalogue in the cache, when it is there and its checksum still matches.
     */
    private Optional<MusicCatalogue> stored() {
        try {
            final Path file = file(CATALOGUE);
            final Path checksum = file(CHECKSUM);
            if (!Files.exists(file) || !Files.exists(checksum)) {
                return Optional.empty();
            }
            final byte[] bytes = Files.readAllBytes(file);
            if (!Checksums.sha256(bytes).equals(Files.readString(checksum, StandardCharsets.US_ASCII).strip())) {
                log.warn("The cached Demozoo catalogue does not match its checksum and is converted afresh");
                return Optional.empty();
            }
            return Optional.of(CatalogueFormat.read(new ByteArrayInputStream(bytes)));
        } catch (IOException e) {
            log.warn("Could not read the cached Demozoo catalogue: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean checkDue() {
        if (checkNow && lookedWithinADay()) {
            checkNow = false;
        }
        return checkNow || !clock.instant().isBefore(nextCheck());
    }

    private boolean lookedWithinADay() {
        if (looked == null) {
            final Properties state = state();
            try {
                final String last = state.getProperty(LOOKED, state.getProperty(CHECKED));
                looked = last == null ? Instant.MIN : Instant.parse(last);
            } catch (RuntimeException e) {
                looked = Instant.MIN;
            }
        }
        return clock.instant().isBefore(looked.plus(ASKED_INTERVAL));
    }

    /**
     * When the export is next to be asked after, read from the state once and kept in hand after that.
     */
    private Instant nextCheck() {
        if (nextCheck == null) {
            final Properties state = state();
            try {
                final String next = state.getProperty(NEXT_CHECK);
                final String checked = state.getProperty(CHECKED);
                nextCheck = next != null ? Instant.parse(next)
                        : checked != null ? Instant.parse(checked).plus(CHECK_INTERVAL) : clock.instant();
            } catch (RuntimeException e) {
                nextCheck = clock.instant();
            }
        }
        return nextCheck;
    }

    /**
     * A failed look is tried again a day later: a network that is down, or a download that cannot finish, would
     * otherwise be tried again at every question asked of the catalogue.
     */
    private void failed(Exception failure) {
        log.warn("Could not refresh the Demozoo catalogue: {}", failure.getMessage());
        looked = clock.instant();
        nextCheck = looked.plus(RETRY_INTERVAL);
        final Properties state = state();
        state.setProperty(NEXT_CHECK, nextCheck.toString());
        state.setProperty(LOOKED, looked.toString());
        try {
            saveState(state);
        } catch (IOException e) {
            log.warn("Could not note when to look at the Demozoo export again: {}", e.getMessage());
        }
    }

    private void refreshInBackground() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        checkNow = false;
        background.execute(() -> {
            try {
                refresh();
            } catch (IOException | RuntimeException e) {
                failed(e);
            } finally {
                refreshing.set(false);
            }
        });
    }

    /**
     * Looks at the catalogue published with the latest release first, and at Demozoo's own export only when no
     * catalogue can be had from there.
     */
    private void refresh() throws IOException {
        try {
            final String published = release.publishedChecksum();
            if (published.equals(storedChecksum())) {
                final Properties state = state();
                markChecked(state);
                saveState(state);
            } else {
                catalogue = takeRelease(published);
            }
            return;
        } catch (IOException e) {
            log.info("No Demozoo catalogue from the latest release ({}); looking at Demozoo's export instead", e.getMessage());
        }
        final HttpFetcher.Head head = http.head(EXPORT);
        final Properties state = state();
        if (head.etag().isPresent() && head.etag().get().equals(state.getProperty(ETAG))) {
            markChecked(state);
            saveState(state);
            return;
        }
        catalogue = fetchAndConvert(head);
    }

    /**
     * Fetches the published catalogue and caches its bytes as they came, so the cached checksum is the published
     * one and an unchanged release is recognised by its checksum alone.
     */
    private MusicCatalogue takeRelease(String sha256) throws IOException {
        try {
            progress.measured(FETCHING_CATALOGUE, 0);
            final ReleaseCatalogue.Fetched fetched = release.fetch(sha256,
                    (read, total) -> progress.measured(FETCHING_CATALOGUE, fraction(read, total)));
            cache.writeAtomically(file(CATALOGUE), fetched.bytes());
            cache.writeAtomically(file(CHECKSUM), fetched.sha256().getBytes(StandardCharsets.US_ASCII));
            final Properties state = new Properties();
            state.setProperty(SOURCE, FROM_RELEASE);
            markChecked(state);
            state.setProperty(CONVERTED, clock.instant().toString());
            saveState(state);
            return fetched.catalogue();
        } finally {
            progress.clear();
        }
    }

    private String storedChecksum() {
        try {
            return Files.readString(file(CHECKSUM), StandardCharsets.US_ASCII).strip();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Fetches the export and converts it. The catalogue and its checksum are written before the state that names
     * the export they came from, so an interrupted conversion is simply done again.
     */
    private MusicCatalogue fetchAndConvert(HttpFetcher.Head head) throws IOException {
        // A file of its own, so two players starting on the same cache do not write over each other's download.
        final Path download = Files.createTempFile(file(CATALOGUE).getParent(), DOWNLOAD_PREFIX, DOWNLOAD_SUFFIX);
        try {
            progress.measured(FETCHING, 0);
            http.download(EXPORT, download, (read, total) -> progress.measured(FETCHING, fraction(read, total)));
            progress.measured(CONVERTING, 0);
            final MusicCatalogue converted = converter.convert(download, (read, total) -> progress.measured(CONVERTING, fraction(read, total)));
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CatalogueFormat.write(converted, bytes);
            cache.writeAtomically(file(CATALOGUE), bytes.toByteArray());
            cache.writeAtomically(file(CHECKSUM), Checksums.sha256(bytes.toByteArray()).getBytes(StandardCharsets.US_ASCII));
            final Properties state = new Properties();
            state.setProperty(SOURCE, FROM_EXPORT);
            head.etag().ifPresent(etag -> state.setProperty(ETAG, etag));
            head.lastModified().ifPresent(modified -> state.setProperty(LAST_MODIFIED, modified));
            markChecked(state);
            state.setProperty(CONVERTED, clock.instant().toString());
            saveState(state);
            return converted;
        } finally {
            progress.clear();
            Files.deleteIfExists(download);
        }
    }

    private void markChecked(Properties state) {
        final Instant now = clock.instant();
        looked = now;
        nextCheck = now.plus(CHECK_INTERVAL);
        state.setProperty(CHECKED, now.toString());
        state.setProperty(LOOKED, now.toString());
        state.setProperty(NEXT_CHECK, nextCheck.toString());
    }

    private static double fraction(long read, long total) {
        return total > 0 ? (double) read / total : 0;
    }

    private Properties state() {
        final Properties state = new Properties();
        try (InputStream in = Files.newInputStream(file(STATE))) {
            state.load(in);
        } catch (IOException e) {
            // No state yet, or none readable: the export is then due a look.
        }
        return state;
    }

    private void saveState(Properties state) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = bytes) {
            state.store(out, null);
        }
        cache.writeAtomically(file(STATE), bytes.toByteArray());
    }

    private Path file(String name) throws IOException {
        return cache.file(SEGMENT, name);
    }
}

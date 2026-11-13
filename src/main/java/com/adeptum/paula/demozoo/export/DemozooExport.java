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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Demozoo's music, converted from the database export Demozoo publishes every night.
 *
 * <p>The first time it is asked for, the export is fetched and converted while the caller waits. After that the
 * converted catalogue is read from the cache, and once a week the export is asked after: only when Demozoo's handle
 * for it has changed is it fetched and converted again, in the background, with the old catalogue served meanwhile.
 * A catalogue whose checksum no longer matches is converted afresh, and a network that is down leaves the old one
 * in use.
 */
@Slf4j
public final class DemozooExport implements CatalogueSource {

    public static final URI EXPORT = URI.create("https://data.demozoo.org/demozoo-export.sql.gz");

    private static final Duration CHECK_INTERVAL = Duration.ofDays(7);
    private static final String SEGMENT = "demozoo";
    private static final String CATALOGUE = "catalogue.bin.gz";
    private static final String CHECKSUM = "catalogue.sha256";
    private static final String STATE = "export.properties";
    private static final String DOWNLOAD = "export.sql.gz.part";
    private static final String ETAG = "etag";
    private static final String LAST_MODIFIED = "last-modified";
    private static final String CHECKED = "checked";
    private static final String CONVERTED = "converted";
    private static final String DIGEST = "SHA-256";
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
    private final Progress progress = new Progress();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile MusicCatalogue catalogue;
    private volatile boolean checkNow;

    public DemozooExport(HttpFetcher http, CacheDirectory cache, Executor background) {
        this(http, cache, Clock.systemUTC(), background, ExportConverter::convert);
    }

    public DemozooExport(HttpFetcher http, CacheDirectory cache, Clock clock, Executor background, Converter converter) {
        this.http = http;
        this.cache = cache;
        this.clock = clock;
        this.background = background;
        this.converter = converter;
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
     * fresh answers.
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
            catalogue = fetchAndConvert(http.head(EXPORT));
        }
        return catalogue;
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
            if (!sha256(bytes).equals(Files.readString(checksum, StandardCharsets.US_ASCII).strip())) {
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
        if (checkNow) {
            return true;
        }
        final String checked = state().getProperty(CHECKED);
        try {
            return checked == null || Instant.parse(checked).plus(CHECK_INTERVAL).isBefore(clock.instant());
        } catch (RuntimeException e) {
            return true;
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
                log.warn("Could not refresh the Demozoo catalogue: {}", e.getMessage());
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void refresh() throws IOException {
        final HttpFetcher.Head head = http.head(EXPORT);
        final Properties state = state();
        if (head.etag().isPresent() && head.etag().get().equals(state.getProperty(ETAG))) {
            state.setProperty(CHECKED, clock.instant().toString());
            saveState(state);
            return;
        }
        catalogue = fetchAndConvert(head);
    }

    /**
     * Fetches the export and converts it. The catalogue and its checksum are written before the state that names
     * the export they came from, so an interrupted conversion is simply done again.
     */
    private MusicCatalogue fetchAndConvert(HttpFetcher.Head head) throws IOException {
        final Path download = file(DOWNLOAD);
        try {
            progress.measured(FETCHING, 0);
            http.download(EXPORT, download, (read, total) -> progress.measured(FETCHING, fraction(read, total)));
            progress.measured(CONVERTING, 0);
            final MusicCatalogue converted = converter.convert(download, (read, total) -> progress.measured(CONVERTING, fraction(read, total)));
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CatalogueFormat.write(converted, bytes);
            cache.writeAtomically(file(CATALOGUE), bytes.toByteArray());
            cache.writeAtomically(file(CHECKSUM), sha256(bytes.toByteArray()).getBytes(StandardCharsets.US_ASCII));
            final Properties state = new Properties();
            head.etag().ifPresent(etag -> state.setProperty(ETAG, etag));
            head.lastModified().ifPresent(modified -> state.setProperty(LAST_MODIFIED, modified));
            state.setProperty(CHECKED, clock.instant().toString());
            state.setProperty(CONVERTED, clock.instant().toString());
            saveState(state);
            return converted;
        } finally {
            progress.clear();
            Files.deleteIfExists(download);
        }
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST).digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(DIGEST + " is missing from the runtime", e);
        }
    }
}

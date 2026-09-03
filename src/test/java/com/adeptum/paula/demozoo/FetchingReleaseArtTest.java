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

package com.adeptum.paula.demozoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.testing.TestArchives;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FetchingReleaseArtTest {

    private static final String PRODUCTION_URL = "https://demozoo.org/api/v1/productions/7/?format=json";
    private static final String MODLAND_FILE = "https://ftp.modland.com/pub/modules/Protracker/Theseus/funkyeeh.lha";
    private static final CompoEntry ENTRY = new CompoEntry(1, "1", 7, "Funkyeeh", "Theseus", Set.of(29));
    private static final String BANNER = ".-------------.\n| THE PARTY   |\n`-------------'\n";

    private final FakeHttp http = new FakeHttp();

    private FetchingReleaseArt art(Path dir) {
        final CacheDirectory cache = new CacheDirectory(dir);
        final TrackResolver resolver = new TrackResolver(new DemozooClient(http, cache), http, cache,
                ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none()));
        return new FetchingReleaseArt(new CachedReleaseArt(cache, Duration.ZERO, Clock.systemUTC()), resolver, Runnable::run);
    }

    @Test
    void bringsDownTheReleaseSoItsArtCanBeRead(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, "{\"id\":7,\"title\":\"Funkyeeh\",\"download_links\":[{\"link_class\":\"ModlandFile\",\"url\":\"" + MODLAND_FILE + "\"}],\"external_links\":[]}");
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("file_id.diz", BANNER.getBytes(StandardCharsets.ISO_8859_1));
        entries.put("tune.mod", TestModules.proTracker());
        http.put(MODLAND_FILE, TestArchives.zip(entries), Optional.empty());
        final FetchingReleaseArt art = art(dir);

        assertEquals(Optional.empty(), art.of(ENTRY.productionId()), "nothing is there before it is fetched");
        art.fetch(ENTRY);

        assertTrue(art.of(ENTRY.productionId()).orElseThrow().contains("| THE PARTY   |"));
    }

    @Test
    void fetchesEachReleaseOnlyOnce(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, "{\"id\":7,\"title\":\"Funkyeeh\",\"download_links\":[],\"external_links\":[]}");
        final FetchingReleaseArt art = art(dir);

        art.fetch(ENTRY);
        final int requests = http.requests();
        art.fetch(ENTRY);

        assertEquals(requests, http.requests());
    }

    @Test
    void leavesAloneAReleaseWhoseArtIsAlreadyRead() {
        final List<String> banner = List.of("one", "two");
        final FetchingReleaseArt art = new FetchingReleaseArt(production -> Optional.of(banner), null, Runnable::run);

        art.fetch(ENTRY);

        assertEquals(Optional.of(banner), art.of(ENTRY.productionId()));
    }

    @Test
    void keepsQuietWhenThereIsNothingToFetch(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, "{\"id\":7,\"title\":\"Funkyeeh\",\"download_links\":[],\"external_links\":[]}");
        final FetchingReleaseArt art = art(dir);

        art.fetch(ENTRY);

        assertEquals(Optional.empty(), art.of(ENTRY.productionId()));
    }
}

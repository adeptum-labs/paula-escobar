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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.adeptum.paula.demozoo.FakeHttp;
import com.adeptum.paula.demozoo.HttpFetcher;
import com.adeptum.paula.demozoo.Production;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReleaseCatalogueTest {

    private static final String BASE = "https://releases.example/latest/download/";
    private static final String CHECKSUM_URL = BASE + CatalogueFormat.checksumName();
    private static final String CATALOGUE_URL = BASE + CatalogueFormat.fileName();

    private final FakeHttp http = new FakeHttp();
    private final ReleaseCatalogue release = new ReleaseCatalogue(http, URI.create(BASE));

    @Test
    void publishedChecksumReadsTheLineBesideTheCatalogue() throws IOException {
        http.put(CHECKSUM_URL, Checksums.line(sha256(), CatalogueFormat.fileName()));

        assertEquals(sha256(), release.publishedChecksum());
    }

    @Test
    void fetchReturnsTheBytesTheirChecksumAndTheCatalogue() throws IOException {
        http.put(CATALOGUE_URL, bytes(), Optional.of(CatalogueFormat.fileName()));

        final ReleaseCatalogue.Fetched fetched = release.fetch(sha256(), HttpFetcher.Watcher.NONE);

        assertArrayEquals(bytes(), fetched.bytes());
        assertEquals(sha256(), fetched.sha256());
        assertEquals(catalogue(), fetched.catalogue());
    }

    @Test
    void fetchRefusesBytesThatDoNotMatchTheirChecksum() throws IOException {
        http.put(CATALOGUE_URL, bytes(), Optional.of(CatalogueFormat.fileName()));
        final String other = Checksums.sha256("another catalogue".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> release.fetch(other, HttpFetcher.Watcher.NONE));
    }

    @Test
    void fetchRefusesBytesThatAreNotACatalogue() throws IOException {
        final byte[] bytes = "not a catalogue".getBytes(StandardCharsets.UTF_8);
        http.put(CATALOGUE_URL, bytes, Optional.empty());

        assertThrows(IOException.class, () -> release.fetch(Checksums.sha256(bytes), HttpFetcher.Watcher.NONE));
    }

    private static MusicCatalogue catalogue() {
        return new MusicCatalogue(Map.of(), Map.of(), Map.of(),
                Map.of(7, new Production(7, "Funkyeeh", List.of(), List.of(), null)), Map.of());
    }

    private static byte[] bytes() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CatalogueFormat.write(catalogue(), bytes);
        return bytes.toByteArray();
    }

    private static String sha256() throws IOException {
        return Checksums.sha256(bytes());
    }
}

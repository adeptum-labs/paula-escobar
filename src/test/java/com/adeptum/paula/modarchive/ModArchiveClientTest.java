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

package com.adeptum.paula.modarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.demozoo.FakeHttp;
import com.adeptum.paula.testing.ModArchivePages;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModArchiveClientTest {

    private static final String PAGE_ONE = "https://modarchive.org/index.php?request=view_top_favourites&page=1";
    private static final String PAGE_TWO = "https://modarchive.org/index.php?request=view_top_favourites&page=2";
    private static final String DOWNLOADS_ONE = "https://modarchive.org/index.php?request=view_chart&query=tophits&page=1";
    private static final String ARTIST_ONE = "https://modarchive.org/index.php?request=view_artist_modules&query=69185&page=1";
    private static final String MODULE_PAGE = "https://modarchive.org/index.php?request=view_by_moduleid&query=212083";
    private static final Duration TTL = Duration.ofDays(1);
    private static final Instant NOW = Instant.now();
    private static final ChartEntry FIRST = new ChartEntry(212083, "UnreaL ][ / PM", "2nd_pm.s3m", "438 favourites");
    private static final ChartEntry SECOND = new ChartEntry(57925, "space_debris", "space_debris.mod", "389 favourites");

    private final FakeHttp http = new FakeHttp();
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ModArchiveClient client(Path dir) {
        return new ModArchiveClient(http, new CacheDirectory(dir), TTL, clock);
    }

    @Test
    void asksForTheRightPageOfTheRightChart(@TempDir Path dir) throws IOException {
        http.put(PAGE_TWO, ModArchivePages.page(Chart.TOP_FAVOURITES, 2, 3, SECOND), Optional.empty());
        http.put(DOWNLOADS_ONE, ModArchivePages.page(Chart.MOST_DOWNLOADS, 1, 25, new ChartEntry(1, "a", "a.it", "9 downloads")), Optional.empty());

        assertEquals(SECOND, client(dir).list(Chart.TOP_FAVOURITES, 2).entries().get(0));
        assertEquals("9 downloads", client(dir).list(Chart.MOST_DOWNLOADS, 1).entries().get(0).measure());
    }

    @Test
    void keepsAPageAndServesItAgainWithoutAsking(@TempDir Path dir) throws IOException {
        http.put(PAGE_ONE, ModArchivePages.page(Chart.TOP_FAVOURITES, 1, 3, FIRST), Optional.empty());

        client(dir).list(Chart.TOP_FAVOURITES, 1);
        assertEquals(FIRST, client(dir).list(Chart.TOP_FAVOURITES, 1).entries().get(0));

        assertEquals(1, http.requests());
        assertTrue(Files.exists(dir.resolve("modarchive/top-favourites/1.html")));
    }

    @Test
    void asksAgainAfterADay(@TempDir Path dir) throws IOException {
        http.put(PAGE_ONE, ModArchivePages.page(Chart.TOP_FAVOURITES, 1, 3, FIRST), Optional.empty());
        client(dir).list(Chart.TOP_FAVOURITES, 1);
        clock = Clock.fixed(NOW.plus(TTL).plusSeconds(60), ZoneOffset.UTC);
        client(dir).list(Chart.TOP_FAVOURITES, 1);
        assertEquals(2, http.requests());
    }

    @Test
    void servesTheOldPageWhenTheSiteIsOutOfReach(@TempDir Path dir) throws IOException {
        http.put(PAGE_ONE, ModArchivePages.page(Chart.TOP_FAVOURITES, 1, 3, FIRST), Optional.empty());
        client(dir).list(Chart.TOP_FAVOURITES, 1);
        clock = Clock.fixed(NOW.plus(TTL).plusSeconds(60), ZoneOffset.UTC);
        http.goOffline();
        assertEquals(FIRST, client(dir).list(Chart.TOP_FAVOURITES, 1).entries().get(0));
        assertThrows(IOException.class, () -> client(dir).list(Chart.TOP_FAVOURITES, 2), "never seen, nothing to serve");
    }

    @Test
    void keepsNothingOfAPageThatDoesNotRead(@TempDir Path dir) {
        http.put(PAGE_ONE, "<html>Down for maintenance</html>");
        assertThrows(IOException.class, () -> client(dir).list(Chart.TOP_FAVOURITES, 1));
        assertFalse(Files.exists(dir.resolve("modarchive/top-favourites/1.html")));
    }

    @Test
    void listsAnArtistsModulesLikeAChart(@TempDir Path dir) throws IOException {
        final Artist artist = new Artist(69185, "Purple Motion");
        final ChartEntry rated = new ChartEntry(180373, "Future Brain", "future_brain.stm", "9 / 10");
        http.put(ARTIST_ONE, ModArchivePages.artistPage(artist, 1, 3, rated), Optional.empty());

        final ChartPage page = client(dir).list(artist, 1);

        assertEquals(List.of(rated), page.entries());
        assertEquals(3, page.lastPage());
        assertTrue(Files.isRegularFile(dir.resolve("modarchive/artist-69185/1.html")));
    }

    @Test
    void namesTheRegisteredArtistsOfAModuleAndKeepsThePage(@TempDir Path dir) throws IOException {
        final Artist artist = new Artist(69185, "Purple Motion");
        http.put(MODULE_PAGE, ModArchivePages.modulePage(artist), Optional.empty());

        assertEquals(List.of(artist), client(dir).artists(212083));
        assertEquals(List.of(artist), client(dir).artists(212083));
        assertEquals(1, http.requests());
        assertTrue(Files.isRegularFile(dir.resolve("modarchive/modules/212083.html")));
    }

    @Test
    void forgettingAChartDropsItsPages(@TempDir Path dir) throws IOException {
        http.put(PAGE_ONE, ModArchivePages.page(Chart.TOP_FAVOURITES, 1, 3, FIRST), Optional.empty());
        http.put(PAGE_TWO, ModArchivePages.page(Chart.TOP_FAVOURITES, 2, 3, SECOND), Optional.empty());
        client(dir).list(Chart.TOP_FAVOURITES, 1);
        client(dir).list(Chart.TOP_FAVOURITES, 2);

        client(dir).forget(Chart.TOP_FAVOURITES);
        client(dir).list(Chart.TOP_FAVOURITES, 1);

        assertEquals(3, http.requests());
        assertFalse(Files.exists(dir.resolve("modarchive/top-favourites/2.html")));
    }
}

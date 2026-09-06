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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.adeptum.paula.testing.ModArchivePages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModArchiveHtmlTest {

    private static byte[] saved(String name) throws IOException {
        try (InputStream in = ModArchiveHtmlTest.class.getResourceAsStream("/modarchive/" + name + ".html")) {
            return in.readAllBytes();
        }
    }

    @Test
    void readsTheFavouritesChart() throws IOException {
        final ChartPage page = ModArchiveHtml.parse(Chart.TOP_FAVOURITES, 1, saved("top-favourites"));

        assertEquals(3, page.entries().size(), "the About block is not an entry");
        assertEquals(new ChartEntry(212083, "UnreaL ][ / PM", "2nd_pm.s3m", "438 favourites"), page.entries().get(0));
        assertEquals(1526, page.lastPage());
        assertEquals(1, page.page());
    }

    @Test
    void readsTheDownloadsChart() throws IOException {
        final ChartPage page = ModArchiveHtml.parse(Chart.MOST_DOWNLOADS, 1, saved("most-downloads"));

        assertEquals("334,892 downloads", page.entries().get(0).measure());
        assertEquals("s3m", page.entries().get(0).extension());
        assertEquals(25, page.lastPage());
    }

    @Test
    void readsTheFeaturedChart() throws IOException {
        final ChartPage page = ModArchiveHtml.parse(Chart.FEATURED, 1, saved("featured"));

        assertEquals(new ChartEntry(81033, "sunset on 77", "sunset_on_77.mod", "Week 36, 2026"), page.entries().get(0));
        assertEquals(159, page.lastPage());
    }

    @Test
    void unescapesWhatTheSiteEscaped() throws IOException {
        final byte[] page = ModArchivePages.page(Chart.TOP_FAVOURITES, 2, 9,
                new ChartEntry(7, "Rollin'Down \"77\" & Co", "rollin.xm", "12 favourites"));

        final ChartPage parsed = ModArchiveHtml.parse(Chart.TOP_FAVOURITES, 2, page);

        assertEquals("Rollin'Down \"77\" & Co", parsed.entries().get(0).title());
        assertEquals(9, parsed.lastPage());
    }

    @Test
    void aBuiltPageReadsLikeASavedOne() throws IOException {
        final ChartPage saved = ModArchiveHtml.parse(Chart.FEATURED, 1, saved("featured"));
        final byte[] built = ModArchivePages.page(Chart.FEATURED, 1, saved.lastPage(), saved.entries().toArray(ChartEntry[]::new));

        assertEquals(saved, ModArchiveHtml.parse(Chart.FEATURED, 1, built));
    }

    @Test
    void readsAnArtistsModules() throws IOException {
        final Artist artist = new Artist(69185, "Purple Motion");
        final ChartPage page = ModArchiveHtml.parse(artist, 1, saved("artist"));

        assertEquals(artist, page.listing());
        assertEquals(3, page.entries().size());
        assertEquals(new ChartEntry(214141, "Puzzix", "purple_motion_-_puzzix.it", ""), page.entries().get(0),
                "an unrated module carries no measure");
        assertEquals(new ChartEntry(180373, "Future Brain", "future_brain.stm", "9 / 10"), page.entries().get(2));
        assertEquals(3, page.lastPage());
    }

    @Test
    void aBuiltArtistPageReadsLikeASavedOne() throws IOException {
        final Artist artist = new Artist(69185, "Purple Motion");
        final ChartPage saved = ModArchiveHtml.parse(artist, 1, saved("artist"));
        final byte[] built = ModArchivePages.artistPage(artist, 1, 3, saved.entries().toArray(ChartEntry[]::new));

        assertEquals(saved, ModArchiveHtml.parse(artist, 1, built));
    }

    @Test
    void readsTheRegisteredArtistsOfAModule() throws IOException {
        assertEquals(List.of(new Artist(69185, "Purple Motion")), ModArchiveHtml.artists(saved("module")));
        assertEquals(List.of(new Artist(1, "A & B"), new Artist(2, "C")),
                ModArchiveHtml.artists(ModArchivePages.modulePage(new Artist(1, "A & B"), new Artist(2, "C"))));
    }

    @Test
    void aModuleWithoutRegisteredArtistsNamesNone() {
        assertEquals(List.of(), ModArchiveHtml.artists(ModArchivePages.modulePage()));
    }

    @Test
    void refusesAnArtistPageWithoutRows() {
        final byte[] page = "<html><body><h1>X's Modules</h1></body></html>".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ModArchiveHtml.parse(new Artist(1, "X"), 1, page));
    }

    @Test
    void refusesAPageWithoutEntries() {
        final byte[] page = "<html><body>Maintenance</body></html>".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ModArchiveHtml.parse(Chart.FEATURED, 1, page));
    }

    @Test
    void slicesByExtension() {
        final ChartEntry xm = new ChartEntry(1, "a", "A.XM", "");
        final ChartEntry med = new ChartEntry(2, "b", "b.med", "");
        assertEquals(List.of(Slice.ALL, Slice.XM), List.of(Slice.values()).stream().filter(slice -> slice.holds(xm)).toList());
        assertEquals(List.of(Slice.ALL, Slice.OTHER), List.of(Slice.values()).stream().filter(slice -> slice.holds(med)).toList());
    }
}

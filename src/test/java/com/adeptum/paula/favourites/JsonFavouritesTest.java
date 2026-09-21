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

package com.adeptum.paula.favourites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.Nick;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.Work;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.LocalTrack;
import com.adeptum.paula.playlist.MusicianTrack;
import com.adeptum.paula.playlist.Track;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFavouritesTest {

    private static final Party PARTY = new Party(300, "CAFe 2002", "2002-08-24");
    private static final Competition COMPO = new Competition(6009, "Amiga/PC Multichannel Traditional Music", 29,
            "Tracked Music", List.of());
    private static final CompoEntry ENTRY = new CompoEntry(1, "1", 56012, "Tribute",
            List.of(new Nick("Visual", 8949, false)), Set.of(29));
    private static final DemozooTrack TRIBUTE = new DemozooTrack(ENTRY, PARTY, COMPO);

    @Test
    void anAbsentFileHoldsNothing(@TempDir Path dir) {
        assertEquals(List.of(), favourites(dir).tracks());
    }

    @Test
    void togglingAnAbsentTrackAddsIt(@TempDir Path dir) throws IOException {
        final Favourites favourites = favourites(dir);

        assertTrue(favourites.toggle(TRIBUTE));

        assertTrue(favourites.holds(TRIBUTE));
        assertEquals(List.of(TRIBUTE), favourites.tracks());
    }

    @Test
    void togglingAKeptTrackRemovesIt(@TempDir Path dir) throws IOException {
        final Favourites favourites = favourites(dir);
        favourites.toggle(TRIBUTE);

        assertFalse(favourites.toggle(TRIBUTE));

        assertFalse(favourites.holds(TRIBUTE));
        assertEquals(List.of(), favourites.tracks());
    }

    @Test
    void aMusicianTrackOfTheSameProductionIsTheSameFavourite(@TempDir Path dir) throws IOException {
        final Favourites favourites = favourites(dir);
        favourites.toggle(TRIBUTE);

        assertTrue(favourites.holds(new MusicianTrack(new Nick("Visual", 8949, false), new Work(ENTRY, "2002", "Amiga"))));
    }

    @Test
    void survivesAcrossInstances(@TempDir Path dir) throws IOException {
        favourites(dir).toggle(TRIBUTE);

        assertTrue(favourites(dir).holds(TRIBUTE));
    }

    @Test
    void aCorruptFileReadsAsEmptyRatherThanThrowing(@TempDir Path dir) throws IOException {
        final DataDirectory data = new DataDirectory(dir);
        Files.writeString(data.file("favourites.json"), "not json", StandardCharsets.UTF_8);

        assertEquals(List.of(), favourites(dir).tracks());
    }

    @Test
    void isSortedByTitleCaseInsensitively(@TempDir Path dir) throws IOException {
        final Favourites favourites = favourites(dir);
        favourites.toggle(new LocalTrack(Path.of("zebra.mod")));
        favourites.toggle(new LocalTrack(Path.of("Apple.mod")));
        favourites.toggle(new LocalTrack(Path.of("banana.mod")));

        assertEquals(List.of("Apple.mod", "banana.mod", "zebra.mod"),
                favourites.tracks().stream().map(Track::label).toList());
    }

    private static Favourites favourites(Path dir) {
        return new JsonFavourites(new DataDirectory(dir));
    }
}

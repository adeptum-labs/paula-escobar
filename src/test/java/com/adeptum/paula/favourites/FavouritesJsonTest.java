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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.Nick;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.Work;
import com.adeptum.paula.modarchive.Artist;
import com.adeptum.paula.modarchive.Chart;
import com.adeptum.paula.modarchive.ChartEntry;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.LocalTrack;
import com.adeptum.paula.playlist.ModArchiveTrack;
import com.adeptum.paula.playlist.MusicianTrack;
import com.adeptum.paula.playlist.Track;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FavouritesJsonTest {

    private static final Party PARTY = new Party(300, "CAFe 2002", "2002-08-24");
    private static final Competition COMPO = new Competition(6009, "Amiga/PC Multichannel Traditional Music", 29,
            "Tracked Music", List.of());
    private static final CompoEntry ENTRY = new CompoEntry(1, "1", 56012, "Tribute",
            List.of(new Nick("Visual", 8949, false)), Set.of(29));

    @Test
    void roundTripsALocalTrack() throws IOException {
        assertRoundTrips(new LocalTrack(Path.of("/music/song.mod")));
    }

    @Test
    void roundTripsADemozooTrack() throws IOException {
        assertRoundTrips(new DemozooTrack(ENTRY, PARTY, COMPO));
    }

    @Test
    void roundTripsAMusicianTrack() throws IOException {
        assertRoundTrips(new MusicianTrack(new Nick("Visual", 8949, false), new Work(ENTRY, "2002", "Amiga")));
    }

    @Test
    void roundTripsAModArchiveChartTrack() throws IOException {
        assertRoundTrips(new ModArchiveTrack(Chart.TOP_FAVOURITES,
                new ChartEntry(57925, "space_debris", "space_debris.mod", "389 favourites")));
    }

    @Test
    void roundTripsAModArchiveArtistTrack() throws IOException {
        assertRoundTrips(new ModArchiveTrack(new Artist(69185, "Purple Motion"),
                new ChartEntry(1, "tune", "tune.mod", "")));
    }

    @Test
    void keepsTheOrderOfTheList() throws IOException {
        final List<Track> tracks = List.of(new LocalTrack(Path.of("/a.mod")), new LocalTrack(Path.of("/b.mod")));
        final byte[] written = FavouritesJson.write(tracks);
        assertEquals(tracks, FavouritesJson.read(written));
    }

    @Test
    void anEmptyListRoundTrips() throws IOException {
        assertEquals(List.of(), FavouritesJson.read(FavouritesJson.write(List.of())));
    }

    private static void assertRoundTrips(Track track) throws IOException {
        final byte[] written = FavouritesJson.write(List.of(track));
        final List<Track> read = FavouritesJson.read(written);
        assertEquals(1, read.size());
        assertEquals(track.label(), read.get(0).label());
        assertEquals(FavouriteKey.of(track), FavouriteKey.of(read.get(0)));
        assertTrue(read.get(0).getClass().equals(track.getClass()));
    }
}

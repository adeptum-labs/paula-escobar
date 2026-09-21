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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.Nick;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.Work;
import com.adeptum.paula.modarchive.Chart;
import com.adeptum.paula.modarchive.ChartEntry;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.LocalTrack;
import com.adeptum.paula.playlist.ModArchiveTrack;
import com.adeptum.paula.playlist.MusicianTrack;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FavouriteKeyTest {

    private static final Party PARTY = new Party(5, "The Party 1995", "1995-12-27");
    private static final Competition COMPO = new Competition(2, "Music", 29, "Tracked Music", List.of());
    private static final CompoEntry ENTRY = new CompoEntry(1, "1", 56012, "Tribute",
            List.of(new Nick("Visual", 8949, false)), Set.of(29));
    private static final Nick MUSICIAN = new Nick("Visual", 8949, false);
    private static final Work WORK = new Work(ENTRY, "2002", "Amiga");

    @Test
    void keysADemozooTrackByItsProductionId() {
        final DemozooTrack track = new DemozooTrack(ENTRY, PARTY, COMPO);
        assertEquals("demozoo:56012", FavouriteKey.of(track));
    }

    @Test
    void aReloadedCompetitionKeysTheSameDemozooTrack() {
        final Competition reloaded = new Competition(2, "Music", 29, "Tracked Music",
                List.of(ENTRY, new CompoEntry(2, "2", 56013, "Development", List.of(), Set.of(29))));
        assertEquals(FavouriteKey.of(new DemozooTrack(ENTRY, PARTY, COMPO)),
                FavouriteKey.of(new DemozooTrack(ENTRY, PARTY, reloaded)));
    }

    @Test
    void aMusicianTrackSharesTheDemozooNamespace() {
        final MusicianTrack track = new MusicianTrack(MUSICIAN, WORK);
        assertEquals("demozoo:56012", FavouriteKey.of(track));
        assertEquals(FavouriteKey.of(new DemozooTrack(ENTRY, PARTY, COMPO)), FavouriteKey.of(track));
    }

    @Test
    void keysAModArchiveTrackByItsModuleId() {
        final ModArchiveTrack track = new ModArchiveTrack(Chart.TOP_FAVOURITES,
                new ChartEntry(57925, "space_debris", "space_debris.mod", "389 favourites"));
        assertEquals("modarchive:57925", FavouriteKey.of(track));
    }

    @Test
    void keysALocalTrackByItsAbsoluteNormalisedPath() {
        final Path absolute = Path.of("song.mod").toAbsolutePath().normalize();
        assertEquals("file:" + absolute, FavouriteKey.of(new LocalTrack(Path.of("song.mod"))));
        assertEquals(FavouriteKey.of(new LocalTrack(absolute)), FavouriteKey.of(new LocalTrack(Path.of("song.mod"))));
    }

    @Test
    void differentSongsKeyDifferently() {
        assertNotEquals(FavouriteKey.of(new DemozooTrack(ENTRY, PARTY, COMPO)),
                FavouriteKey.of(new ModArchiveTrack(Chart.TOP_FAVOURITES,
                        new ChartEntry(57925, "space_debris", "space_debris.mod", "389 favourites"))));
    }
}

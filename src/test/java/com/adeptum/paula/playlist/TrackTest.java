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

package com.adeptum.paula.playlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.Nick;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.Work;
import com.adeptum.paula.modarchive.Chart;
import com.adeptum.paula.modarchive.ChartEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrackTest {

    @Test
    void localTracksAreLabelledByFileName() {
        assertEquals("space.mod", new LocalTrack(Path.of("dir", "space.mod")).label());
    }

    @Test
    void demozooTracksAreLabelledByCompoPlacingTitleAndAuthor() {
        final CompoEntry entry = new CompoEntry(1, "1", 7, "Funkyeeh", List.of(new Nick("Theseus", 0, false)), Set.of(29));
        assertEquals("Assembly 1995 · 4 Channel Music  #1 Funkyeeh by Theseus",
                new DemozooTrack(entry, new Party(3, "Assembly 1995", "1995-08-11"),
                        new Competition(2, "4 Channel Music", 29, "Tracked Music", List.of())).label());
    }

    @Test
    void modArchiveTracksAreLabelledByChartTitleEntryTitleAndFileName() {
        assertEquals("Top Favourites · UnreaL ][ / PM · 2nd_pm.s3m",
                new ModArchiveTrack(Chart.TOP_FAVOURITES,
                        new ChartEntry(212083, "UnreaL ][ / PM", "2nd_pm.s3m", "438 favourites")).label());
    }

    @Test
    void musicianTracksAreLabelledByNameTitleAndYear() {
        final CompoEntry entry = new CompoEntry(1, "1", 7, "Habits", List.of(new Nick("NightBeat", 0, false)), Set.of(29));
        final Nick musician = new Nick("NightBeat", 41, false);
        assertEquals("NightBeat  Habits (2026)", new MusicianTrack(musician, new Work(entry, "2026", "Commodore 64")).label());
    }

    @Test
    void musicianTracksOmitTheYearWhenItIsEmpty() {
        final CompoEntry entry = new CompoEntry(1, "1", 7, "Habits", List.of(new Nick("NightBeat", 0, false)), Set.of(29));
        final Nick musician = new Nick("NightBeat", 41, false);
        assertEquals("NightBeat  Habits", new MusicianTrack(musician, new Work(entry, "", "Commodore 64")).label());
    }
}

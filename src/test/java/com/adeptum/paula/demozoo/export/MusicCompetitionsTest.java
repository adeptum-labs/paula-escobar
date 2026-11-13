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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.export.MusicCompetitions.Placing;
import com.adeptum.paula.demozoo.export.MusicCompetitions.Type;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MusicCompetitionsTest {

    private static final Type MUSIC = new Type(14, "Music", true);
    private static final Type TRACKED = new Type(29, "Tracked Music", true);
    private static final Type EXECUTABLE = new Type(31, "Executable Music", true);
    private static final Type DEMO = new Type(1, "Demo", false);
    private static final Type MUSICDISK = new Type(7, "Musicdisk", false);
    private static final Type VIDEO = new Type(41, "Video", false);
    private static final int ASSEMBLY_1998_ENTRIES = 149;
    private static final int ASSEMBLY_1998_PLAIN_MUSIC = 132;

    @Test
    void keepsATypedMusicCompetition() {
        final Competition competition = judge(1, "4 Channel Music", TRACKED,
                List.of(placing(1, 7, List.of(TRACKED)))).orElseThrow();

        assertEquals(1, competition.id());
        assertEquals("4 Channel Music", competition.name());
        assertEquals(29, competition.typeId());
        assertEquals("Tracked Music", competition.typeName());
    }

    @Test
    void dropsATypedNonMusicCompetition() {
        assertTrue(judge(2, "Demo", DEMO, List.of(placing(1, 7, List.of(DEMO)))).isEmpty());
        assertTrue(judge(3, "Musicdisk", MUSICDISK, List.of(placing(1, 8, List.of(MUSICDISK)))).isEmpty());
    }

    @Test
    void keepsAnUntypedCompetitionWhenMostOfItsTypedEntriesAreMusic() {
        final Competition competition = judge(4, "4 Channel Music", null, List.of(
                placing(1, 1, List.of(TRACKED)),
                placing(2, 2, List.of(EXECUTABLE)),
                placing(3, 3, List.of(TRACKED)))).orElseThrow();

        assertEquals(29, competition.typeId());
        assertEquals("Tracked Music", competition.typeName(), "named after the music type most entries share");
    }

    @Test
    void dropsAnUntypedCompetitionThatIsAnEvenSplitOrHasNothingTyped() {
        assertTrue(judge(5, "Wild", null, List.of(
                placing(1, 1, List.of(TRACKED)),
                placing(2, 2, List.of(DEMO)))).isEmpty(), "half the typed entries are music");
        assertTrue(judge(6, "Obscure computer", null, List.of(placing(1, 3, List.of()))).isEmpty(),
                "nothing says what its entry was");
    }

    @Test
    void sortsEntriesByPositionAndGivesUntypedEntriesTheCompetitionType() {
        final Competition competition = judge(7, "4 Channel Music", TRACKED, List.of(
                placing(2, 8, List.of(TRACKED)),
                placing(1, 7, List.of()),
                placing(3, 9, List.of(EXECUTABLE)))).orElseThrow();

        assertEquals(List.of(7, 8, 9), competition.entries().stream().map(CompoEntry::productionId).toList());
        assertEquals(Set.of(29), competition.entries().get(0).typeIds(), "the untyped entry takes the compo's type");
        assertEquals(Set.of(31), competition.entries().get(2).typeIds(), "a typed entry keeps its own");
    }

    /**
     * Every Kindergarden competition of the nineties went untyped: the four channel one was music by its entries,
     * the wild one was a mix with a music tune in the minority, and nothing says what the obscure computer held.
     */
    @Test
    void judgesEveryKindergardenCompetitionTheWayDemozooJsonDid() {
        final Competition music = judge(1, "4 Channel Music", null, List.of(
                new Placing(1, "1", 1, "Oldskaal", List.of(), List.of(TRACKED)),
                new Placing(2, "2", 2, "Arcane Sleeps", List.of(), List.of(EXECUTABLE)),
                new Placing(3, "3", 3, "Chunky 2 planar", List.of(), List.of(TRACKED)),
                new Placing(4, "4", 4, "Untyped", List.of(), List.of()))).orElseThrow();

        assertEquals("Tracked Music", music.typeName());
        assertEquals(29, music.typeId());
        assertEquals(Set.of(29), music.entries().get(3).typeIds(), "an untyped entry takes that type too");
        assertTrue(judge(2, "Wild", null, List.of(
                placing(1, 5, "Egg", List.of(VIDEO)),
                placing(2, 6, "A Tune", List.of(TRACKED)),
                placing(3, 7, "Sock", List.of(VIDEO)))).isEmpty());
        assertTrue(judge(3, "Obscure computer", null, List.of(placing(1, 8, "Thing", List.of()))).isEmpty());
    }

    /**
     * Assembly 1998's Music competition has no type, so its 149 results alone say it is music: Demozoo files 132
     * of them as plain music and 17 as tracked music.
     */
    @Test
    void judgesAssembly1998MusicByItsEntries() {
        final List<Placing> placings = IntStream.rangeClosed(1, ASSEMBLY_1998_ENTRIES)
                .mapToObj(position -> placing(position, position, "Entry " + position, typesAt(position)))
                .toList();

        final Competition competition = judge(42, "Music", null, placings).orElseThrow();

        assertEquals("Music", competition.typeName(), "named after the type most of its entries share");
        assertEquals(14, competition.typeId());
        assertEquals(ASSEMBLY_1998_ENTRIES, competition.entries().size());
        assertEquals(Set.of(29), competition.entries().get(ASSEMBLY_1998_ENTRIES - 1).typeIds(),
                "a tracked entry keeps its own type");
    }

    private static List<Type> typesAt(int position) {
        return position <= ASSEMBLY_1998_PLAIN_MUSIC ? List.of(MUSIC) : List.of(TRACKED);
    }

    private static Optional<Competition> judge(int id, String name, Type type, List<Placing> placings) {
        return MusicCompetitions.judge(id, name, type, placings);
    }

    private static Placing placing(int position, int productionId, List<Type> types) {
        return placing(position, productionId, "Entry " + productionId, types);
    }

    private static Placing placing(int position, int productionId, String title, List<Type> types) {
        return new Placing(position, String.valueOf(position), productionId, title, List.of(), types);
    }
}

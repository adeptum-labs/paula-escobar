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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DemozooJsonTest {

    static final String SERIES = """
            {"id":2,"name":"Assembly","parties":[
              {"id":103,"name":"Assembly 1995","start_date":"1995-08-10"},
              {"id":7,"name":"Assembly 1992","start_date":"1992-07-24"}]}
            """;

    static final String PARTY = """
            {"id":103,"name":"Assembly 1995","competitions":[
              {"id":1,"name":"Demo","production_type":{"id":1,"name":"Demo","supertype":"production"},"results":[]},
              {"id":2,"name":"4 Channel Music","production_type":{"id":29,"name":"Tracked Music","supertype":"music"},"results":[
                {"position":2,"ranking":"","score":"","production":{"id":8,"title":"Second","author_nicks":[],"types":[]}},
                {"position":1,"ranking":"1","score":"1420","production":{"id":7,"title":"Funkyeeh",
                  "author_nicks":[{"name":"Theseus","abbreviation":"","releaser":{"url":"https://demozoo.org/sceners/5887/","id":5887,"name":"NightBeat","is_group":false}},
                    {"name":"Wild Mc","abbreviation":"","releaser":{"url":"https://demozoo.org/groups/999/","id":999,"name":"Wild Mc Crew","is_group":true}}],
                  "types":[{"id":29,"name":"Tracked Music"}]}},
                {"position":3,"ranking":null,"production":null}]},
              {"id":3,"name":"MP3","production_type":{"id":30,"name":"Streaming Music","supertype":"music"},"results":[]}]}
            """;

    static final String PRODUCTION = """
            {"id":7,"title":"Funkyeeh",
             "download_links":[{"link_class":"SceneOrgFile","url":"https://files.scene.org/view/x.zip"}],
             "external_links":[{"link_class":"ModarchiveModule","url":"https://modarchive.org/index.php?request=view_by_moduleid&query=5"}]}
            """;

    static final String WORKS = """
            [
              {"id":10,"title":"Cyberpunk","supertype":"music","release_date":"2026-01-24",
               "platforms":[{"id":1,"name":"Commodore 64"}],"types":[{"id":1,"name":"Music"}],
               "author_nicks":[{"name":"Theseus","abbreviation":"","releaser":{"id":5887,"name":"NightBeat","is_group":false}}]},
              {"id":11,"title":"A Picture","supertype":"graphics","platforms":[],"types":[],"author_nicks":[]},
              {"id":12,"title":"Oldskool","supertype":"music","release_date":"1999-05-01",
               "platforms":[{"id":2,"name":"Amiga"}],"types":[{"id":2,"name":"Music"}],
               "author_nicks":[{"name":"Theseus","abbreviation":"","releaser":{"id":5887,"name":"NightBeat","is_group":false}}]}
            ]
            """;

    @Test
    void parsesSeriesWithPartiesSortedByDate() throws IOException {
        final PartySeries series = DemozooJson.series(bytes(SERIES));
        assertEquals("Assembly", series.name());
        assertEquals(List.of("Assembly 1992", "Assembly 1995"), series.parties().stream().map(Party::name).toList());
        assertEquals(new Party(103, "Assembly 1995", "1995-08-10"), series.parties().get(1));
    }

    @Test
    void keepsOnlyMusicCompetitionsWithEntriesSortedByPosition() throws IOException {
        final List<Competition> compos = DemozooJson.competitions(bytes(PARTY));
        assertEquals(List.of("4 Channel Music", "MP3"), compos.stream().map(Competition::name).toList());
        assertEquals(29, compos.get(0).typeId());
        final List<CompoEntry> entries = compos.get(0).entries();
        assertEquals(2, entries.size(), "results without a production are dropped");
        assertEquals(List.of(new Nick("Theseus", 5887, false), new Nick("Wild Mc", 999, true)), entries.get(0).authors());
        assertEquals("Theseus & Wild Mc", entries.get(0).author());
        assertEquals(List.of(new Nick("Theseus", 5887, false)), entries.get(0).musicians(), "the group is not a musician of its own");
        assertEquals("-", entries.get(1).placing());
        assertEquals("unknown", entries.get(1).author());
        assertEquals(Set.of(29), entries.get(1).typeIds(), "falls back to the competition type");
    }

    @Test
    void aNickWithoutAReleaserGetsIdZero() throws IOException {
        final String party = """
                {"id":1,"name":"P","competitions":[
                  {"id":1,"name":"Music","production_type":{"id":29,"name":"Tracked Music","supertype":"music"},"results":[
                    {"position":1,"ranking":"1","production":{"id":1,"title":"T","author_nicks":[{"name":"Solo"}],"types":[{"id":29}]}}]}]}
                """;

        final CompoEntry entry = DemozooJson.competitions(bytes(party)).get(0).entries().get(0);

        assertEquals(List.of(new Nick("Solo", 0, false)), entry.authors());
        assertEquals(List.of(), entry.musicians());
    }

    /**
     * Demozoo leaves a fifth of its competitions untyped, every Kindergarden music competition of the nineties
     * among them, so what was entered in one has to say what it was.
     */
    @Test
    void judgesAnUntypedCompetitionByWhatWasEnteredInIt() throws IOException {
        final String party = """
                {"id":149,"name":"Kindergarden 2001","competitions":[
                  {"id":1,"name":"4 Channel Music","production_type":null,"results":[
                    {"position":1,"ranking":"1","production":{"id":1,"title":"Oldskaal","author_nicks":[],"types":[{"id":29,"name":"Tracked Music","supertype":"music"}]}},
                    {"position":2,"ranking":"2","production":{"id":2,"title":"Arcane Sleeps","author_nicks":[],"types":[{"id":31,"name":"Executable Music","supertype":"music"}]}},
                    {"position":3,"ranking":"3","production":{"id":3,"title":"Chunky 2 planar","author_nicks":[],"types":[{"id":29,"name":"Tracked Music","supertype":"music"}]}},
                    {"position":4,"ranking":"4","production":{"id":4,"title":"Untyped","author_nicks":[],"types":[]}}]},
                  {"id":2,"name":"Wild","production_type":null,"results":[
                    {"position":1,"ranking":"1","production":{"id":5,"title":"Egg","author_nicks":[],"types":[{"id":41,"name":"Video","supertype":"production"}]}},
                    {"position":2,"ranking":"2","production":{"id":6,"title":"A Tune","author_nicks":[],"types":[{"id":29,"name":"Tracked Music","supertype":"music"}]}},
                    {"position":3,"ranking":"3","production":{"id":7,"title":"Sock","author_nicks":[],"types":[{"id":41,"name":"Video","supertype":"production"}]}}]},
                  {"id":3,"name":"Obscure computer","results":[
                    {"position":1,"ranking":"1","production":{"id":8,"title":"Thing","author_nicks":[],"types":[]}}]}]}
                """;

        final List<Competition> compos = DemozooJson.competitions(bytes(party));

        assertEquals(List.of("4 Channel Music"), compos.stream().map(Competition::name).toList(),
                "most entries of the first are music, most of the Wild are not, and nothing says what the last held");
        assertEquals("Tracked Music", compos.get(0).typeName(), "named after what most of its entries are");
        assertEquals(29, compos.get(0).typeId());
        assertEquals(Set.of(29), compos.get(0).entries().get(3).typeIds(), "an untyped entry takes that type too");
    }

    @Test
    void flagsExecutableMusicAsUnplayableAndStreamingAsPlayable() {
        final List<Nick> author = List.of(new Nick("a", 0, false));
        assertTrue(new CompoEntry(1, "1", 1, "t", author, Set.of(29)).likelyPlayable());
        assertTrue(new CompoEntry(1, "1", 1, "t", author, Set.of(30)).likelyPlayable(), "streaming music, now that MP3 plays");
        assertFalse(new CompoEntry(1, "1", 1, "t", author, Set.of(14, 31)).likelyPlayable());
        assertFalse(new CompoEntry(1, "1", 1, "t", author, Set.of(38)).likelyPlayable());
    }

    @Test
    void parsesProductionLinksFromBothLists() throws IOException {
        final Production production = DemozooJson.production(bytes(PRODUCTION));
        assertEquals("Funkyeeh", production.title());
        assertEquals(List.of(new Link("SceneOrgFile", "https://files.scene.org/view/x.zip")), production.downloads());
        assertEquals(List.of(new Link("ModarchiveModule", "https://modarchive.org/index.php?request=view_by_moduleid&query=5")),
                production.externals());
    }

    /**
     * Music is seldom pictured, so a release with no screenshot is the ordinary case rather than the odd one.
     */
    @Test
    void takesTheFirstScreenshotAsThePictureOfARelease() throws IOException {
        assertNull(DemozooJson.production(bytes(PRODUCTION)).picture(), "nothing where the release has none");

        final String pictured = PRODUCTION.replace("\"title\":\"Funkyeeh\"",
                "\"title\":\"Funkyeeh\",\"screenshots\":[{\"standard_url\":\"https://media.demozoo.org/s/1.png\","
                        + "\"thumbnail_url\":\"https://media.demozoo.org/t/1.png\"}]");
        assertEquals("https://media.demozoo.org/s/1.png", DemozooJson.production(bytes(pictured)).picture());
    }

    @Test
    void malformedJsonIsAnIoException() {
        assertThrows(IOException.class, () -> DemozooJson.series(bytes("{")));
        assertThrows(IOException.class, () -> DemozooJson.works(bytes("{")));
    }

    @Test
    void keepsOnlyMusicWorksInServedOrderWithPositions() throws IOException {
        final List<Work> works = DemozooJson.works(bytes(WORKS));

        assertEquals(2, works.size(), "the graphics production is dropped");
        assertEquals("2026", works.get(0).year());
        assertEquals("Commodore 64", works.get(0).platform());
        assertEquals(1, works.get(0).entry().position());
        assertEquals("Cyberpunk", works.get(0).entry().title());
        assertEquals(List.of(new Nick("Theseus", 5887, false)), works.get(0).entry().authors());
        assertEquals("1999", works.get(1).year());
        assertEquals("Amiga", works.get(1).platform());
        assertEquals(2, works.get(1).entry().position());
    }

    @Test
    void aWorkWithoutAReleaseDateGetsAnEmptyYear() throws IOException {
        final String works = """
                [{"id":20,"title":"Unknown","supertype":"music","platforms":[],"types":[],"author_nicks":[]}]
                """;

        assertEquals("", DemozooJson.works(bytes(works)).get(0).year());
    }

    @Test
    void findsTheSceneOrgFolderOfAParty() throws IOException {
        final String party = PARTY.replace("\"competitions\":[", "\"external_links\":["
                + "{\"link_class\":\"PouetParty\",\"url\":\"https://www.pouet.net/party.php?which=134\"},"
                + "{\"link_class\":\"SceneOrgFolder\",\"url\":\"https://files.scene.org/browse/parties/1995/asm95/\"}],"
                + "\"competitions\":[");

        assertEquals(Optional.of("https://files.scene.org/browse/parties/1995/asm95/"), DemozooJson.sceneOrgFolder(bytes(party)));
        assertEquals(Optional.empty(), DemozooJson.sceneOrgFolder(bytes(PARTY)));
    }

    @Test
    void unexpectedShapesAreAnIoException() {
        final IOException error = assertThrows(IOException.class,
                () -> DemozooJson.series(bytes("{\"id\":2,\"name\":\"Assembly\",\"parties\":[{\"name\":\"no id\"}]}")));
        assertTrue(error.getMessage().startsWith("Unexpected Demozoo response"));
        assertThrows(IOException.class, () -> DemozooJson.competitions(bytes("{\"competitions\":[{\"production_type\":{\"supertype\":\"music\"}}]}")));
        assertThrows(IOException.class, () -> DemozooJson.production(bytes("{\"title\":\"no id\"}")));
        assertThrows(IOException.class, () -> DemozooJson.works(bytes("[{\"supertype\":\"music\",\"title\":\"no id\"}]")));
    }

    static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}

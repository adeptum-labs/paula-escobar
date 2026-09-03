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
                  "author_nicks":[{"name":"Theseus"},{"name":"Wild Mc"}],"types":[{"id":29,"name":"Tracked Music"}]}},
                {"position":3,"ranking":null,"production":null}]},
              {"id":3,"name":"MP3","production_type":{"id":30,"name":"Streaming Music","supertype":"music"},"results":[]}]}
            """;

    static final String PRODUCTION = """
            {"id":7,"title":"Funkyeeh",
             "download_links":[{"link_class":"SceneOrgFile","url":"https://files.scene.org/view/x.zip"}],
             "external_links":[{"link_class":"ModarchiveModule","url":"https://modarchive.org/index.php?request=view_by_moduleid&query=5"}]}
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
        assertEquals(new CompoEntry(1, "1", 7, "Funkyeeh", "Theseus & Wild Mc", Set.of(29)), entries.get(0));
        assertEquals("-", entries.get(1).placing());
        assertEquals("unknown", entries.get(1).author());
        assertEquals(Set.of(29), entries.get(1).typeIds(), "falls back to the competition type");
    }

    @Test
    void flagsStreamingAndExecutableMusicAsUnplayable() {
        assertTrue(new CompoEntry(1, "1", 1, "t", "a", Set.of(29)).likelyPlayable());
        assertFalse(new CompoEntry(1, "1", 1, "t", "a", Set.of(30)).likelyPlayable());
        assertFalse(new CompoEntry(1, "1", 1, "t", "a", Set.of(14, 31)).likelyPlayable());
    }

    @Test
    void parsesProductionLinksFromBothLists() throws IOException {
        final Production production = DemozooJson.production(bytes(PRODUCTION));
        assertEquals("Funkyeeh", production.title());
        assertEquals(List.of(new Link("SceneOrgFile", "https://files.scene.org/view/x.zip")), production.downloads());
        assertEquals(List.of(new Link("ModarchiveModule", "https://modarchive.org/index.php?request=view_by_moduleid&query=5")),
                production.externals());
    }

    @Test
    void malformedJsonIsAnIoException() {
        assertThrows(IOException.class, () -> DemozooJson.series(bytes("{")));
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
    }

    static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}

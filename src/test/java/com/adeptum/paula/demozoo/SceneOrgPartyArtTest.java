/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SceneOrgPartyArtTest {

    private static final int PARTY = 601;
    private static final String PARTY_URL = "https://demozoo.org/api/v1/parties/601/?format=json";
    private static final String PARTY_JSON = """
            {"id":601,"name":"Icing 1995","competitions":[],
             "external_links":[{"link_class":"SceneOrgFolder","url":"https://files.scene.org/browse/parties/1995/icing95/"}]}
            """;
    private static final String FOLDER = "https://archive.scene.org/pub/parties/1995/icing95/";
    private static final String LISTING = """
            <a href="../">../</a>
            <a href="info/">info/</a>
            <a href="m4ch/">m4ch/</a>
            <a href="results.txt">results.txt</a>
            """;
    private static final String INFO_LISTING = """
            <a href="../">../</a>
            <a href="date.txt">date.txt</a>
            <a href="icing95i.diz">icing95i.diz</a>
            """;
    private static final String LOGO = "\n.---------------.\n|   I C I N G   |\n`---------------'\n\n";

    private final FakeHttp http = new FakeHttp();

    private SceneOrgPartyArt art(Path dir) {
        final CacheDirectory cache = new CacheDirectory(dir);
        return new SceneOrgPartyArt(new DemozooClient(http, cache), http, cache, Runnable::run);
    }

    private void serveTheParty() {
        http.put(PARTY_URL, PARTY_JSON);
        http.put(FOLDER, LISTING);
        http.put(FOLDER + "info/", INFO_LISTING);
        http.put(FOLDER + "info/icing95i.diz", LOGO.getBytes(StandardCharsets.ISO_8859_1), Optional.empty());
    }

    @Test
    void readsTheLogoOutOfThePartyFolderOnSceneOrg(@TempDir Path dir) {
        serveTheParty();
        final SceneOrgPartyArt art = art(dir);

        assertEquals(Optional.empty(), art.of(PARTY), "nothing is there before it is fetched");
        art.fetch(PARTY);

        assertEquals(List.of(".---------------.", "|   I C I N G   |", "`---------------'"), art.of(PARTY).orElseThrow());
    }

    @Test
    void keepsTheLogoForTheNextTimeThePartyIsOpened(@TempDir Path dir) {
        serveTheParty();
        art(dir).fetch(PARTY);
        final int requests = http.requests();

        final SceneOrgPartyArt second = art(dir);

        assertTrue(second.of(PARTY).isPresent(), "the logo is read back from the cache");
        assertEquals(requests, http.requests(), "nothing is fetched a second time");
    }

    @Test
    void aPartyWithoutAFolderOrALogoIsLeftAlone(@TempDir Path dir) {
        http.put(PARTY_URL, "{\"id\":601,\"name\":\"Icing 1995\",\"competitions\":[],\"external_links\":[]}");
        final SceneOrgPartyArt art = art(dir);

        art.fetch(PARTY);

        assertEquals(Optional.empty(), art.of(PARTY));
        assertFalse(art.fetching(PARTY), "a party that has been looked at is no longer being fetched");
    }

    @Test
    void looksForTheLogoOnlyOnce(@TempDir Path dir) {
        http.put(PARTY_URL, PARTY_JSON);
        http.put(FOLDER, LISTING);
        final SceneOrgPartyArt art = art(dir);

        art.fetch(PARTY);
        final int requests = http.requests();
        art.fetch(PARTY);

        assertEquals(requests, http.requests(), "a party without a logo is not fetched again");
    }
}

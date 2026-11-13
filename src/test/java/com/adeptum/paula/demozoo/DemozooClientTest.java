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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.demozoo.export.CatalogueSource;
import com.adeptum.paula.demozoo.export.MusicCatalogue;
import com.adeptum.paula.playback.Progress;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DemozooClientTest {

    private static final int SERIES_ID = 2;
    private static final int PARTY_ID = 103;
    private static final int PRODUCTION_ID = 7;
    private static final int RELEASER_ID = 5887;
    private static final String FOLDER = "https://files.scene.org/browse/parties/1995/asm95/";
    private static final Nick NICK = new Nick("Theseus", RELEASER_ID, false);
    private static final CompoEntry ENTRY = new CompoEntry(1, "1", PRODUCTION_ID, "Funkyeeh", List.of(NICK), Set.of(29));
    private static final Competition COMPETITION = new Competition(9, "4 Channel Music", 29, "Tracked Music", List.of(ENTRY));
    private static final PartySeries SERIES = new PartySeries(SERIES_ID, "Assembly",
            List.of(new Party(PARTY_ID, "Assembly 1995", "1995-08-10")));
    private static final Production PRODUCTION = new Production(PRODUCTION_ID, "Funkyeeh",
            List.of(new Link("SceneOrgFile", "https://files.scene.org/view/funky.mod")), List.of(), null);
    private static final Work WORK = new Work(ENTRY, "1995", "Amiga");
    private static final MusicCatalogue CATALOGUE = new MusicCatalogue(Map.of(SERIES_ID, SERIES),
            Map.of(PARTY_ID, List.of(COMPETITION)), Map.of(PARTY_ID, FOLDER), Map.of(PRODUCTION_ID, PRODUCTION),
            Map.of(RELEASER_ID, List.of(WORK)));

    @Test
    void passesEveryLookupThroughTheCatalogue() throws IOException {
        final DemozooClient client = new DemozooClient(CatalogueSource.fixed(CATALOGUE));

        assertEquals(SERIES, client.series(SERIES_ID));
        assertEquals(List.of(COMPETITION), client.competitions(PARTY_ID));
        assertEquals(Optional.of(FOLDER), client.sceneOrgFolder(PARTY_ID));
        assertEquals(PRODUCTION, client.production(PRODUCTION_ID));
        assertEquals(List.of(WORK), client.works(RELEASER_ID));
    }

    @Test
    void anUnknownSeriesOrProductionIsAnIoException() {
        final DemozooClient client = new DemozooClient(CatalogueSource.fixed(CATALOGUE));

        final IOException series = assertThrows(IOException.class, () -> client.series(99));
        final IOException production = assertThrows(IOException.class, () -> client.production(99));

        assertEquals("Demozoo has no party series 99", series.getMessage());
        assertEquals("Demozoo has no production 99", production.getMessage());
    }

    @Test
    void anUnknownPartyHasNoCompetitionsOrSceneOrgFolder() throws IOException {
        final DemozooClient client = new DemozooClient(CatalogueSource.fixed(CATALOGUE));

        assertEquals(List.of(), client.competitions(99));
        assertEquals(Optional.empty(), client.sceneOrgFolder(99));
    }

    @Test
    void forgettingASeriesAPartyOrMusicianAsksForAFreshLook() {
        final CountingSource source = new CountingSource(CATALOGUE);
        final DemozooClient client = new DemozooClient(source);

        client.forgetSeries(SERIES_ID);
        client.forgetParty(PARTY_ID);
        client.forgetWorks(RELEASER_ID);

        assertEquals(3, source.checks());
    }

    @Test
    void preparingPassesThrough() {
        final Progress.Step step = new Progress.Step("Reading the Demozoo database", 0.5, 0);
        final CountingSource source = new CountingSource(CATALOGUE);
        source.show(step);
        final DemozooClient client = new DemozooClient(source);

        assertEquals(Optional.of(step), client.preparing());
        assertTrue(new DemozooClient(CatalogueSource.fixed(CATALOGUE)).preparing().isEmpty());
    }

    private static final class CountingSource implements CatalogueSource {

        private final MusicCatalogue catalogue;
        private int checks;
        private Optional<Progress.Step> step = Optional.empty();

        private CountingSource(MusicCatalogue catalogue) {
            this.catalogue = catalogue;
        }

        private void show(Progress.Step step) {
            this.step = Optional.of(step);
        }

        private int checks() {
            return checks;
        }

        @Override
        public MusicCatalogue catalogue() {
            return catalogue;
        }

        @Override
        public void checkSoon() {
            checks++;
        }

        @Override
        public Optional<Progress.Step> preparing() {
            return step;
        }
    }
}

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

import com.adeptum.paula.demozoo.export.CatalogueSource;
import com.adeptum.paula.playback.Progress;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Answers questions about Demozoo's music from the catalogue converted out of Demozoo's nightly database export.
 */
public final class DemozooClient {

    private final CatalogueSource source;

    public DemozooClient(CatalogueSource source) {
        this.source = source;
    }

    public PartySeries series(int id) throws IOException {
        return source.catalogue().series(id).orElseThrow(() -> new IOException("Demozoo has no party series " + id));
    }

    public List<Competition> competitions(int partyId) throws IOException {
        return source.catalogue().competitions(partyId);
    }

    public Optional<String> sceneOrgFolder(int partyId) throws IOException {
        return source.catalogue().sceneOrgFolder(partyId);
    }

    public Production production(int id) throws IOException {
        return source.catalogue().production(id).orElseThrow(() -> new IOException("Demozoo has no production " + id));
    }

    public List<Work> works(int releaserId) throws IOException {
        return source.catalogue().works(releaserId);
    }

    /**
     * Asking for fresh answers about a series, a party or a musician has Demozoo's export looked at again at the
     * next chance, at most once a day, since every answer comes from the same converted catalogue.
     */
    public void forgetSeries(int id) {
        source.checkSoon();
    }

    public void forgetParty(int partyId) {
        source.checkSoon();
    }

    public void forgetWorks(int releaserId) {
        source.checkSoon();
    }

    /**
     * What getting Demozoo's music ready is busy with, while it is: the first fetch and conversion of the export
     * takes a while.
     */
    public Optional<Progress.Step> preparing() {
        return source.preparing();
    }
}

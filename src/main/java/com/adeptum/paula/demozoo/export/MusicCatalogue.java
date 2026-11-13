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

import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.PartySeries;
import com.adeptum.paula.demozoo.Production;
import com.adeptum.paula.demozoo.Work;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Demozoo's music side, converted once from the nightly export and kept in the cache directory so that the
 * player can start without asking the network for every party and production it shows.
 *
 * <p>{@code competitions} holds only the music competitions of a party, keyed by party id; a party without
 * any is absent rather than mapped to an empty list.
 */
public record MusicCatalogue(Map<Integer, PartySeries> series, Map<Integer, List<Competition>> competitions,
        Map<Integer, String> sceneOrgFolders, Map<Integer, Production> productions, Map<Integer, List<Work>> works) {

    public MusicCatalogue {
        series = Map.copyOf(series);
        competitions = Map.copyOf(competitions);
        sceneOrgFolders = Map.copyOf(sceneOrgFolders);
        productions = Map.copyOf(productions);
        works = Map.copyOf(works);
    }

    public Optional<PartySeries> series(int id) {
        return Optional.ofNullable(series.get(id));
    }

    public List<Competition> competitions(int partyId) {
        return competitions.getOrDefault(partyId, List.of());
    }

    public Optional<String> sceneOrgFolder(int partyId) {
        return Optional.ofNullable(sceneOrgFolders.get(partyId));
    }

    public Optional<Production> production(int id) {
        return Optional.ofNullable(productions.get(id));
    }

    public List<Work> works(int releaserId) {
        return works.getOrDefault(releaserId, List.of());
    }
}

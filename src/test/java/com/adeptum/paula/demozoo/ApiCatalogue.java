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
import com.adeptum.paula.demozoo.export.MusicCatalogue;
import com.adeptum.paula.playback.Progress;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A catalogue built from Demozoo API answers registered with a {@link FakeHttp}, so tests that describe their data
 * the way Demozoo's API once served it keep doing so. Reading them is not counted as a request, and the catalogue is
 * built afresh on every look, so an answer registered partway through a test is seen at once. Offline, there is
 * no catalogue to be had.
 */
public final class ApiCatalogue implements CatalogueSource {

    private static final String API = "https://demozoo.org/api/v1/";
    private static final Pattern RESOURCE = Pattern.compile("^" + Pattern.quote(API) + "(party_series|parties|productions|releasers)/(\\d+)/(productions/)?");

    private final FakeHttp http;
    private int checks;

    public ApiCatalogue(FakeHttp http) {
        this.http = http;
    }

    public static DemozooClient client(FakeHttp http) {
        return new DemozooClient(new ApiCatalogue(http));
    }

    /**
     * How many times fresh answers were asked for.
     */
    public int checks() {
        return checks;
    }

    @Override
    public MusicCatalogue catalogue() throws IOException {
        if (http.isOffline()) {
            throw new IOException("offline");
        }
        final Map<Integer, PartySeries> series = new HashMap<>();
        final Map<Integer, List<Competition>> competitions = new HashMap<>();
        final Map<Integer, String> folders = new HashMap<>();
        final Map<Integer, Production> productions = new HashMap<>();
        final Map<Integer, List<Work>> works = new HashMap<>();
        for (final Map.Entry<URI, byte[]> answer : http.bodiesUnder(API).entrySet()) {
            final Matcher matcher = RESOURCE.matcher(answer.getKey().toString());
            if (!matcher.find()) {
                continue;
            }
            final int id = Integer.parseInt(matcher.group(2));
            final byte[] body = answer.getValue();
            switch (matcher.group(1)) {
                case "party_series" -> series.put(id, DemozooJson.series(body));
                case "parties" -> {
                    final List<Competition> music = DemozooJson.competitions(body);
                    if (!music.isEmpty()) {
                        competitions.put(id, music);
                    }
                    DemozooJson.sceneOrgFolder(body).ifPresent(folder -> folders.put(id, folder));
                }
                case "productions" -> productions.put(id, DemozooJson.production(body));
                default -> {
                    if (matcher.group(3) != null) {
                        works.put(id, DemozooJson.works(body));
                    }
                }
            }
        }
        return new MusicCatalogue(series, competitions, folders, productions, works);
    }

    @Override
    public void checkSoon() {
        checks++;
    }

    @Override
    public Optional<Progress.Step> preparing() {
        return Optional.empty();
    }
}

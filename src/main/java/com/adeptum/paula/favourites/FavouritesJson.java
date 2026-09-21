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

import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.Nick;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.Work;
import com.adeptum.paula.modarchive.Artist;
import com.adeptum.paula.modarchive.Chart;
import com.adeptum.paula.modarchive.ChartEntry;
import com.adeptum.paula.modarchive.Listing;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.LocalTrack;
import com.adeptum.paula.playlist.ModArchiveTrack;
import com.adeptum.paula.playlist.MusicianTrack;
import com.adeptum.paula.playlist.Track;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.spi.JsonProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.parsson.JsonProviderImpl;

/**
 * A favourite is written back as the {@link Track} it is, so it plays with no conversion once read again.
 * The provider is instantiated directly rather than looked up through the service loader so the native
 * image needs no reflection metadata, the same way {@code DemozooJson} reads Demozoo's own payloads.
 */
public final class FavouritesJson {

    private static final JsonProvider JSON = new JsonProviderImpl();

    private static final String KIND = "kind";
    private static final String LOCAL = "local";
    private static final String DEMOZOO = "demozoo";
    private static final String MUSICIAN = "musician";
    private static final String MODARCHIVE = "modarchive";

    private static final String PATH = "path";

    private static final String POSITION = "position";
    private static final String RANKING = "ranking";
    private static final String PRODUCTION_ID = "productionId";
    private static final String TITLE = "title";
    private static final String AUTHORS = "authors";
    private static final String TYPE_IDS = "typeIds";
    private static final String NAME = "name";
    private static final String RELEASER_ID = "releaserId";
    private static final String GROUP = "group";

    private static final String PARTY_ID = "partyId";
    private static final String PARTY_NAME = "partyName";
    private static final String PARTY_START_DATE = "partyStartDate";
    private static final String COMPO_ID = "compoId";
    private static final String COMPO_NAME = "compoName";
    private static final String COMPO_TYPE_ID = "compoTypeId";
    private static final String COMPO_TYPE_NAME = "compoTypeName";

    private static final String MUSICIAN_NAME = "musicianName";
    private static final String MUSICIAN_RELEASER_ID = "musicianReleaserId";
    private static final String MUSICIAN_GROUP = "musicianGroup";
    private static final String WORK_YEAR = "workYear";
    private static final String WORK_PLATFORM = "workPlatform";

    private static final String LISTING_KIND = "listingKind";
    private static final String CHART = "chart";
    private static final String ARTIST = "artist";
    private static final String CHART_ID = "chartId";
    private static final String ARTIST_MEMBER_ID = "artistMemberId";
    private static final String ARTIST_NAME = "artistName";
    private static final String MODULE_ID = "moduleId";
    private static final String FILE_NAME = "fileName";
    private static final String MEASURE = "measure";

    private FavouritesJson() {
    }

    public static byte[] write(List<Track> tracks) {
        final JsonArrayBuilder array = JSON.createArrayBuilder();
        tracks.forEach(track -> array.add(object(track)));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonWriter writer = JSON.createWriter(out)) {
            writer.writeArray(array.build());
        }
        return out.toByteArray();
    }

    public static List<Track> read(byte[] body) throws IOException {
        try (JsonReader reader = JSON.createReader(new ByteArrayInputStream(body))) {
            return reader.readArray().stream().map(JsonValue::asJsonObject).map(FavouritesJson::track).toList();
        } catch (RuntimeException e) {
            throw new IOException("Malformed favourites file: " + e.getMessage(), e);
        }
    }

    private static JsonObject object(Track track) {
        return switch (track) {
            case LocalTrack local -> JSON.createObjectBuilder()
                    .add(KIND, LOCAL)
                    .add(PATH, local.path().toString())
                    .build();
            case DemozooTrack remote -> entry(JSON.createObjectBuilder().add(KIND, DEMOZOO), remote.entry())
                    .add(PARTY_ID, remote.party().id())
                    .add(PARTY_NAME, remote.party().name())
                    .add(PARTY_START_DATE, remote.party().startDate())
                    .add(COMPO_ID, remote.compo().id())
                    .add(COMPO_NAME, remote.compo().name())
                    .add(COMPO_TYPE_ID, remote.compo().typeId())
                    .add(COMPO_TYPE_NAME, remote.compo().typeName())
                    .build();
            case MusicianTrack work -> entry(JSON.createObjectBuilder().add(KIND, MUSICIAN), work.work().entry())
                    .add(MUSICIAN_NAME, work.musician().name())
                    .add(MUSICIAN_RELEASER_ID, work.musician().releaserId())
                    .add(MUSICIAN_GROUP, work.musician().group())
                    .add(WORK_YEAR, work.work().year())
                    .add(WORK_PLATFORM, work.work().platform())
                    .build();
            case ModArchiveTrack module -> listing(JSON.createObjectBuilder().add(KIND, MODARCHIVE), module.listing())
                    .add(MODULE_ID, module.entry().moduleId())
                    .add(TITLE, module.entry().title())
                    .add(FILE_NAME, module.entry().fileName())
                    .add(MEASURE, module.entry().measure())
                    .build();
        };
    }

    private static JsonObjectBuilder entry(JsonObjectBuilder object, CompoEntry entry) {
        final JsonArrayBuilder authors = JSON.createArrayBuilder();
        entry.authors().forEach(nick -> authors.add(JSON.createObjectBuilder()
                .add(NAME, nick.name()).add(RELEASER_ID, nick.releaserId()).add(GROUP, nick.group())));
        final JsonArrayBuilder typeIds = JSON.createArrayBuilder();
        entry.typeIds().forEach(typeIds::add);
        return object.add(POSITION, entry.position()).add(RANKING, entry.ranking())
                .add(PRODUCTION_ID, entry.productionId()).add(TITLE, entry.title())
                .add(AUTHORS, authors).add(TYPE_IDS, typeIds);
    }

    private static JsonObjectBuilder listing(JsonObjectBuilder object, Listing listing) {
        return switch (listing) {
            case Chart chart -> object.add(LISTING_KIND, CHART).add(CHART_ID, chart.id());
            case Artist artist -> object.add(LISTING_KIND, ARTIST)
                    .add(ARTIST_MEMBER_ID, artist.memberId()).add(ARTIST_NAME, artist.name());
        };
    }

    private static Track track(JsonObject object) {
        return switch (object.getString(KIND)) {
            case LOCAL -> new LocalTrack(Path.of(object.getString(PATH)));
            case DEMOZOO -> new DemozooTrack(entry(object),
                    new Party(object.getInt(PARTY_ID), object.getString(PARTY_NAME), object.getString(PARTY_START_DATE)),
                    new Competition(object.getInt(COMPO_ID), object.getString(COMPO_NAME), object.getInt(COMPO_TYPE_ID),
                            object.getString(COMPO_TYPE_NAME), List.of()));
            case MUSICIAN -> new MusicianTrack(
                    new Nick(object.getString(MUSICIAN_NAME), object.getInt(MUSICIAN_RELEASER_ID), object.getBoolean(MUSICIAN_GROUP)),
                    new Work(entry(object), object.getString(WORK_YEAR), object.getString(WORK_PLATFORM)));
            case MODARCHIVE -> new ModArchiveTrack(listing(object),
                    new ChartEntry(object.getInt(MODULE_ID), object.getString(TITLE), object.getString(FILE_NAME),
                            object.getString(MEASURE)));
            default -> throw new JsonException("Unknown favourite kind: " + object.getString(KIND));
        };
    }

    private static CompoEntry entry(JsonObject object) {
        final List<Nick> authors = object.getJsonArray(AUTHORS).stream().map(JsonValue::asJsonObject)
                .map(nick -> new Nick(nick.getString(NAME), nick.getInt(RELEASER_ID), nick.getBoolean(GROUP)))
                .toList();
        final Set<Integer> typeIds = object.getJsonArray(TYPE_IDS).stream()
                .map(value -> ((JsonNumber) value).intValue())
                .collect(Collectors.toUnmodifiableSet());
        return new CompoEntry(object.getInt(POSITION), object.getString(RANKING), object.getInt(PRODUCTION_ID),
                object.getString(TITLE), authors, typeIds);
    }

    private static Listing listing(JsonObject object) {
        return CHART.equals(object.getString(LISTING_KIND))
                ? chartOf(object.getString(CHART_ID))
                : new Artist(object.getInt(ARTIST_MEMBER_ID), object.getString(ARTIST_NAME));
    }

    private static Chart chartOf(String id) {
        return Arrays.stream(Chart.values()).filter(chart -> chart.id().equals(id)).findFirst()
                .orElse(Chart.TOP_FAVOURITES);
    }
}

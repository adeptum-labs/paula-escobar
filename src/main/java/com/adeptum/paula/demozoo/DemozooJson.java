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

import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.parsson.JsonProviderImpl;

/**
 * Turns Demozoo API payloads into the model. The provider is instantiated directly rather than looked up through
 * the service loader so the native image needs no reflection metadata.
 */
public final class DemozooJson {

    private static final JsonProvider JSON = new JsonProviderImpl();
    private static final String MUSIC = "music";
    private static final String UNKNOWN_AUTHOR = "unknown";
    private static final String AUTHOR_SEPARATOR = " & ";

    private DemozooJson() {
    }

    private interface Parser<T> {
        T parse(JsonObject object);
    }

    public static PartySeries series(byte[] body) throws IOException {
        return parse(body, series -> {
            final List<Party> parties = objects(series, "parties")
                    .map(party -> new Party(party.getInt("id"), party.getString("name", ""), party.getString("start_date", "")))
                    .sorted(Comparator.comparing(Party::startDate))
                    .toList();
            return new PartySeries(series.getInt("id"), series.getString("name", ""), parties);
        });
    }

    public static List<Competition> competitions(byte[] body) throws IOException {
        return parse(body, party -> objects(party, "competitions")
                .filter(competition -> MUSIC.equals(object(competition, "production_type").getString("supertype", "")))
                .map(DemozooJson::competition)
                .toList());
    }

    public static Production production(byte[] body) throws IOException {
        return parse(body, production -> new Production(production.getInt("id"), production.getString("title", ""),
                links(production, "download_links"), links(production, "external_links")));
    }

    /**
     * Fields Demozoo always sends are read without null checks; a payload missing them is reported as bad data
     * rather than escaping as an unchecked exception.
     */
    private static <T> T parse(byte[] body, Parser<T> parser) throws IOException {
        final JsonObject object = read(body);
        try {
            return parser.parse(object);
        } catch (RuntimeException e) {
            throw new IOException("Unexpected Demozoo response: " + e, e);
        }
    }

    private static Competition competition(JsonObject competition) {
        final JsonObject type = object(competition, "production_type");
        final int typeId = type.getInt("id", 0);
        final List<CompoEntry> entries = objects(competition, "results")
                .filter(result -> has(result, "production"))
                .map(result -> entry(result, typeId))
                .sorted(Comparator.comparingInt(CompoEntry::position))
                .toList();
        return new Competition(competition.getInt("id"), competition.getString("name", ""), typeId, type.getString("name", ""), entries);
    }

    private static CompoEntry entry(JsonObject result, int competitionTypeId) {
        final JsonObject production = result.getJsonObject("production");
        final Set<Integer> types = objects(production, "types").map(type -> type.getInt("id")).collect(Collectors.toUnmodifiableSet());
        return new CompoEntry(result.getInt("position", 0), result.getString("ranking", ""), production.getInt("id"),
                production.getString("title", ""), author(production), types.isEmpty() ? Set.of(competitionTypeId) : types);
    }

    private static String author(JsonObject production) {
        final String names = objects(production, "author_nicks")
                .map(nick -> nick.getString("name", ""))
                .filter(name -> !name.isBlank())
                .collect(Collectors.joining(AUTHOR_SEPARATOR));
        return names.isEmpty() ? UNKNOWN_AUTHOR : names;
    }

    private static List<Link> links(JsonObject production, String name) {
        return objects(production, name).map(link -> new Link(link.getString("link_class", ""), link.getString("url", ""))).toList();
    }

    private static JsonObject read(byte[] body) throws IOException {
        try (JsonReader reader = JSON.createReader(new ByteArrayInputStream(body))) {
            return reader.readObject();
        } catch (JsonException e) {
            throw new IOException("Malformed Demozoo response: " + e.getMessage(), e);
        }
    }

    private static Stream<JsonObject> objects(JsonObject parent, String name) {
        final JsonArray array = has(parent, name) ? parent.getJsonArray(name) : JsonValue.EMPTY_JSON_ARRAY;
        return array.getValuesAs(JsonObject.class).stream();
    }

    private static JsonObject object(JsonObject parent, String name) {
        return has(parent, name) ? parent.getJsonObject(name) : JsonValue.EMPTY_JSON_OBJECT;
    }

    private static boolean has(JsonObject parent, String name) {
        return parent.containsKey(name) && !parent.isNull(name);
    }
}

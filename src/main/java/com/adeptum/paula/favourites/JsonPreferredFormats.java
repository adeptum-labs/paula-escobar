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

import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.spi.JsonProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.parsson.JsonProviderImpl;

/**
 * The choices kept as JSON under the data directory, read once and written whole on each change; a file that
 * is missing or cannot be parsed counts as empty, as it does for the favourites. The provider is instantiated
 * directly so the native image needs no reflection metadata.
 */
@Slf4j
public final class JsonPreferredFormats implements PreferredFormats {

    private static final JsonProvider JSON = new JsonProviderImpl();
    private static final String FILE = "preferred-formats.json";

    private final DataDirectory data;
    private final Map<String, String> choices = new ConcurrentHashMap<>();

    public JsonPreferredFormats(DataDirectory data) {
        this.data = data;
        choices.putAll(read());
    }

    @Override
    public Optional<String> of(String key) {
        return Optional.ofNullable(choices.get(key));
    }

    @Override
    public synchronized void set(String key, String file) throws IOException {
        choices.put(key, file);
        final JsonObjectBuilder object = JSON.createObjectBuilder();
        choices.forEach(object::add);
        data.writeAtomically(data.file(FILE), object.build().toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> read() {
        final Map<String, String> read = new ConcurrentHashMap<>();
        try {
            final Path file = data.file(FILE);
            if (Files.exists(file)) {
                try (JsonReader reader = JSON.createReader(new ByteArrayInputStream(Files.readAllBytes(file)))) {
                    final JsonObject object = reader.readObject();
                    object.forEach((key, value) -> {
                        if (value instanceof JsonString text) {
                            read.put(key, text.getString());
                        }
                    });
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the preferred formats: {}", e.getMessage());
        }
        return read;
    }
}

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

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * The releases whose downloads were brought down and found to hold nothing Paula can play, executable music most
 * often. Each is noted in the cache together with the formats Paula knew at the time, so a release is asked
 * about again once a format has been learned since, and the note survives a restart. The notes are read once and
 * kept in memory, since the browser asks about every entry it draws.
 */
@Slf4j
public final class UnplayableReleases {

    private static final String UNPLAYABLE = "unplayable";

    private final CacheDirectory cache;
    private final String formats;
    private Set<Integer> releases;

    public UnplayableReleases(CacheDirectory cache, ModuleLoaderRegistry loaders) {
        this.cache = cache;
        this.formats = TrackResolver.knownFormats(loaders);
    }

    public boolean contains(int productionId) {
        return releases().contains(productionId);
    }

    void add(int productionId) throws IOException {
        cache.writeAtomically(cache.file(UNPLAYABLE, String.valueOf(productionId)),
                formats.getBytes(StandardCharsets.UTF_8));
        releases().add(productionId);
    }

    void remove(int productionId) throws IOException {
        releases().remove(productionId);
        Files.deleteIfExists(cache.root().resolve(UNPLAYABLE).resolve(String.valueOf(productionId)));
    }

    private synchronized Set<Integer> releases() {
        if (releases == null) {
            releases = ConcurrentHashMap.newKeySet();
            final Path notes = cache.root().resolve(UNPLAYABLE);
            if (Files.isDirectory(notes)) {
                try (Stream<Path> noted = Files.list(notes)) {
                    noted.filter(this::notedWithTodaysFormats).forEach(note -> releases.add(productionOf(note)));
                } catch (IOException | UncheckedIOException e) {
                    log.debug("Could not read the unplayable releases: {}", e.getMessage());
                }
            }
        }
        return releases;
    }

    private boolean notedWithTodaysFormats(Path note) {
        try {
            return note.getFileName().toString().chars().allMatch(Character::isDigit)
                    && formats.equals(Files.readString(note));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int productionOf(Path note) {
        return Integer.parseInt(note.getFileName().toString());
    }
}

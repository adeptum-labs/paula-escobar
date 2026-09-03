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

import com.adeptum.paula.cache.CacheDirectory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds the art among the files a release was downloaded with. Nothing is fetched for it: the art is whatever
 * came along in the archive of a release that has already been played.
 */
@Slf4j
public final class CachedReleaseArt implements ReleaseArt {

    private static final String FILES = "files";
    private static final String FILE_ID = "file_id.diz";
    private static final long LONGEST = 8192;
    private static final Duration RECHECK = Duration.ofSeconds(2);
    private static final char ESCAPE = 0x1B;

    private final CacheDirectory cache;
    private final Duration recheck;
    private final Clock clock;
    private final Map<Integer, List<String>> found = new ConcurrentHashMap<>();
    private final Map<Integer, Instant> missed = new ConcurrentHashMap<>();

    public CachedReleaseArt(CacheDirectory cache) {
        this(cache, RECHECK, Clock.systemUTC());
    }

    CachedReleaseArt(CacheDirectory cache, Duration recheck, Clock clock) {
        this.cache = cache;
        this.recheck = recheck;
        this.clock = clock;
    }

    /**
     * Art found stays found, while a release that had none is looked at again now and then, since it may be
     * downloaded while its competition is on the screen.
     */
    @Override
    public Optional<List<String>> of(int productionId) {
        final List<String> art = found.get(productionId);
        if (art != null) {
            return Optional.of(art);
        }
        final Instant lastLooked = missed.get(productionId);
        if (lastLooked != null && lastLooked.plus(recheck).isAfter(clock.instant())) {
            return Optional.empty();
        }
        final Optional<List<String>> read = read(productionId);
        read.ifPresentOrElse(lines -> found.put(productionId, lines), () -> missed.put(productionId, clock.instant()));
        return read;
    }

    private Optional<List<String>> read(int productionId) {
        final Path directory = cache.root().resolve(FILES).resolve(String.valueOf(productionId));
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(CachedReleaseArt::isArtFile)
                    .sorted(Comparator.comparing((Path file) -> isFileId(file) ? 0 : 1).thenComparing(Path::toString))
                    .map(TextArt::read)
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (IOException | UncheckedIOException e) {
            log.debug("No art for production {}: {}", productionId, e.toString());
            return Optional.empty();
        }
    }

    private static boolean isArtFile(Path file) {
        return TextArt.isArtName(file.getFileName().toString()) && file.toFile().length() <= LONGEST;
    }

    private static boolean isFileId(Path file) {
        return file.getFileName().toString().equalsIgnoreCase(FILE_ID);
    }
}

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

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/**
 * Brings a release down so the art it was packed with can be read, for a competition being looked at rather
 * than played. Each release is fetched once, in the background, and only where its files are not there yet, so
 * looking at a competition costs the one download that playing from it would have cost anyway.
 */
@Slf4j
public final class FetchingReleaseArt implements ReleaseArt {

    private final ReleaseArt art;
    private final TrackResolver resolver;
    private final Executor executor;
    private final Set<Integer> fetched = ConcurrentHashMap.newKeySet();
    private final Set<Integer> fetching = ConcurrentHashMap.newKeySet();

    public FetchingReleaseArt(ReleaseArt art, TrackResolver resolver, Executor executor) {
        this.art = art;
        this.resolver = resolver;
        this.executor = executor;
    }

    @Override
    public Optional<List<String>> of(int productionId) {
        return art.of(productionId);
    }

    @Override
    public boolean fetching(int productionId) {
        return fetching.contains(productionId);
    }

    @Override
    public void fetch(CompoEntry entry) {
        if (art.of(entry.productionId()).isPresent() || !fetched.add(entry.productionId())) {
            return;
        }
        fetching.add(entry.productionId());
        executor.execute(() -> {
            try {
                resolver.resolve(entry);
            } catch (IOException | RuntimeException e) {
                log.debug("No files for the art of {}: {}", entry.title(), e.getMessage());
            } finally {
                fetching.remove(entry.productionId());
            }
        });
    }
}

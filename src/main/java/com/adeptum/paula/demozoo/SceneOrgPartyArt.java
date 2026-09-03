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
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds the logo of a party among the files it left on scene.org. The party directory is read for a file id or
 * information file, the information directory first, since that is where a party keeps the logo that belongs to
 * the party as a whole rather than to one competition.
 */
@Slf4j
public final class SceneOrgPartyArt implements PartyArt {

    private static final Pattern SCENE_ORG_FOLDER = Pattern.compile("^https?://files\\.scene\\.org/(?:browse|view|get)/");
    private static final String SCENE_ORG_ARCHIVE = "https://archive.scene.org/pub/";
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"");
    private static final String INFORMATION = "info/";
    private static final String ART = "art";
    private static final String SUFFIX = ".diz";
    private static final int LONGEST = 8192;
    private static final int MOST_TRIED = 4;

    private final DemozooClient demozoo;
    private final HttpFetcher http;
    private final CacheDirectory cache;
    private final Executor executor;
    private final Map<Integer, Optional<List<String>>> known = new ConcurrentHashMap<>();
    private final Set<Integer> looked = ConcurrentHashMap.newKeySet();
    private final Set<Integer> looking = ConcurrentHashMap.newKeySet();

    public SceneOrgPartyArt(DemozooClient demozoo, HttpFetcher http, CacheDirectory cache, Executor executor) {
        this.demozoo = demozoo;
        this.http = http;
        this.cache = cache;
        this.executor = executor;
    }

    /**
     * Asked once a frame while a competition is on the screen, so what the cache holds is read from the disk
     * once and remembered, logo or no logo.
     */
    @Override
    public Optional<List<String>> of(int partyId) {
        return known.computeIfAbsent(partyId, this::cached);
    }

    @Override
    public void fetch(int partyId) {
        if (of(partyId).isPresent() || !looked.add(partyId)) {
            return;
        }
        looking.add(partyId);
        executor.execute(() -> {
            try {
                look(partyId);
            } catch (IOException | RuntimeException e) {
                log.debug("No logo for party {}: {}", partyId, e.getMessage());
            } finally {
                looking.remove(partyId);
            }
        });
    }

    @Override
    public boolean fetching(int partyId) {
        return looking.contains(partyId);
    }

    @Override
    public void forget(int partyId) {
        known.remove(partyId);
        looked.remove(partyId);
        try {
            Files.deleteIfExists(cache.file(ART, partyId + SUFFIX));
        } catch (IOException e) {
            log.debug("Could not forget the logo of party {}: {}", partyId, e.getMessage());
        }
    }

    private Optional<List<String>> cached(int partyId) {
        try {
            final Path file = cache.file(ART, partyId + SUFFIX);
            return Files.isRegularFile(file) ? TextArt.read(file) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void look(int partyId) throws IOException {
        final Optional<String> folder = demozoo.sceneOrgFolder(partyId);
        if (folder.isEmpty()) {
            return;
        }
        final URI directory = URI.create(SCENE_ORG_FOLDER.matcher(folder.get()).replaceFirst(SCENE_ORG_ARCHIVE));
        for (final URI candidate : candidates(directory)) {
            final byte[] body = http.get(candidate).body();
            final Optional<List<String>> art = body.length <= LONGEST ? TextArt.of(body) : Optional.empty();
            if (art.isPresent()) {
                known.put(partyId, art);
                cache.writeAtomically(cache.file(ART, partyId + SUFFIX), body);
                return;
            }
        }
    }

    /**
     * The information directory is read first and the party directory itself after it, so a logo for the party
     * comes before whatever a single competition left lying about.
     */
    private List<URI> candidates(URI directory) throws IOException {
        final List<String> listing = entries(directory);
        final List<URI> candidates = new ArrayList<>();
        if (listing.contains(INFORMATION)) {
            entries(directory.resolve(INFORMATION)).stream()
                    .filter(TextArt::isArtName)
                    .forEach(name -> candidates.add(directory.resolve(INFORMATION).resolve(name)));
        }
        listing.stream().filter(TextArt::isArtName).forEach(name -> candidates.add(directory.resolve(name)));
        return candidates.size() > MOST_TRIED ? candidates.subList(0, MOST_TRIED) : candidates;
    }

    private List<String> entries(URI directory) throws IOException {
        final String listing = new String(http.get(directory).body(), StandardCharsets.ISO_8859_1);
        final List<String> names = new ArrayList<>();
        final Matcher hrefs = HREF.matcher(listing);
        while (hrefs.find()) {
            final String name = hrefs.group(1);
            if (!name.startsWith("/") && !name.startsWith("?") && !name.startsWith("..") && !name.contains("://")) {
                names.add(name);
            }
        }
        return names;
    }
}

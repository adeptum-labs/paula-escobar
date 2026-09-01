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

import com.adeptum.paula.archive.ArchiveExtractor;
import com.adeptum.paula.archive.Archives;
import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Turns a competition entry into a playable file on disk: the production's best download is fetched into the
 * cache, unpacked when it is an archive, and the first module the loaders accept is returned.
 */
public final class TrackResolver {

    private static final String SCENE_ORG = "SceneOrgFile";
    private static final String MODARCHIVE = "ModarchiveModule";
    private static final String MODLAND = "ModlandFile";
    private static final Pattern SCENE_ORG_VIEW = Pattern.compile("^https?://files\\.scene\\.org/(?:view|get)/");
    private static final String SCENE_ORG_ARCHIVE = "https://archive.scene.org/pub/";
    private static final Pattern MODARCHIVE_ID = Pattern.compile("query=(\\d+)");
    private static final String MODARCHIVE_DOWNLOAD = "https://api.modarchive.org/downloads.php?moduleid=";
    private static final String FILES = "files";
    private static final String EXTRACTED = "extracted";
    private static final String DEFAULT_NAME = "download";
    private static final Set<String> UNUSABLE_NAMES = Set.of("", ".", "..");

    private final DemozooClient demozoo;
    private final HttpFetcher http;
    private final CacheDirectory cache;
    private final ModuleLoaderRegistry loaders;

    public TrackResolver(DemozooClient demozoo, HttpFetcher http, CacheDirectory cache, ModuleLoaderRegistry loaders) {
        this.demozoo = demozoo;
        this.http = http;
        this.cache = cache;
        this.loaders = loaders;
    }

    public Path resolve(CompoEntry entry) throws IOException {
        final Path directory = cache.directory(FILES, String.valueOf(entry.productionId()));
        final Optional<Path> cached = firstPlayable(directory);
        if (cached.isPresent()) {
            return cached.get();
        }
        final Production production = demozoo.production(entry.productionId());
        final Link link = preferredLink(production).orElseThrow(() -> new IOException("No download for " + entry.title()));
        final URI uri = downloadUri(link);
        final HttpFetcher.Response response = http.get(uri);
        final Path download = directory.resolve(fileName(response, uri));
        cache.writeAtomically(download, response.body());
        return playableFile(download, entry)
                .orElseThrow(() -> new IOException("No playable file in " + download.getFileName() + " for " + entry.title()));
    }

    /**
     * The scene.org file is the release as handed in at the party, so it wins over the copies on ModArchive and
     * Modland; any other download link is a last resort.
     */
    static Optional<Link> preferredLink(Production production) {
        return Stream.of(
                        withClass(production.downloads(), SCENE_ORG),
                        withClass(production.externals(), MODARCHIVE),
                        withClass(production.downloads(), MODLAND),
                        production.downloads().stream())
                .flatMap(links -> links)
                .findFirst();
    }

    static URI downloadUri(Link link) {
        return switch (link.linkClass()) {
            case SCENE_ORG -> URI.create(SCENE_ORG_VIEW.matcher(link.url()).replaceFirst(SCENE_ORG_ARCHIVE));
            case MODARCHIVE -> URI.create(MODARCHIVE_DOWNLOAD + firstGroup(MODARCHIVE_ID, link.url()));
            default -> URI.create(link.url());
        };
    }

    private static Stream<Link> withClass(List<Link> links, String linkClass) {
        return links.stream().filter(link -> link.linkClass().equals(linkClass));
    }

    private static String firstGroup(Pattern pattern, String text) {
        final Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * The server's suggestion and the URL are both untrusted, so only their last path element is ever used.
     */
    private static String fileName(HttpFetcher.Response response, URI uri) {
        final String suggested = response.fileName().orElseGet(() -> lastSegment(uri));
        final Path name = Path.of(suggested.replace('\\', '/')).getFileName();
        return name == null || UNUSABLE_NAMES.contains(name.toString()) ? DEFAULT_NAME : name.toString();
    }

    private static String lastSegment(URI uri) {
        final String path = uri.getPath() == null ? "" : uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private Optional<Path> playableFile(Path download, CompoEntry entry) throws IOException {
        final Optional<ArchiveExtractor> archive = Archives.detect(download);
        if (archive.isEmpty()) {
            if (loaders.loaderFor(download).isPresent()) {
                return Optional.of(download);
            }
            throw new IOException(download.getFileName() + " for " + entry.title() + " is not a module or archive");
        }
        archive.get().extract(download, download.resolveSibling(EXTRACTED), wantedEntry());
        return firstPlayable(download.resolveSibling(EXTRACTED));
    }

    private Predicate<String> wantedEntry() {
        return name -> loaders.loaderFor(Path.of(name)).isPresent();
    }

    /**
     * Files are chosen by name order, skipping archives so a download that merely looks like a module by name is
     * never handed to the loaders.
     */
    private Optional<Path> firstPlayable(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> loaders.loaderFor(file).isPresent())
                    .filter(TrackResolver::isPlainFile)
                    .min(Comparator.comparing(Path::toString));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static boolean isPlainFile(Path file) {
        try {
            return Files.size(file) > 0 && Archives.detect(file).isEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

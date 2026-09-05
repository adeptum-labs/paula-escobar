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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Where downloads lie in the cache. A party hands in a whole competition as one archive that every entry in it
 * points at, so a download is kept once under a name made from its address, and each production remembers
 * which download it was found in. A download is staged beside its final place and moved there whole, so a
 * directory that exists is one that arrived and unpacked to the end.
 */
final class DownloadCache {

    private static final String DOWNLOADS = "downloads";
    private static final String PRODUCTIONS = "productions";
    private static final String STAGING_PREFIX = "part-";
    private static final String DIGEST = "SHA-256";
    private static final int KEY_BYTES = 8;

    private final CacheDirectory cache;

    DownloadCache(CacheDirectory cache) {
        this.cache = cache;
    }

    Path directory(URI uri) {
        return cache.root().resolve(DOWNLOADS).resolve(key(uri));
    }

    Path staging() throws IOException {
        return Files.createTempDirectory(cache.directory(DOWNLOADS), STAGING_PREFIX);
    }

    /**
     * Two resolvers may bring down the same file at once; whichever moves in second is thrown away.
     */
    void commit(Path staging, Path directory) throws IOException {
        try {
            Files.move(staging, directory, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            if (!Files.isDirectory(directory)) {
                throw e;
            }
            discard(staging);
        }
    }

    void discard(Path staging) throws IOException {
        try (Stream<Path> tree = Files.walk(staging)) {
            for (final Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    Optional<Path> of(int productionId) throws IOException {
        final Path marker = cache.root().resolve(PRODUCTIONS).resolve(String.valueOf(productionId));
        if (!Files.isRegularFile(marker)) {
            return Optional.empty();
        }
        return Optional.of(cache.root().resolve(DOWNLOADS).resolve(Files.readString(marker).strip()))
                .filter(Files::isDirectory);
    }

    void remember(int productionId, Path directory) throws IOException {
        cache.writeAtomically(cache.file(PRODUCTIONS, String.valueOf(productionId)),
                directory.getFileName().toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String key(URI uri) {
        try {
            final byte[] digest = MessageDigest.getInstance(DIGEST).digest(uri.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, KEY_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

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

package com.adeptum.paula.demozoo.export;

import com.adeptum.paula.playback.Progress;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Demozoo's music read from a file rather than fetched: either the database export Demozoo publishes, converted
 * the first time it is asked for, or a catalogue already converted from one. Either way it is loaded once and
 * kept, since the file does not change under it.
 */
public final class FileCatalogue implements CatalogueSource {

    /**
     * What a file is turned into the catalogue with: a conversion when it is Demozoo's export, a read when it
     * already is a catalogue.
     */
    @FunctionalInterface
    private interface Loader {
        MusicCatalogue load(Path file) throws IOException;
    }

    private final Path file;
    private final Loader loader;
    private MusicCatalogue catalogue;

    private FileCatalogue(Path file, Loader loader) {
        this.file = file;
        this.loader = loader;
    }

    /**
     * Demozoo's full database export at the given path, converted on the first look.
     */
    public static FileCatalogue export(Path export) {
        return new FileCatalogue(export, file -> ExportConverter.convert(file, ExportConverter.Watcher.NONE));
    }

    /**
     * A catalogue already converted from Demozoo's export, read on the first look.
     */
    public static FileCatalogue catalogue(Path catalogue) {
        return new FileCatalogue(catalogue, FileCatalogue::read);
    }

    /**
     * Writes the catalogue and its checksum into a directory, made when it is not there, and answers the two
     * files written.
     */
    public static List<Path> write(MusicCatalogue catalogue, Path directory) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CatalogueFormat.write(catalogue, bytes);
        final byte[] written = bytes.toByteArray();
        final Path file = directory.resolve(CatalogueFormat.fileName());
        final Path checksum = directory.resolve(CatalogueFormat.checksumName());
        Files.createDirectories(directory);
        Files.write(file, written);
        Files.writeString(checksum, Checksums.line(Checksums.sha256(written), CatalogueFormat.fileName()),
                StandardCharsets.US_ASCII);
        return List.of(file, checksum);
    }

    @Override
    public synchronized MusicCatalogue catalogue() throws IOException {
        if (catalogue == null) {
            catalogue = loader.load(file);
        }
        return catalogue;
    }

    @Override
    public void checkSoon() {
        // The file does not change under it.
    }

    @Override
    public Optional<Progress.Step> preparing() {
        return Optional.empty();
    }

    private static MusicCatalogue read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return CatalogueFormat.read(in);
        }
    }
}

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

package com.adeptum.paula.module.flac;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import de.quippy.jflac.FLACDecoder;
import de.quippy.jflac.metadata.StreamInfo;
import de.quippy.jflac.metadata.VorbisComment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the lossless streams a music competition is handed in as when the entrant would rather not lose
 * anything to a lossy encoder.
 */
public final class FlacLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT = new ModuleFormat("flac", "FLAC", Set.of("flac"));

    private static final String SECONDS = "seconds";

    @Override
    public ModuleFormat format() {
        return FORMAT;
    }

    @Override
    public boolean supports(Path path) {
        return FORMAT.extensions().contains(ModuleFormat.extensionOf(path.getFileName().toString()));
    }

    @Override
    public Module load(Path path) throws IOException {
        final byte[] file = Files.readAllBytes(path);
        final FLACDecoder decoder = open(path, file);
        final FlacAudio audio = audioOf(path, decoder.getStreamInfo());
        return new FlacModule(path, metadata(decoder.getVorbisComment(), audio), file, audio);
    }

    private static FLACDecoder open(Path path, byte[] file) throws IOException {
        try {
            return FlacAudio.open(file);
        } catch (IOException | RuntimeException e) {
            throw new UnsupportedModuleException(path, "not FLAC audio");
        }
    }

    private static FlacAudio audioOf(Path path, StreamInfo info) throws UnsupportedModuleException {
        if (info == null) {
            throw new UnsupportedModuleException(path, "no FLAC stream info");
        }
        final FlacAudio audio = FlacAudio.of(info);
        if (!audio.isPlayable()) {
            throw new UnsupportedModuleException(path, "unplayable FLAC stream of " + audio.describe());
        }
        return audio;
    }

    /**
     * The tags a file carries about itself go where a module's credits would, since that is where the player
     * shows the lines a format offers beyond its title.
     */
    private static ModuleMetadata metadata(VorbisComment comment, FlacAudio audio) {
        final List<String> credits = new ArrayList<>();
        addUnlessBlank(credits, comment == null ? null : comment.getArtist());
        addUnlessBlank(credits, comment == null ? null : comment.getAlbum());
        addUnlessBlank(credits, comment == null ? null : comment.getDate());
        credits.add(audio.describe());
        return ModuleMetadata.builder()
                .title(comment == null ? "" : text(comment.getTitle()))
                .format(FORMAT)
                .channels(audio.channels())
                .songLength(audio.seconds())
                .lengthUnit(SECONDS)
                .credits(credits)
                .build();
    }

    private static void addUnlessBlank(List<String> credits, String line) {
        if (line != null && !line.isBlank()) {
            credits.add(line.trim());
        }
    }

    private static String text(String comment) {
        return comment == null ? "" : comment.trim();
    }
}

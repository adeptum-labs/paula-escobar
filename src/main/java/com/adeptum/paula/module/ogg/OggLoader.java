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
package com.adeptum.paula.module.ogg;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import de.quippy.ogg.jorbis.Comment;
import de.quippy.ogg.jorbis.JOrbisException;
import de.quippy.ogg.jorbis.VorbisFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the streams a music competition is handed in as when the entrant wants a lossy encoder that no patent
 * ever stood behind.
 */
@Slf4j
public final class OggLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT = new ModuleFormat("ogg", "Ogg Vorbis", Set.of("ogg", "oga"));

    private static final String SECONDS = "seconds";

    @Override
    public ModuleFormat format() {
        return FORMAT;
    }

    @Override
    public boolean supports(Path path) {
        return FORMAT.extensions().contains(ModuleFormat.extensionOf(path.getFileName().toString()));
    }

    /**
     * The headers and the last page are read straight off the disk, since that is what tells the stream its
     * length; the bytes the renderer decodes are kept separately so it never needs the file again.
     */
    @Override
    public Module load(Path path) throws IOException {
        final VorbisFile vorbis = open(path);
        try {
            final OggAudio audio = audioOf(path, vorbis);
            final ModuleMetadata metadata = metadata(tagsOf(vorbis), audio);
            return new OggModule(path, metadata, Files.readAllBytes(path), audio);
        } finally {
            close(path, vorbis);
        }
    }

    private static VorbisFile open(Path path) throws UnsupportedModuleException {
        try {
            return new VorbisFile(path.toString());
        } catch (JOrbisException | RuntimeException e) {
            throw new UnsupportedModuleException(path, "not Ogg Vorbis audio");
        }
    }

    private static OggAudio audioOf(Path path, VorbisFile vorbis) throws UnsupportedModuleException {
        if (vorbis.streams() < 1) {
            throw new UnsupportedModuleException(path, "no Vorbis stream");
        }
        final OggAudio audio = OggAudio.of(vorbis);
        if (!audio.isPlayable()) {
            throw new UnsupportedModuleException(path, "unplayable Vorbis stream of " + audio.describe());
        }
        return audio;
    }

    /**
     * A file whose encoder wrote no comment header at all still plays, only namelessly.
     */
    private static OggTags tagsOf(VorbisFile vorbis) {
        final Comment[] comments = vorbis.getComment();
        return OggTags.of(comments == null || comments.length == 0 ? null : comments[0]);
    }

    /**
     * The tags a file carries about itself go where a module's credits would, since that is where the player
     * shows the lines a format offers beyond its title.
     */
    private static ModuleMetadata metadata(OggTags tags, OggAudio audio) {
        final List<String> credits = new ArrayList<>();
        addUnlessBlank(credits, tags.artist());
        addUnlessBlank(credits, tags.album());
        addUnlessBlank(credits, tags.date());
        credits.add(audio.describe());
        return ModuleMetadata.builder()
                .title(tags.title())
                .format(FORMAT)
                .channels(audio.channels())
                .songLength(audio.seconds())
                .lengthUnit(SECONDS)
                .credits(credits)
                .build();
    }

    private static void addUnlessBlank(List<String> credits, String line) {
        if (!line.isBlank()) {
            credits.add(line);
        }
    }

    private static void close(Path path, VorbisFile vorbis) {
        try {
            vorbis.close();
        } catch (IOException | RuntimeException e) {
            log.debug("The Ogg Vorbis file {} cannot be closed", path, e);
        }
    }
}

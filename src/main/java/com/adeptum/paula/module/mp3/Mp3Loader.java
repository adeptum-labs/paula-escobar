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

package com.adeptum.paula.module.mp3;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import de.quippy.mp3.decoder.BitstreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the MPEG audio layers JavaMod's decoder covers, which is where a streaming music competition keeps its
 * entries.
 */
public final class Mp3Loader implements ModuleLoader {

    public static final ModuleFormat FORMAT = new ModuleFormat("mp3", "MPEG audio", Set.of("mp3", "mp2"));

    private static final String SECONDS = "seconds";

    @Override
    public ModuleFormat format() {
        return FORMAT;
    }

    @Override
    public boolean supports(Path path) {
        return FORMAT.matches(path.getFileName().toString());
    }

    @Override
    public Module load(Path path) throws IOException {
        final byte[] file = Files.readAllBytes(path);
        final int from = Id3.audioStart(file);
        final Mp3Audio audio = scan(path, file, from);
        return new Mp3Module(path, metadata(Id3.of(file), audio), file, from, audio);
    }

    private static Mp3Audio scan(Path path, byte[] file, int from) throws IOException {
        final Mp3Audio audio;
        try {
            audio = Mp3Audio.of(file, from);
        } catch (BitstreamException | RuntimeException e) {
            throw new UnsupportedModuleException(path, "not MPEG audio");
        }
        if (audio == null) {
            throw new UnsupportedModuleException(path, "no MPEG audio frames");
        }
        return audio;
    }

    /**
     * The tags a file carries about itself go where a module's credits would, since that is where the player
     * shows the lines a format offers beyond its title.
     */
    private static ModuleMetadata metadata(Id3 tags, Mp3Audio audio) {
        final List<String> credits = new ArrayList<>();
        addUnlessBlank(credits, tags.artist());
        addUnlessBlank(credits, tags.album());
        addUnlessBlank(credits, tags.year());
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
}

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

package com.adeptum.paula.module.wav;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.ModuleMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Reads the uncompressed recordings a competition entry is sometimes handed in as, whichever of the three
 * containers it was written by.
 */
public final class WavLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT = new ModuleFormat("wav", "Wave audio", Set.of("wav", "au", "aif"));

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
        final WavAudio audio = WavReader.read(path, file);
        return new WavModule(path, metadata(audio), file, audio);
    }

    /**
     * A wave file carries no tags of its own worth showing, so what it does say about itself goes where the
     * player looks for the lines a format offers beyond its title.
     */
    private static ModuleMetadata metadata(WavAudio audio) {
        return ModuleMetadata.builder()
                .format(FORMAT)
                .channels(audio.channels())
                .songLength(audio.seconds())
                .lengthUnit(SECONDS)
                .credits(List.of(audio.describe()))
                .build();
    }
}

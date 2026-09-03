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

package com.adeptum.paula.module.digibooster;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A loaded DigiBooster module, ready to be played. A module can hold several songs; the first one is the one
 * Paula plays.
 */
public record DigiBoosterModule(Path source, ModuleMetadata metadata, DbmFile file) implements Module {

    static DigiBoosterModule of(Path source, DbmFile file) {
        final ModuleMetadata metadata = ModuleMetadata.builder()
                .title(file.name().strip())
                .format(DigiBoosterLoader.FORMAT)
                .channels(file.tracks())
                .songLength(file.songs().getFirst().playList().length)
                .instruments(file.instruments().stream().map(instrument -> instrument.name().strip()).toList())
                .credits(credits(file))
                .build();
        return new DigiBoosterModule(source, metadata, file);
    }

    @Override
    public Renderer createRenderer(int sampleRate) {
        return new DigiBoosterRenderer(file, sampleRate);
    }

    private static List<String> credits(DbmFile file) {
        final List<String> credits = new ArrayList<>();
        credits.add(file.creator());
        file.songs().stream().map(song -> song.name().strip()).filter(name -> !name.isEmpty()).forEach(credits::add);
        return credits;
    }
}

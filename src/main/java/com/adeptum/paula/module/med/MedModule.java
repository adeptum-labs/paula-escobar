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

package com.adeptum.paula.module.med;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One OctaMED module, ready to play.
 */
public record MedModule(Path source, ModuleMetadata metadata, MedFile file) implements Module {

    private static final String LINES = "blocks";

    static MedModule of(Path source, MedFile file) {
        return new MedModule(source, metadata(file), file);
    }

    @Override
    public Renderer createRenderer(int sampleRate) {
        return new MedRenderer(file, sampleRate);
    }

    /**
     * The annotation the musician left goes where the credits of other formats do, ahead of the instrument
     * names, since that is where the player shows what a module has to say for itself.
     */
    private static ModuleMetadata metadata(MedFile file) {
        final List<String> credits = new ArrayList<>();
        if (!file.comment().isBlank()) {
            credits.add(file.comment().strip());
        }
        return ModuleMetadata.builder()
                .title(file.name())
                .format(MedLoader.FORMAT)
                .channels(file.tracks())
                .songLength(file.song().length())
                .lengthUnit(LINES)
                .credits(credits)
                .instruments(instrumentNames(file))
                .build();
    }

    private static List<String> instrumentNames(MedFile file) {
        return file.instruments().stream().map(MedInstrument::name).toList();
    }
}

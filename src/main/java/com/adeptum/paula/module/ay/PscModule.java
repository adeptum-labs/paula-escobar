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


package com.adeptum.paula.module.ay;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;
import java.util.List;

/**
 * One module of Pro Sound Creator, ready to play.
 */
public record PscModule(Path source, ModuleMetadata metadata, PscFile file) implements Module {

    private static final String POSITIONS = "positions";

    static PscModule of(Path source, PscFile file) {
        return new PscModule(source, metadata(file), file);
    }

    @Override
    public Renderer createRenderer(int sampleRate) {
        return new AyRenderer(new PscEngine(file), AyChip.Voicing.AY, RegisterFrames.SPECTRUM_CLOCK,
                RegisterFrames.INTERRUPTS_A_SECOND, sampleRate);
    }

    private static ModuleMetadata metadata(PscFile file) {
        return ModuleMetadata.builder()
                .title(file.title())
                .format(ProSoundCreatorLoader.FORMAT)
                .channels(PscFile.CHANNELS)
                .songLength(file.positions().length)
                .lengthUnit(POSITIONS)
                .credits(file.author().isBlank() ? List.of() : List.of(file.author()))
                .build();
    }
}

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

package com.adeptum.paula.module.xtracker;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;
import java.util.List;

/**
 * One X-Tracker module, ready to play.
 */
public record XTrackerModule(Path source, ModuleMetadata metadata, DmfFile file) implements Module {

    private static final String POSITIONS = "positions";

    static XTrackerModule of(Path source, DmfFile file) {
        return new XTrackerModule(source, metadata(file), file);
    }

    @Override
    public Renderer createRenderer(int sampleRate) {
        return new DmfRenderer(file, sampleRate);
    }

    private static ModuleMetadata metadata(DmfFile file) {
        return ModuleMetadata.builder()
                .title(file.title())
                .format(XTrackerLoader.FORMAT)
                .channels(file.tracks())
                .songLength(file.orders().length)
                .lengthUnit(POSITIONS)
                .credits(file.composer().isBlank() ? List.of() : List.of(file.composer()))
                .instruments(file.samples().stream().map(DmfSample::name).toList())
                .build();
    }
}

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

package com.adeptum.paula.module.composer669;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;
import java.util.List;

/**
 * One Composer 669 or UNIS 669 module, ready to play.
 */
public record Composer669Module(Path source, ModuleMetadata metadata, C669File file) implements Module {

    private static final String POSITIONS = "positions";

    static Composer669Module of(Path source, C669File file) {
        return new Composer669Module(source, metadata(file), file);
    }

    @Override
    public Renderer createRenderer(int sampleRate) {
        return new C669Renderer(file, sampleRate);
    }

    /**
     * The tracker has no title, only a message of three lines; the first goes where the title would and the
     * others where the credits do.
     */
    private static ModuleMetadata metadata(C669File file) {
        final List<String> message = file.message();
        return ModuleMetadata.builder()
                .title(file.title())
                .format(Composer669Loader.FORMAT)
                .channels(C669Engine.VOICES)
                .songLength(file.orders().length)
                .lengthUnit(POSITIONS)
                .credits(message.subList(1, message.size()).stream().filter(line -> !line.isBlank()).toList())
                .instruments(file.samples().stream().map(C669Sample::name).toList())
                .build();
    }
}

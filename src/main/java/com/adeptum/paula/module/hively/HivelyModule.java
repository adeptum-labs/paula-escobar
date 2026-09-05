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

package com.adeptum.paula.module.hively;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;
import java.util.List;

/**
 * A loaded AHX or HivelyTracker tune, ready to be played. The file names nothing but the tune itself, so the
 * module has no credits to show.
 */
public record HivelyModule(Path source, ModuleMetadata metadata, HvlTune tune) implements Module {

    static HivelyModule of(Path source, HvlTune tune) {
        final ModuleMetadata metadata = ModuleMetadata.builder()
                .title(tune.name().strip())
                .format(HivelyLoader.FORMAT)
                .channels(tune.channels())
                .songLength(tune.positionCount())
                .instruments(instruments(tune))
                .build();
        return new HivelyModule(source, metadata, tune);
    }

    @Override
    public Renderer createRenderer(int sampleRate) {
        return new HivelyRenderer(tune, sampleRate);
    }

    /**
     * The first instrument is the empty one the replayer keeps so that notes can number theirs from one, and
     * is none of the tune's own.
     */
    private static List<String> instruments(HvlTune tune) {
        return tune.instruments().stream().skip(1).map(instrument -> instrument.name().strip()).toList();
    }
}

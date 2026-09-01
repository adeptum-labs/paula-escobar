/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

package com.adeptum.paula.module.javamod;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.playback.javamod.JavaModRenderer;
import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record JavaModModule(
        Path source,
        ModuleMetadata metadata,
        de.quippy.javamod.multimedia.mod.loader.Module tracker) implements Module {

    static JavaModModule of(Path source, de.quippy.javamod.multimedia.mod.loader.Module tracker) {
        final ModuleMetadata metadata = ModuleMetadata.builder()
                .title(tracker.getSongName().strip())
                .format(format(source, tracker))
                .channels(tracker.getNChannels())
                .songLength(tracker.getSongLength())
                .instruments(instrumentNames(tracker.getInstrumentContainer()))
                .build();
        return new JavaModModule(source, metadata, tracker);
    }

    @Override
    public Renderer createRenderer(int sampleRate) {
        return new JavaModRenderer(tracker, sampleRate);
    }

    private static ModuleFormat format(Path source, de.quippy.javamod.multimedia.mod.loader.Module tracker) {
        final String extension = JavaModLoader.extension(source.getFileName().toString().toLowerCase(Locale.ROOT));
        return new ModuleFormat(tracker.getModID().strip().toLowerCase(Locale.ROOT), tracker.getTrackerName().strip(), Set.of(extension));
    }

    private static List<String> instrumentNames(InstrumentsContainer container) {
        if (container == null) {
            return List.of();
        }
        return container.hasInstruments()
                ? Arrays.stream(container.getInstruments()).map(instrument -> instrument == null ? null : instrument.name).map(JavaModModule::clean).toList()
                : Arrays.stream(container.getSamples()).map(sample -> sample == null ? null : sample.name).map(JavaModModule::clean).toList();
    }

    private static String clean(String name) {
        return Objects.requireNonNullElse(name, "").strip();
    }
}

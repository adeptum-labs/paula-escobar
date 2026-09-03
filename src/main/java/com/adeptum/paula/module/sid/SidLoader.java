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

package com.adeptum.paula.module.sid;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import de.quippy.sidplay.libsidplay.components.sidtune.SidTune;
import de.quippy.sidplay.libsidplay.components.sidtune.SidTuneInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads Commodore 64 SID tunes through the libsidplay2 port bundled with JavaMod.
 */
@Slf4j
public final class SidLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT =
            new ModuleFormat("sid", "Commodore 64 SID and programs (libsidplay2)", Set.of("sid", "psid", "rsid", "prg", "c64"));

    /**
     * A program has no header to say how it is played, so the engine runs it as the C64 would; it knows those
     * files by their name rather than their contents, which is why they are loaded from their path.
     */
    public static final Set<String> PROGRAMS = Set.of("prg", "c64");

    private static final int VOICES_PER_CHIP = 3;
    private static final int NAME = 0;
    private static final int AUTHOR = 1;
    private static final int RELEASED = 2;
    private static final String SUBTUNES = "subtunes";
    private static final String DEFAULT_FORMAT_NAME = "SID";

    private final SongLengths lengths;

    public SidLoader(SongLengths lengths) {
        this.lengths = lengths;
    }

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
        final SidTune tune;
        try {
            tune = tune(path, file);
        } catch (RuntimeException e) {
            log.debug("The SID engine rejected {}", path, e);
            throw new UnsupportedModuleException(path, "not a SID file");
        }
        if (!tune.getStatus()) {
            throw new UnsupportedModuleException(path, tune.getInfo().statusString);
        }
        final SidTuneInfo info = tune.getInfo();
        final int subtune = Math.max(1, info.startSong);
        return new SidModule(path, metadata(info), file, subtune, lengths.lengthOf(file, subtune));
    }

    static SidTune tune(Path path, byte[] file) {
        if (PROGRAMS.contains(ModuleFormat.extensionOf(path.getFileName().toString()))) {
            return new SidTune(path.toString(), null);
        }
        final short[] unsigned = new short[file.length];
        for (int i = 0; i < file.length; i++) {
            unsigned[i] = (short) (file[i] & 0xFF);
        }
        return new SidTune(unsigned, file.length);
    }

    private static ModuleMetadata metadata(SidTuneInfo info) {
        final String[] strings = info.infoString == null ? new String[0] : info.infoString;
        final int chips = info.sidChipBase2 > 0 ? 2 : 1;
        return ModuleMetadata.builder()
                .title(string(strings, NAME))
                .format(new ModuleFormat(FORMAT.id(), Objects.requireNonNullElse(info.formatString, DEFAULT_FORMAT_NAME), FORMAT.extensions()))
                .channels(VOICES_PER_CHIP * chips)
                .songLength(info.songs)
                .lengthUnit(SUBTUNES)
                .credits(Arrays.stream(new String[] {string(strings, AUTHOR), string(strings, RELEASED)}).filter(s -> !s.isBlank()).toList())
                .build();
    }

    private static String string(String[] strings, int index) {
        return index < strings.length && strings[index] != null ? strings[index].strip() : "";
    }
}

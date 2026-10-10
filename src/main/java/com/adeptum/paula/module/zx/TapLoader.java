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

package com.adeptum.paula.module.zx;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Loads a ZX Spectrum tape by running its program. The music engines on tapes are Z80 code, so the tape is
 * played by emulating the machine around it; a tape that makes no sound, or needs more of the ROM than the
 * engines ever do, is refused with the reason.
 */
public final class TapLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT =
            new ModuleFormat("tap", "ZX Spectrum tapes (Z80 emulation)", Set.of("tap"));

    private static final String BEEPER = "ZX Spectrum tape (48K beeper)";
    private static final String BEEPER_AND_AY = "ZX Spectrum tape (128K AY and beeper)";
    private static final int BEEPER_CHANNELS = 1;
    private static final int BEEPER_AND_AY_CHANNELS = 4;
    private static final String SECONDS = "seconds";

    private final TapeLengths lengths;

    TapLoader(TapeLengths lengths) {
        this.lengths = lengths;
    }

    public static TapLoader inUserCache() {
        return new TapLoader(TapeLengths.inUserCache());
    }

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
        final byte[] bytes = Files.readAllBytes(path);
        final TapProgram program = TapProgram.of(path, TapReader.read(path, bytes));
        final TapeRun run = lengths.of(bytes, program);
        if (!run.audible()) {
            throw new UnsupportedModuleException(path,
                    run.reason() == EndReason.ROM_CALL ? "the program needs the Spectrum ROM" : "the program makes no sound");
        }
        return new TapModule(path, metadata(program, run), program, run);
    }

    private static ModuleMetadata metadata(TapProgram program, TapeRun run) {
        final boolean extended = run.model() == Spectrum.Model.K128;
        return ModuleMetadata.builder()
                .title(program.title())
                .format(new ModuleFormat(FORMAT.id(), extended ? BEEPER_AND_AY : BEEPER, FORMAT.extensions()))
                .channels(extended ? BEEPER_AND_AY_CHANNELS : BEEPER_CHANNELS)
                .songLength((int) run.length().toSeconds())
                .lengthUnit(SECONDS)
                .build();
    }
}


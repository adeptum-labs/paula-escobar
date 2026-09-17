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
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.UnsupportedModuleException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Loads the modules of Pro Sound Creator, a ZX Spectrum tracker of the late nineties whose modules turn up in
 * the AY music competitions of the parties of the time. The tune is played by working out what the Spectrum's
 * interrupt would have written to the chip.
 */
public final class ProSoundCreatorLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT =
            new ModuleFormat("psc", "ZX Spectrum Pro Sound Creator modules", Set.of("psc"));

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
        try {
            return PscModule.of(path, PscReader.read(Files.readAllBytes(path)));
        } catch (IOException e) {
            throw new UnsupportedModuleException(path, e.getMessage());
        }
    }
}

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
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.UnsupportedModuleException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Loads the modules of Composer 669, the eight-channel PC tracker of 1992 that counted in hertz, and of UNIS
 * 669, which took it on with two effects more.
 */
public final class Composer669Loader implements ModuleLoader {

    public static final ModuleFormat FORMAT = new ModuleFormat("669", "Composer 669 modules", Set.of("669"));

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
        final byte[] file = Files.readAllBytes(path);
        try {
            return Composer669Module.of(path, C669Reader.read(file));
        } catch (IOException e) {
            throw new UnsupportedModuleException(path, e.getMessage());
        }
    }
}

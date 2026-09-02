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

package com.adeptum.paula.module.digibooster;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Loads the modules of DigiBooster Pro 2 and DigiBooster 3, the Amiga trackers whose modules the rest of the
 * players never learned to read.
 */
public final class DigiBoosterLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT = new ModuleFormat("dbm", "DigiBooster modules", Set.of("dbm"));

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
        return DigiBoosterModule.of(path, DbmReader.read(Files.readAllBytes(path)));
    }
}

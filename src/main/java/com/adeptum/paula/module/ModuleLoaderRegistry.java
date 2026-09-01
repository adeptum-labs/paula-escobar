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

package com.adeptum.paula.module;

import com.adeptum.paula.module.javamod.JavaModLoader;
import com.adeptum.paula.module.sid.SidLoader;
import com.adeptum.paula.module.sid.SongLengths;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class ModuleLoaderRegistry {

    private final List<ModuleLoader> loaders;

    public ModuleLoaderRegistry(List<ModuleLoader> loaders) {
        this.loaders = List.copyOf(loaders);
    }

    public static ModuleLoaderRegistry withBuiltInLoaders(SongLengths sidLengths) {
        return new ModuleLoaderRegistry(List.of(new JavaModLoader(), new SidLoader(sidLengths)));
    }

    public List<ModuleFormat> formats() {
        return loaders.stream().map(ModuleLoader::format).toList();
    }

    public Optional<ModuleLoader> loaderFor(Path path) {
        return loaders.stream().filter(loader -> loader.supports(path)).findFirst();
    }

    public Module load(Path path) throws IOException {
        return loaderFor(path).orElseThrow(() -> new UnsupportedModuleException(path)).load(path);
    }
}

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

package com.adeptum.paula.module.javamod;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.UnsupportedModuleException;
import de.quippy.javamod.multimedia.mod.loader.ModuleFactory;
import de.quippy.javamod.multimedia.mod.loader.tracker.FarandoleTrackerMod;
import de.quippy.javamod.multimedia.mod.loader.tracker.ImpulseTrackerMod;
import de.quippy.javamod.multimedia.mod.loader.tracker.MultiTrackerMod;
import de.quippy.javamod.multimedia.mod.loader.tracker.ProTrackerMod;
import de.quippy.javamod.multimedia.mod.loader.tracker.ScreamTrackerMod;
import de.quippy.javamod.multimedia.mod.loader.tracker.ScreamTrackerOldMod;
import de.quippy.javamod.multimedia.mod.loader.tracker.ScreamTrackerSTXMod;
import de.quippy.javamod.multimedia.mod.loader.tracker.XMMod;
import de.quippy.javamod.system.Log;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads every tracker format JavaMod understands: ProTracker, FastTracker, Scream Tracker, Impulse Tracker and more.
 */
public final class JavaModLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT;

    /**
     * JavaMod's loaders register themselves with the factory from their static initialisers, so they must be
     * initialised explicitly before the factory knows any format.
     */
    private static final List<Class<?>> TRACKER_LOADERS = List.of(
            ProTrackerMod.class, XMMod.class, ScreamTrackerOldMod.class, ScreamTrackerSTXMod.class,
            ScreamTrackerMod.class, ImpulseTrackerMod.class, FarandoleTrackerMod.class, MultiTrackerMod.class);

    static {
        Log.setLogLevel(Log.LOGLEVEL_NONE);
        TRACKER_LOADERS.forEach(JavaModLoader::initialise);
        FORMAT = new ModuleFormat("tracker", "Tracker modules (JavaMod)", Set.of(ModuleFactory.getSupportedFileExtensions()));
    }

    private static void initialise(Class<?> loader) {
        try {
            MethodHandles.lookup().ensureInitialized(loader);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot initialise JavaMod loader " + loader.getName(), e);
        }
    }

    @Override
    public ModuleFormat format() {
        return FORMAT;
    }

    @Override
    public boolean supports(Path path) {
        final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return FORMAT.extensions().contains(ModuleFormat.extensionOf(name)) || name.startsWith("mod.");
    }

    @Override
    public Module load(Path path) throws IOException {
        try {
            return JavaModModule.of(path, ModuleFactory.getInstance(path.toFile()));
        } catch (IOException e) {
            throw new UnsupportedModuleException(path, rootMessage(e));
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}

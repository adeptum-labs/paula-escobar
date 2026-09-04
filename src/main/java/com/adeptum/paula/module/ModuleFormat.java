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

package com.adeptum.paula.module;

import java.util.Locale;
import java.util.Set;

public record ModuleFormat(String id, String name, Set<String> extensions) {

    public ModuleFormat {
        extensions = Set.copyOf(extensions);
    }

    /**
     * The archives that keep a whole scene collection, Modland and AMP among them, name a file after the
     * format it holds rather than after itself: MOD.tune, not tune.mod. Both readings count.
     */
    public boolean matches(String fileName) {
        return extensions.contains(extensionOf(fileName)) || extensions.contains(prefixOf(fileName));
    }

    /**
     * The lower-cased text after the last dot, or nothing when the name has no dot.
     */
    public static String extensionOf(String fileName) {
        final int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String prefixOf(String fileName) {
        final int dot = fileName.indexOf('.');
        return dot < 1 ? "" : fileName.substring(0, dot).toLowerCase(Locale.ROOT);
    }
}

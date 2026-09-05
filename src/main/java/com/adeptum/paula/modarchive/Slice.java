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

package com.adeptum.paula.modarchive;

import java.util.Locale;
import java.util.stream.Stream;

/**
 * A chart cut by file format; the multichannel trackers each have a slice of their own.
 */
public enum Slice {

    ALL("All"), MOD("MOD"), XM("XM"), IT("IT"), S3M("S3M"), OTHER("Other");

    private final String label;

    Slice(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean holds(ChartEntry entry) {
        return switch (this) {
            case ALL -> true;
            case OTHER -> Stream.of(MOD, XM, IT, S3M).noneMatch(slice -> slice.holds(entry));
            default -> name().toLowerCase(Locale.ROOT).equals(entry.extension());
        };
    }
}

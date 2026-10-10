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

package com.adeptum.paula.demozoo;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One of the versions of a tune that a release hands in, such as the tape, the tracker module and a recording
 * of them, and whether it is the one that plays.
 */
public record Variant(Path file, String format, boolean plays) {

    public static final int FEWEST_FORMATS = 2;
    private static final String SEPARATOR = "/";
    private static final String PLAYING_OPEN = "[";
    private static final String PLAYING_CLOSE = "]";

    /**
     * The formats a release offers in the order they are tried, the one that plays in brackets; nothing for a
     * release that offers no choice.
     */
    public static String tag(List<Variant> variants) {
        return variants.size() < FEWEST_FORMATS ? "" : variants.stream()
                .map(variant -> variant.plays ? PLAYING_OPEN + variant.format + PLAYING_CLOSE : variant.format)
                .collect(Collectors.joining(SEPARATOR));
    }
}

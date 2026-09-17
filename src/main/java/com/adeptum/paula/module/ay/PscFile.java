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

/**
 * A module of Pro Sound Creator: its tempo, the position the order loops back to, its samples, ornaments,
 * patterns and the order they come in.
 */
public record PscFile(int tempo, int loop, String title, String author, int[] positions, PscSample[] samples,
        PscOrnament[] ornaments, PscPattern[] patterns) {

    public static final int CHANNELS = 3;
    public static final int MOST_SAMPLES = 32;
    public static final int MOST_ORNAMENTS = 32;
}

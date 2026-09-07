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

package com.adeptum.paula.module.med;

/**
 * The song as the file holds it: which blocks are played in what order, how fast, and how loud and where each
 * of its tracks sounds.
 */
record MedSong(int[] playSeq, int tempo, int tempo2, int transpose, int flags, int flags2, int masterVolume,
               int[] trackVolumes, int[] trackPans) {

    static final int FLAG_VOLUMES_ARE_HEX = 0x10;
    static final int FLAG_TRACKER_SLIDES = 0x20;
    static final int FLAG_EIGHT_CHANNEL = 0x40;

    static final int FLAG2_BEAT_MASK = 0x1F;
    static final int FLAG2_BPM = 0x20;
    static final int FLAG2_MIXING = 0x80;

    private static final int DEFAULT_BEAT_LINES = 4;

    boolean isEightChannel() {
        return (flags & FLAG_EIGHT_CHANNEL) != 0;
    }

    boolean isBpm() {
        return (flags2 & FLAG2_BPM) != 0;
    }

    boolean volumesAreHex() {
        return (flags & FLAG_VOLUMES_ARE_HEX) != 0;
    }

    /**
     * How many lines make a beat when the tempo is given in beats a minute, which is what turns that tempo
     * into a rate of lines.
     */
    int beatLines() {
        final int lines = flags2 & FLAG2_BEAT_MASK;
        return lines == 0 ? DEFAULT_BEAT_LINES : lines;
    }

    int length() {
        return playSeq.length;
    }
}

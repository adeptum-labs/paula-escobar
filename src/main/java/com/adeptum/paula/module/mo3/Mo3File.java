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

package com.adeptum.paula.module.mo3;

import java.util.List;

/**
 * Everything an MO3 holds: what it says about itself, the order it plays its patterns in, the tracks those
 * patterns are made of, and the instruments and samples that sound them. The waveforms stay where they were
 * found, since they are packed by schemes of their own.
 */
record Mo3File(int version, String name, String message, Mo3Song song, int[] orders, List<Mo3Pattern> patterns,
               List<byte[]> tracks, List<Mo3Instrument> instruments, List<Mo3Sample> samples,
               byte[] file, int sampleData) {

    /**
     * An order that names no pattern: one to skip over and one to stop at, as Impulse Tracker wrote them.
     */
    static final int ORDER_SKIP = 0xFF;
    static final int ORDER_STOP = 0xFE;

    Mo3Kind kind() {
        return song.kind();
    }

    /**
     * Whether the two orders that name no pattern mean what Impulse Tracker meant by them. ProTracker and Fast
     * Tracker have patterns of those numbers, so there they are ordinary orders.
     */
    boolean hasOrderSeparators() {
        return kind() != Mo3Kind.PROTRACKER && kind() != Mo3Kind.FAST_TRACKER;
    }

    /**
     * Whether the module reaches its samples through instruments. Fast Tracker always does; the others only
     * when they say so.
     */
    boolean hasInstruments() {
        return kind() == Mo3Kind.FAST_TRACKER || song.has(Mo3Song.INSTRUMENT_MODE);
    }
}

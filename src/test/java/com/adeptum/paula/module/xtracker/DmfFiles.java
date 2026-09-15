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

package com.adeptum.paula.module.xtracker;

import java.util.Arrays;
import java.util.List;

/**
 * Modules built straight from the records, so an engine test states the one entry it is about.
 */
final class DmfFiles {

    static final int SAMPLE_RATE = 48000;
    static final int C3 = 8363;
    static final int C3_NOTE = 37;
    static final int ROW_FRAMES = SAMPLE_RATE / 8;
    static final int ROW = DmfTempo.UNITS_PER_ROW;
    static final int SAMPLE_LENGTH = 4096;
    static final int HIGH = 16000;
    static final int ROWS = 16;
    private static final int NONE = DmfTrackEntry.NONE;

    private DmfFiles() {
    }

    /**
     * A square looping over its whole length, high for the first half and low for the second.
     */
    static DmfSample square(int volume) {
        final short[] data = new short[SAMPLE_LENGTH];
        for (int at = 0; at < data.length; at++) {
            data[at] = (short) (at < data.length / 2 ? HIGH : -HIGH);
        }
        return new DmfSample("square", data, 0, SAMPLE_LENGTH, C3, volume, false);
    }

    /**
     * A sample without a loop whose every frame holds its own index, so a position can be read back from the
     * sound.
     */
    static DmfSample ramp() {
        final short[] data = new short[SAMPLE_LENGTH];
        for (int at = 0; at < data.length; at++) {
            data[at] = (short) at;
        }
        return new DmfSample("ramp", data, 0, 0, C3, 0, false);
    }

    /**
     * The ramp as if it had been stored in 16 bits, so its offsets count two bytes a frame.
     */
    static DmfSample wide() {
        return new DmfSample("wide", ramp().data(), 0, 0, C3, 0, true);
    }

    /**
     * Mixes whole rows, which leaves the engine at the start of the next one.
     */
    static void playRows(DmfEngine engine, int count) {
        engine.mix(new short[count * ROW_FRAMES * 2], count * ROW_FRAMES);
    }

    static DmfTrackEntry note(int note, int instrument) {
        return new DmfTrackEntry(instrument, note, NONE, NONE, 0, NONE, 0, NONE, 0);
    }

    static DmfTrackEntry noteOff() {
        return note(DmfTrackEntry.NOTE_OFF, NONE);
    }

    static DmfTrackEntry instrumentOnly(int instrument) {
        return note(NONE, instrument);
    }

    static DmfTrackEntry empty() {
        return note(NONE, NONE);
    }

    static DmfTrackEntry withVolume(DmfTrackEntry entry, int volume) {
        return new DmfTrackEntry(entry.instrument(), entry.note(), volume, entry.instrumentEffect(),
                entry.instrumentData(), entry.noteEffect(), entry.noteData(), entry.volumeEffect(), entry.volumeData());
    }

    static DmfTrackEntry instrumentEffect(DmfTrackEntry entry, int effect, int data) {
        return new DmfTrackEntry(entry.instrument(), entry.note(), entry.volume(), effect, data, entry.noteEffect(),
                entry.noteData(), entry.volumeEffect(), entry.volumeData());
    }

    static DmfTrackEntry noteEffect(DmfTrackEntry entry, int effect, int data) {
        return new DmfTrackEntry(entry.instrument(), entry.note(), entry.volume(), entry.instrumentEffect(),
                entry.instrumentData(), effect, data, entry.volumeEffect(), entry.volumeData());
    }

    static DmfTrackEntry volumeEffect(DmfTrackEntry entry, int effect, int data) {
        return new DmfTrackEntry(entry.instrument(), entry.note(), entry.volume(), entry.instrumentEffect(),
                entry.instrumentData(), entry.noteEffect(), entry.noteData(), effect, data);
    }

    /**
     * One track playing one pattern of sixteen rows, its entries on the first rows in the order given; a null
     * leaves a row without an entry. Instrument one is a square at volume 0, instrument two the ramp.
     */
    static DmfFile track(DmfTrackEntry... rows) {
        final DmfTrackEntry[][] columns = new DmfTrackEntry[rows.length][];
        for (int row = 0; row < rows.length; row++) {
            columns[row] = new DmfTrackEntry[] {rows[row]};
        }
        return tracks(columns);
    }

    /**
     * Several tracks in one pattern of sixteen rows: {@code rows[row][track]}.
     */
    static DmfFile tracks(DmfTrackEntry[]... rows) {
        final int tracks = rows[0].length;
        final DmfTrackEntry[][] entries = new DmfTrackEntry[ROWS][tracks];
        for (int row = 0; row < rows.length; row++) {
            entries[row] = Arrays.copyOf(rows[row], tracks);
        }
        final DmfPattern pattern = new DmfPattern(tracks, 0, new DmfGlobalEntry[ROWS], entries);
        return new DmfFile(8, "", "", tracks, new int[] {0}, List.of(pattern), List.of(square(0), ramp(), wide()));
    }

    static DmfEngine engine(DmfFile file) {
        return new DmfEngine(file, SAMPLE_RATE);
    }

    static void units(DmfEngine engine, int count) {
        for (int unit = 0; unit < count; unit++) {
            engine.nextUnit();
        }
    }

    static void rows(DmfEngine engine, int count) {
        units(engine, count * ROW);
    }
}

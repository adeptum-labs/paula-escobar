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

package com.adeptum.paula.testing;

import java.nio.charset.StandardCharsets;

/**
 * Builds the smallest ST Song Compiler module that still plays: one sample, one ornament, one pattern of a
 * note, a wait, a rest and an envelope, and two positions so that a transposition is exercised.
 */
public final class TestStcs {

    public static final String TITLE = "PAULA TEST STC";
    public static final int TEMPO = 5;
    public static final int NOTE = 24;
    public static final int SAMPLE = 3;
    public static final int ORNAMENT = 2;
    public static final int ORNAMENT_STEP = 12;
    public static final int TRANSPOSITION = -5;
    public static final int LEVEL = 13;
    public static final int NOISE = 9;
    public static final int EFFECT = 0x123;
    public static final int SAMPLE_LOOP = 4;
    public static final int ENVELOPE = 3;
    public static final int ENVELOPE_PERIOD = 0x40;
    public static final int WAIT = 1;

    private static final int HEADER_SIZE = 27;
    private static final int SAMPLES = 8;
    private static final int SAMPLE_BYTES = 99;
    private static final int ORNAMENTS = 4;
    private static final int ORNAMENT_BYTES = 33;
    private static final int LINES = 32;
    private static final int PATTERN_BYTES = 7;
    private static final int POSITIONS = 2;

    private TestStcs() {
    }

    public static byte[] stc() {
        final int positionsAt = HEADER_SIZE + SAMPLES * SAMPLE_BYTES;
        final int ornamentsAt = positionsAt + 1 + POSITIONS * 2;
        final int patternsAt = ornamentsAt + ORNAMENTS * ORNAMENT_BYTES;
        final int channelsAt = patternsAt + PATTERN_BYTES * 2;
        final byte[] first = firstChannel();
        final byte[] second = secondChannel();
        final int size = channelsAt + first.length + second.length + 1;
        final byte[] file = new byte[size];

        file[0] = TEMPO;
        putShort(file, 1, positionsAt);
        putShort(file, 3, ornamentsAt);
        putShort(file, 5, patternsAt);
        final byte[] title = String.format("%-18s", TITLE).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, file, 7, 18);
        putShort(file, 25, size);

        writeSample(file, HEADER_SIZE + SAMPLE * SAMPLE_BYTES);
        writePositions(file, positionsAt);
        writeOrnament(file, ornamentsAt + ORNAMENT * ORNAMENT_BYTES);
        writePattern(file, patternsAt, channelsAt, first.length, second.length);
        System.arraycopy(first, 0, file, channelsAt, first.length);
        System.arraycopy(second, 0, file, channelsAt + first.length, second.length);
        file[size - 1] = (byte) 0xff;
        return file;
    }

    /**
     * The effect is split across the high nibble of the first byte and the whole of the third, and the bit
     * that would make it rise is left clear.
     */
    private static void writeSample(byte[] file, int at) {
        file[at] = SAMPLE;
        file[at + 1] = (byte) ((EFFECT >> 8 & 0x0f) << 4 | LEVEL);
        file[at + 2] = (byte) (0x40 | NOISE);
        file[at + 3] = (byte) EFFECT;
        file[at + 1 + LINES * 3] = SAMPLE_LOOP;
        file[at + 2 + LINES * 3] = LINES;
    }

    private static void writePositions(byte[] file, int at) {
        file[at] = POSITIONS - 1;
        file[at + 1] = 1;
        file[at + 2] = 0;
        file[at + 3] = 1;
        file[at + 4] = TRANSPOSITION;
    }

    private static void writeOrnament(byte[] file, int at) {
        file[at] = ORNAMENT;
        file[at + 2] = ORNAMENT_STEP;
    }

    private static void writePattern(byte[] file, int at, int channelsAt, int firstLength, int secondLength) {
        file[at] = 1;
        putShort(file, at + 1, channelsAt);
        putShort(file, at + 3, channelsAt + firstLength);
        putShort(file, at + 5, channelsAt + firstLength + secondLength);
        file[at + PATTERN_BYTES] = (byte) 0xff;
    }

    /**
     * A sample, an ornament and a note, then a wait, then a rest.
     */
    private static byte[] firstChannel() {
        return new byte[]{(byte) (0x60 + SAMPLE), (byte) (0x70 + ORNAMENT), (byte) (0xa1 + WAIT), NOTE,
                (byte) 0x80, (byte) 0xff};
    }

    private static byte[] secondChannel() {
        return new byte[]{(byte) (0x80 + ENVELOPE), (byte) ENVELOPE_PERIOD, NOTE, (byte) 0xff};
    }

    private static void putShort(byte[] file, int at, int value) {
        file[at] = (byte) value;
        file[at + 1] = (byte) (value >> 8);
    }
}

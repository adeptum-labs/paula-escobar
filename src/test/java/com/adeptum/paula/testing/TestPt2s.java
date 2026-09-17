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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Builds a small Pro Tracker 2 module that reaches every command the reader has to decode: two patterns, one
 * sample of two lines, one ornament, a first channel that waits a line between its events, a second that
 * names an envelope and turns it off again, and a third that rests and waits out the pattern.
 */
public final class TestPt2s {

    public static final String TITLE = "PAULA PT2";
    public static final int TEMPO = 3;
    public static final int LOOP = 1;

    public static final int SAMPLE = 1;
    public static final int SAMPLE_LOOP = 1;
    public static final int NOISE = 5;
    public static final int LEVEL = 12;
    public static final int VIBRATO = 10;
    public static final int SECOND_LEVEL = 6;
    public static final int ORNAMENT = 1;
    public static final int ORNAMENT_STEP = 12;

    public static final int VOLUME = 11;
    public static final int NOTE = 24;
    public static final int LINE_TEMPO = 4;
    public static final int NOISE_ADD = 3;
    public static final int GLISS_STEP = -10;
    public static final int GLISS_TARGET = 26;
    public static final int ENVELOPE_TYPE = 14;
    public static final int ENVELOPE_TONE = 0x0123;
    public static final int SECOND_NOTE = 12;
    public static final int SECOND_PATTERN_NOTE = 30;
    public static final int FIRST_PATTERN_LINES = 6;
    public static final int SECOND_PATTERN_LINES = 5;

    private static final int HEADER = 131;
    private static final int SAMPLES_AT = 3;
    private static final int ORNAMENTS_AT = 67;
    private static final int PATTERNS_AT = 99;
    private static final int TITLE_AT = 101;
    private static final int PATTERN_BYTES = 6;

    private TestPt2s() {
    }

    public static byte[] pt2() {
        final byte[] positions = {0, 1, (byte) 0xff};
        final int patternsAt = HEADER + positions.length;
        final int sampleAt = patternsAt + 2 * PATTERN_BYTES;
        final byte[] sample = sample();
        final int ornamentAt = sampleAt + sample.length;
        final byte[] ornament = ornament();
        final byte[][] streams = {firstA(), firstB(), firstC(), {(byte) (0x80 + SECOND_PATTERN_NOTE), 0x00},
            {0x70}, {0x70}};

        final byte[] header = new byte[HEADER];
        header[0] = TEMPO;
        header[1] = (byte) positions.length;
        header[2] = LOOP;
        putShort(header, SAMPLES_AT + SAMPLE * 2, sampleAt);
        putShort(header, ORNAMENTS_AT + ORNAMENT * 2, ornamentAt);
        putShort(header, PATTERNS_AT, patternsAt);
        Arrays.fill(header, TITLE_AT, HEADER, (byte) ' ');
        final byte[] title = TITLE.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, header, TITLE_AT, title.length);

        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(header);
        file.writeBytes(positions);
        int at = ornamentAt + ornament.length;
        final byte[] table = new byte[2 * PATTERN_BYTES];
        for (int stream = 0; stream < streams.length; stream++) {
            putShort(table, stream * 2, at);
            at += streams[stream].length;
        }
        file.writeBytes(table);
        file.writeBytes(sample);
        file.writeBytes(ornament);
        for (final byte[] stream : streams) {
            file.writeBytes(stream);
        }
        return file.toByteArray();
    }

    /**
     * The first line sounds tone and noise and bends the tone up by the vibrato; the second masks both.
     */
    private static byte[] sample() {
        final int noiseAndPositiveVibrato = NOISE << 3 | 4;
        final int toneAndNoiseMasked = 3;
        return new byte[]{2, SAMPLE_LOOP,
            (byte) noiseAndPositiveVibrato, (byte) (LEVEL << 4), VIBRATO,
            (byte) toneAndNoiseMasked, (byte) (SECOND_LEVEL << 4), 0};
    }

    private static byte[] ornament() {
        return new byte[]{2, 0, 0, ORNAMENT_STEP};
    }

    /**
     * A sample, an ornament, a volume, a tempo and a wait of one line before the note; two lines on, a noise
     * addon and a glissando to a note; two lines on again, a rest, and then the end of the pattern.
     */
    private static byte[] firstA() {
        return new byte[]{
            (byte) (0xe0 + SAMPLE), (byte) (0x60 + ORNAMENT), (byte) (0x10 + VOLUME), 0x0f, LINE_TEMPO, 0x21,
            (byte) (0x80 + NOTE),
            0x01, NOISE_ADD, 0x0d, (byte) GLISS_STEP, 0, 0, (byte) (0x80 + GLISS_TARGET),
            (byte) 0xe0,
            0x00};
    }

    private static byte[] firstB() {
        return new byte[]{
            (byte) (0x70 + ENVELOPE_TYPE), (byte) ENVELOPE_TONE, (byte) (ENVELOPE_TONE >> 8), (byte) (0xe0 + SAMPLE),
            (byte) (0x80 + SECOND_NOTE),
            0x7f, 0x70, 0x70, 0x70, 0x70, 0x70};
    }

    private static byte[] firstC() {
        return new byte[]{0x25, (byte) 0xe0};
    }

    private static void putShort(byte[] into, int at, int value) {
        into[at] = (byte) value;
        into[at + 1] = (byte) (value >> 8);
    }
}

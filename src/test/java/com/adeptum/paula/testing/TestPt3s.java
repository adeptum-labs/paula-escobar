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
 * Builds a small Pro Tracker 3 module that still reaches every command the reader has to decode: two
 * patterns, one sample of two lines with every flag a line can carry, one ornament, and on the first channel
 * effects whose parameters follow the note in the reverse of the order they were named.
 */
public final class TestPt3s {

    public static final String TITLE = "PAULA PT3";
    public static final String AUTHOR = "ADEPTUM";
    public static final int VERSION = 5;
    public static final int TABLE = 2;
    public static final int TEMPO = 3;
    public static final int LOOP = 1;

    public static final int SAMPLE = 1;
    public static final int SAMPLE_LOOP = 1;
    public static final int LEVEL = 12;
    public static final int NOISE_OFFSET = -3;
    public static final int TONE_OFFSET = -300;
    public static final int SECOND_LEVEL = 5;
    public static final int ORNAMENT = 2;
    public static final int ORNAMENT_STEP = -12;
    public static final int ORNAMENT_LOOP = 1;

    public static final int VOLUME = 11;
    public static final int NOTE = 16;
    public static final int LINE_TEMPO = 4;
    public static final int ORNAMENT_OFFSET = 5;
    public static final int GLISS_PERIOD = 2;
    public static final int GLISS_STEP = -7;
    public static final int SLIDE_TARGET = 18;
    public static final int SLIDE_PERIOD = 1;
    public static final int SLIDE_STEP = 10;
    public static final int ENVELOPE_TYPE = 14;
    public static final int ENVELOPE_TONE = 0x0123;
    public static final int NOISE_BASE = 1;
    public static final int SECOND_NOTE = 8;
    public static final int FIRST_PATTERN_LINES = 5;

    private static final int HEADER = 201;
    private static final int SAMPLES_AT = 105;
    private static final int ORNAMENTS_AT = 169;
    private static final int PATTERN_BYTES = 6;

    private TestPt3s() {
    }

    public static byte[] pt3() {
        final byte[] header = header();
        final int positionsAt = HEADER;
        final int patternsAt = positionsAt + 3;
        final int sampleAt = patternsAt + 2 * PATTERN_BYTES;
        final byte[] sample = sample();
        final int ornamentAt = sampleAt + sample.length;
        final byte[] ornament = ornament();
        final int channelsAt = ornamentAt + ornament.length;
        final byte[][] streams = {firstA(), firstB(), firstC(), {0x50, 0x00}, {0x50, (byte) 0xd0}, {0x50, (byte) 0xd0}};

        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        putShort(header, 103, patternsAt);
        putShort(header, SAMPLES_AT + SAMPLE * 2, sampleAt);
        putShort(header, ORNAMENTS_AT, ornamentAt);
        file.writeBytes(header);
        file.writeBytes(new byte[]{0, 3, (byte) 0xff});
        int at = channelsAt;
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

    private static byte[] header() {
        final byte[] header = new byte[HEADER];
        Arrays.fill(header, 0, 98, (byte) ' ');
        text(header, 0, "ProTracker 3." + VERSION + " compilation of ");
        text(header, 30, TITLE);
        text(header, 62, " by ");
        text(header, 66, AUTHOR);
        header[99] = TABLE;
        header[100] = TEMPO;
        header[101] = 2;
        header[102] = LOOP;
        return header;
    }

    /**
     * The first line carries a rising volume slide, a noise offset of minus three kept for the next line, the
     * envelope masked, the noise masked, the tone offset kept, and a level of twelve; the second only a level.
     */
    private static byte[] sample() {
        final int slideUpEnvelopeMasked = 0x80 | 0x40 | (NOISE_OFFSET & 0x1f) << 1 | 1;
        final int noiseMaskedToneKept = 0x80 | 0x40 | LEVEL;
        return new byte[]{SAMPLE_LOOP, 2,
                (byte) slideUpEnvelopeMasked, (byte) noiseMaskedToneKept, (byte) TONE_OFFSET, (byte) (TONE_OFFSET >> 8),
                0, SECOND_LEVEL, 0, 0};
    }

    private static byte[] ornament() {
        return new byte[]{ORNAMENT_LOOP, 2, 0, ORNAMENT_STEP};
    }

    /**
     * A sample, an ornament, a volume and three effects before the note, their parameters after it in reverse;
     * then a wait and a rest, a glissando to a note, and the end of the pattern.
     */
    private static byte[] firstA() {
        return new byte[]{
                (byte) (0xd0 + SAMPLE), (byte) (0x40 + ORNAMENT), (byte) (0xc0 + VOLUME), 0x01, 0x04, 0x09,
                (byte) (0x50 + NOTE),
                LINE_TEMPO, ORNAMENT_OFFSET, GLISS_PERIOD, (byte) GLISS_STEP, (byte) (GLISS_STEP >> 8),
                (byte) 0xb1, 2, (byte) 0xc0,
                0x02, (byte) (0x50 + SLIDE_TARGET), SLIDE_PERIOD, 0, 0, SLIDE_STEP, 0,
                0x00};
    }

    private static byte[] firstB() {
        return new byte[]{(byte) 0xb1, 5,
                (byte) (0x10 + ENVELOPE_TYPE), (byte) (ENVELOPE_TONE >> 8), (byte) ENVELOPE_TONE, (byte) (SAMPLE * 2),
                (byte) (0x20 + NOISE_BASE), (byte) (0x50 + SECOND_NOTE), (byte) 0xd0};
    }

    private static byte[] firstC() {
        return new byte[]{(byte) 0xb1, 5, (byte) 0xb0, 0x55, (byte) 0xd0};
    }

    private static void text(byte[] into, int at, String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, into, at, bytes.length);
    }

    private static void putShort(byte[] into, int at, int value) {
        into[at] = (byte) value;
        into[at + 1] = (byte) (value >> 8);
    }
}

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
 * Builds a small Pro Sound Creator module: two samples, two ornaments and two patterns, laid out the way the
 * editor lays them out.
 */
public final class TestPscs {

    public static final String TITLE = "PAULA PSC";
    public static final String AUTHOR = "ADEPTUM";
    public static final String VERSION = "1.07";
    public static final String OLD_VERSION = "1.00";
    public static final int TEMPO = 3;
    public static final int LOOP = 1;

    public static final int SAMPLE = 1;
    public static final int TONE_STEP = 2;
    public static final int NOISE_ADDING = 3;
    public static final int LEVEL = 12;
    public static final int SECOND_LEVEL = 8;
    public static final int ORNAMENT = 1;
    public static final int ORNAMENT_STEP = 12;
    public static final int ORNAMENT_NOISE = 1;

    public static final int VOLUME = 11;
    public static final int NOTE = 24;
    public static final int LINE_TEMPO = 4;
    public static final int GLISS_STEP = 10;
    public static final int GLISS_NOTE = 26;
    public static final int ENVELOPE_TYPE = 14;
    public static final int ENVELOPE_TONE = 0x0123;
    public static final int NOISE_BASE = 2;
    public static final int SECOND_NOTE = 12;
    public static final int SECOND_PATTERN_NOTE = 30;
    public static final int VOLUME_SLIDE_PERIOD = 2;
    public static final int FIRST_PATTERN_LINES = 4;
    public static final int SECOND_PATTERN_LINES = 2;

    private static final int HEADER = 76;
    private static final int POSITIONS = 0x300;
    private static final int POSITION_BYTES = 8;

    private TestPscs() {
    }

    public static byte[] psc() {
        return psc(VERSION);
    }

    /**
     * Before version 1.03 the editor wrote where samples and ornaments lie as offsets from the start of the
     * file; from it on, from the start of their tables.
     */
    public static byte[] psc(String version) {
        final boolean old = version.compareTo("1.03") < 0;
        final byte[][] samples = {{0, 0, 0, 0, (byte) 0xd9, 0}, sample()};
        final byte[][] ornaments = {{(byte) 0xc0, 0}, ornament()};
        final int ornamentsTable = HEADER + 2 * samples.length;
        final int samplesStart = ornamentsTable + 2 * ornaments.length;
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final byte[] tables = new byte[samplesStart - HEADER];
        int at = samplesStart;
        for (int sample = 0; sample < samples.length; sample++) {
            putShort(tables, 2 * sample, at + 1 - (old ? 0 : HEADER));
            body.write(0);
            body.writeBytes(samples[sample]);
            at += 1 + samples[sample].length;
        }
        for (int ornament = 0; ornament < ornaments.length; ornament++) {
            putShort(tables, ornamentsTable - HEADER + 2 * ornament, at + 1 - (old ? 0 : ornamentsTable));
            body.write(0);
            body.writeBytes(ornaments[ornament]);
            at += 1 + ornaments[ornament].length;
        }
        final byte[][] streams = {firstA(), firstB(), {0x7c, (byte) 0xc3},
            {0x70, VOLUME_SLIDE_PERIOD, SECOND_PATTERN_NOTE, (byte) 0xc1}, {0x7c, SECOND_NOTE, (byte) 0xc1},
            {0x7d, 0x66, (byte) (0x80 + SAMPLE), SECOND_NOTE, (byte) 0xc1}};
        final int[] starts = new int[streams.length];
        for (int stream = 0; stream < streams.length; stream++) {
            starts[stream] = at;
            body.writeBytes(streams[stream]);
            at += streams[stream].length;
        }

        final byte[] positions = new byte[2 * POSITION_BYTES + 4];
        final int[] lengths = {FIRST_PATTERN_LINES, SECOND_PATTERN_LINES};
        for (int position = 0; position < lengths.length; position++) {
            positions[position * POSITION_BYTES] = (byte) position;
            positions[position * POSITION_BYTES + 1] = (byte) lengths[position];
            for (int channel = 0; channel < 3; channel++) {
                putShort(positions, position * POSITION_BYTES + 2 + 2 * channel, starts[position * 3 + channel]);
            }
        }
        positions[2 * POSITION_BYTES] = LOOP;
        positions[2 * POSITION_BYTES + 1] = (byte) 0xff;
        putShort(positions, 2 * POSITION_BYTES + 2, POSITIONS + POSITION_BYTES);

        final byte[] file = new byte[POSITIONS + positions.length];
        System.arraycopy(header(version, samplesStart, ornamentsTable), 0, file, 0, HEADER);
        System.arraycopy(tables, 0, file, HEADER, tables.length);
        final byte[] rest = body.toByteArray();
        System.arraycopy(rest, 0, file, samplesStart, rest.length);
        System.arraycopy(positions, 0, file, POSITIONS, positions.length);
        return file;
    }

    private static byte[] header(String version, int samplesStart, int ornamentsTable) {
        final byte[] header = new byte[HEADER];
        final String id = "PSC V" + version + " COMPILATION OF " + padded(TITLE) + " BY " + padded(AUTHOR);
        System.arraycopy(id.getBytes(StandardCharsets.US_ASCII), 0, header, 0, id.length());
        putShort(header, 69, samplesStart);
        putShort(header, 71, POSITIONS);
        header[73] = TEMPO;
        putShort(header, 74, ornamentsTable);
        return header;
    }

    private static String padded(String name) {
        final char[] spaces = new char[20 - name.length()];
        Arrays.fill(spaces, ' ');
        return name + new String(spaces);
    }

    /**
     * The first line adds to the tone and the noise, lets the envelope sound and begins the loop; the second
     * adds to the tone again, masks tone and noise, steps the volume down and ends the loop.
     */
    private static byte[] sample() {
        return new byte[]{
            TONE_STEP, 0, NOISE_ADDING, LEVEL, 0x60, 0,
            TONE_STEP, 0, 0, SECOND_LEVEL, (byte) 0x9d, 0};
    }

    /**
     * The first line begins the loop; the second moves the note up an octave, adds to the noise and ends it.
     */
    private static byte[] ornament() {
        return new byte[]{0x60, 0, (byte) (0x80 | ORNAMENT_NOISE), ORNAMENT_STEP};
    }

    /**
     * A sample, an ornament, a volume and a tempo with the note, waiting a line; then a glissando to a note, and
     * a rest.
     */
    private static byte[] firstA() {
        return new byte[]{
            (byte) (0x80 + SAMPLE), (byte) (0xa0 + ORNAMENT), (byte) (0x57 + VOLUME), 0x6e, LINE_TEMPO, NOTE,
            (byte) 0xc1,
            0x6d, GLISS_STEP, GLISS_NOTE, (byte) 0xc0,
            0x7c, (byte) 0xc0};
    }

    /**
     * An envelope and a noise base, which only the second channel may set, and a note sounding by the envelope.
     */
    private static byte[] firstB() {
        return new byte[]{
            0x7a, ENVELOPE_TYPE, (byte) ENVELOPE_TONE, (byte) (ENVELOPE_TONE >> 8), 0x7b, NOISE_BASE, 0x57,
            (byte) (0x80 + SAMPLE), SECOND_NOTE, (byte) 0xc3};
    }

    private static void putShort(byte[] into, int at, int value) {
        into[at] = (byte) value;
        into[at + 1] = (byte) (value >> 8);
    }
}

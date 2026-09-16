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

/**
 * Builds a small UltraTracker module: two channels, one pattern of sixty-four rows, one looping square-wave
 * sample and a message of one line. The first channel sounds a note with two effects and then repeats an
 * empty event to the end of the pattern; the second only ever repeats an empty one.
 */
public final class TestUlts {

    public static final String TITLE = "Paula ULT";
    public static final String MESSAGE = "made for the tests";
    public static final String SAMPLE_NAME = "square";
    public static final String SAMPLE_FILE = "SQUARE.WAV";
    public static final int CHANNELS = 2;
    public static final int NOTE = 49;
    public static final int INSTRUMENT = 1;
    public static final int FIRST_EFFECT = 0x0C;
    public static final int FIRST_PARAM = 0x80;
    public static final int SECOND_EFFECT = 0x0A;
    public static final int SECOND_PARAM = 0x40;
    public static final int SAMPLE_LENGTH = 32;
    public static final int LOOP_START = 0;
    public static final int LOOP_END = 32;
    public static final int VOLUME = 200;
    public static final int SPEED = 8363;
    public static final int FINETUNE = -1024;
    public static final int LOOP = 8;
    public static final int FIRST_CHANNEL_PANNING = 3;
    public static final int SECOND_CHANNEL_PANNING = 12;
    public static final int SQUARE_HIGH = 100;

    private static final String SIGNATURE = "MAS_UTrack_V00";
    private static final int NAME_LENGTH = 32;
    private static final int FILE_NAME_LENGTH = 12;
    private static final int ROWS = 64;
    private static final int REPEAT = 0xfc;
    private static final int ORDER_END = 0xff;

    private TestUlts() {
    }

    public static byte[] ult() {
        return ult('4');
    }

    /**
     * The same module as an older version would have written it: before version four a sample header has no
     * speed of its own and keeps its finetune where the newer ones keep the speed, and before version three
     * the channels carry no panning.
     */
    public static byte[] ult(char version) {
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(SIGNATURE.getBytes(StandardCharsets.US_ASCII));
        file.write(version);
        file.writeBytes(padded(TITLE, NAME_LENGTH));
        file.write(1);
        file.writeBytes(padded(MESSAGE, NAME_LENGTH));
        file.write(1);
        sample(file, version);
        final byte[] orders = new byte[256];
        orders[1] = (byte) ORDER_END;
        file.writeBytes(orders);
        file.write(CHANNELS - 1);
        file.write(0);
        if (version >= '3') {
            file.write(FIRST_CHANNEL_PANNING);
            file.write(SECOND_CHANNEL_PANNING);
        }
        file.writeBytes(new byte[]{(byte) NOTE, INSTRUMENT, (byte) (SECOND_EFFECT << 4 | FIRST_EFFECT),
                (byte) FIRST_PARAM, (byte) SECOND_PARAM});
        file.writeBytes(new byte[]{(byte) REPEAT, ROWS - 1, 0, 0, 0, 0, 0});
        file.writeBytes(new byte[]{(byte) REPEAT, ROWS, 0, 0, 0, 0, 0});
        for (int frame = 0; frame < SAMPLE_LENGTH; frame++) {
            file.write(frame < SAMPLE_LENGTH / 2 ? SQUARE_HIGH : -SQUARE_HIGH);
        }
        return file.toByteArray();
    }

    private static void sample(ByteArrayOutputStream file, char version) {
        file.writeBytes(padded(SAMPLE_NAME, NAME_LENGTH));
        file.writeBytes(padded(SAMPLE_FILE, FILE_NAME_LENGTH));
        writeInt(file, LOOP_START);
        writeInt(file, LOOP_END);
        writeInt(file, 0);
        writeInt(file, SAMPLE_LENGTH);
        file.write(VOLUME);
        file.write(LOOP);
        if (version >= '4') {
            writeShort(file, SPEED);
        }
        writeShort(file, FINETUNE);
    }

    private static byte[] padded(String text, int length) {
        final byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, (byte) ' ');
        final byte[] written = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(written, 0, bytes, 0, written.length);
        return bytes;
    }

    private static void writeInt(ByteArrayOutputStream file, int value) {
        writeShort(file, value);
        writeShort(file, value >> 16);
    }

    private static void writeShort(ByteArrayOutputStream file, int value) {
        file.write(value);
        file.write(value >> 8);
    }
}

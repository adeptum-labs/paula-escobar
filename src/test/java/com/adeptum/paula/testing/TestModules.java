/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Builds minimal but valid modules to play in tests: a four-channel ProTracker module and a two-track
 * DigiBooster Pro one, each with a single looping square-wave sample, one pattern and one note.
 */
public final class TestModules {

    public static final String TITLE = "Paula Test";
    public static final String SAMPLE_NAME = "square";

    public static final String SONG_NAME = "Paula Song";
    public static final int C3_FREQUENCY = 8363;
    public static final int DBM_TRACKS = 2;
    public static final int DBM_ROWS = 4;
    public static final int DBM_ORDERS = 2;
    public static final int ECHO_DELAY = 0x30;
    public static final int ECHO_FEEDBACK = 0x60;
    public static final int ECHO_MIX = 0x70;
    public static final int ECHO_CROSS = 0x80;

    private static final int HEADER_LENGTH = 1084;
    private static final int PATTERN_LENGTH = 64 * 4 * 4;
    private static final int SAMPLE_LENGTH = 64;
    private static final int PERIOD_C2 = 428;
    private static final int DBM_LENGTH = 1024;

    private TestModules() {
    }

    public static Path writeProTracker(Path directory) throws IOException {
        return Files.write(directory.resolve("test.mod"), proTracker());
    }

    public static byte[] proTracker() {
        final ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + PATTERN_LENGTH + SAMPLE_LENGTH);
        buffer.put(padded(TITLE, 20));
        buffer.put(padded(SAMPLE_NAME, 22))
                .putShort((short) (SAMPLE_LENGTH / 2))
                .put((byte) 0)
                .put((byte) 64)
                .putShort((short) 0)
                .putShort((short) (SAMPLE_LENGTH / 2));
        buffer.position(950);
        buffer.put((byte) 1).put((byte) 0);
        buffer.position(1080);
        buffer.put("M.K.".getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) (PERIOD_C2 >> 8)).put((byte) PERIOD_C2).put((byte) 0x10).put((byte) 0);
        buffer.position(HEADER_LENGTH + PATTERN_LENGTH);
        for (int i = 0; i < SAMPLE_LENGTH; i++) {
            buffer.put((byte) (i < SAMPLE_LENGTH / 2 ? 100 : -100));
        }
        return buffer.array();
    }

    public static Path writeDigiBooster(Path directory) throws IOException {
        return Files.write(directory.resolve("test.dbm"), digiBooster());
    }

    /**
     * Builds a minimal DigiBooster Pro module: two tracks, one looping eight-bit sample played by one
     * instrument with a volume envelope, one pattern of four rows and an echo on the first track.
     */
    public static byte[] digiBooster() {
        final ByteBuffer buffer = ByteBuffer.allocate(DBM_LENGTH);
        buffer.put("DBM0".getBytes(StandardCharsets.US_ASCII)).put((byte) 2).put((byte) 0x21).putShort((short) 0);
        chunk(buffer, "NAME", padded(TITLE, 44));
        chunk(buffer, "INFO", words(1, 1, 1, 1, DBM_TRACKS));
        chunk(buffer, "SONG", song());
        chunk(buffer, "INST", instrument());
        chunk(buffer, "PATT", pattern());
        chunk(buffer, "SMPL", sample());
        chunk(buffer, "VENV", envelope());
        chunk(buffer, "DSPE", echo());
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private static byte[] song() {
        return ByteBuffer.allocate(44 + 2 + 2 * DBM_ORDERS).put(padded(SONG_NAME, 44)).putShort((short) DBM_ORDERS)
                .putShort((short) 0).putShort((short) 0).array();
    }

    private static byte[] instrument() {
        return ByteBuffer.allocate(50).put(padded(SAMPLE_NAME, 30)).putShort((short) 1).putShort((short) 64)
                .putInt(C3_FREQUENCY).putInt(0).putInt(SAMPLE_LENGTH / 2).putShort((short) 0).putShort((short) 1).array();
    }

    /**
     * One entry on the second track of the first row: a C-4 played by the first instrument.
     */
    private static byte[] pattern() {
        final byte[] packed = {2, 0x03, 0x40, 1, 0, 0, 0, 0};
        return ByteBuffer.allocate(6 + packed.length).putShort((short) DBM_ROWS).putInt(packed.length).put(packed).array();
    }

    private static byte[] sample() {
        final ByteBuffer sample = ByteBuffer.allocate(8 + SAMPLE_LENGTH).putInt(1).putInt(SAMPLE_LENGTH);
        for (int i = 0; i < SAMPLE_LENGTH; i++) {
            sample.put((byte) (i < SAMPLE_LENGTH / 2 ? 100 : -100));
        }
        return sample.array();
    }

    private static byte[] envelope() {
        final ByteBuffer envelope = ByteBuffer.allocate(2 + 136).putShort((short) 1);
        envelope.putShort((short) 1).put((byte) 0x05).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 2).put((byte) 0);
        envelope.putShort((short) 0).putShort((short) 64).putShort((short) 10).putShort((short) 32).putShort((short) 20).putShort((short) 0);
        return envelope.array();
    }

    private static byte[] echo() {
        return ByteBuffer.allocate(2 + DBM_TRACKS + 8).putShort((short) DBM_TRACKS).put((byte) 0).put((byte) 1)
                .putShort((short) ECHO_DELAY).putShort((short) ECHO_FEEDBACK).putShort((short) ECHO_MIX).putShort((short) ECHO_CROSS).array();
    }

    private static byte[] words(int... values) {
        final ByteBuffer words = ByteBuffer.allocate(values.length * 2);
        for (final int value : values) {
            words.putShort((short) value);
        }
        return words.array();
    }

    private static void chunk(ByteBuffer buffer, String id, byte[] body) {
        buffer.put(id.getBytes(StandardCharsets.US_ASCII)).putInt(body.length).put(body);
    }

    private static byte[] padded(String text, int length) {
        final byte[] bytes = new byte[length];
        final byte[] source = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, bytes, 0, Math.min(source.length, length));
        return bytes;
    }
}

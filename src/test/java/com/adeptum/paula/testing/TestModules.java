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

/**
 * Builds a minimal but valid four-channel ProTracker module: one looping square-wave sample, one pattern, one note.
 */
public final class TestModules {

    public static final String TITLE = "Paula Test";
    public static final String SAMPLE_NAME = "square";

    private static final int HEADER_LENGTH = 1084;
    private static final int PATTERN_LENGTH = 64 * 4 * 4;
    private static final int SAMPLE_LENGTH = 64;
    private static final int PERIOD_C2 = 428;

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

    private static byte[] padded(String text, int length) {
        final byte[] bytes = new byte[length];
        final byte[] source = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, bytes, 0, Math.min(source.length, length));
        return bytes;
    }
}

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
 * Builds a minimal but valid PSID v2 file: the init routine turns the volume up and holds a pulse wave on voice
 * one, the play routine does nothing, so rendering yields a steady tone.
 */
public final class TestSids {

    public static final String NAME = "Paula Test Tune";
    public static final String AUTHOR = "Paula";
    public static final String RELEASED = "2026 Adeptum";
    public static final int SUBTUNES = 2;

    private static final int HEADER_LENGTH = 0x7C;
    private static final int VERSION = 2;
    private static final int LOAD_ADDRESS = 0x1000;
    private static final int START_SONG = 1;
    private static final int PAL_6581_FLAGS = 0x0014;
    private static final int STRING_LENGTH = 32;
    private static final byte[] INIT = {
        (byte) 0xA9, 0x0F, (byte) 0x8D, 0x18, (byte) 0xD4,
        (byte) 0xA9, 0x20, (byte) 0x8D, 0x01, (byte) 0xD4,
        (byte) 0xA9, 0x00, (byte) 0x8D, 0x05, (byte) 0xD4,
        (byte) 0xA9, (byte) 0xF0, (byte) 0x8D, 0x06, (byte) 0xD4,
        (byte) 0xA9, 0x08, (byte) 0x8D, 0x03, (byte) 0xD4,
        (byte) 0xA9, 0x41, (byte) 0x8D, 0x04, (byte) 0xD4,
        0x60};
    private static final byte[] PLAY = {0x60};
    private static final int BASIC_START = 0x0801;
    private static final int PROGRAM_LENGTH = 512;

    /**
     * 10 SYS 2062, the line that starts the machine code following it.
     */
    private static final byte[] BASIC_LINE = {
        0x0B, 0x08, 0x0A, 0x00, (byte) 0x9E, 0x32, 0x30, 0x36, 0x32, 0x00, 0x00, 0x00};

    private TestSids() {
    }

    /**
     * A C64 program as the disks of the day hold them: a load address and a BASIC line that starts the machine
     * code after it, which here only turns the volume up and returns. It is padded to the length the engine
     * needs before it will look at a program at all.
     */
    public static byte[] program() {
        final ByteBuffer buffer = ByteBuffer.allocate(PROGRAM_LENGTH);
        return buffer.putShort(Short.reverseBytes((short) BASIC_START)).put(BASIC_LINE).put(INIT).array();
    }

    public static Path writeProgram(Path directory) throws IOException {
        return Files.write(directory.resolve("tune.prg"), program());
    }

    public static Path writePsid(Path directory) throws IOException {
        return Files.write(directory.resolve("test.sid"), psid());
    }

    public static byte[] psid() {
        final ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + 2 + INIT.length + PLAY.length);
        buffer.put("PSID".getBytes(StandardCharsets.US_ASCII))
                .putShort((short) VERSION)
                .putShort((short) HEADER_LENGTH)
                .putShort((short) 0)
                .putShort((short) LOAD_ADDRESS)
                .putShort((short) (LOAD_ADDRESS + INIT.length))
                .putShort((short) SUBTUNES)
                .putShort((short) START_SONG)
                .putInt(0)
                .put(padded(NAME))
                .put(padded(AUTHOR))
                .put(padded(RELEASED))
                .putShort((short) PAL_6581_FLAGS)
                .put((byte) 0)
                .put((byte) 0)
                .putShort((short) 0);
        buffer.put((byte) (LOAD_ADDRESS & 0xFF)).put((byte) (LOAD_ADDRESS >> 8));
        buffer.put(INIT).put(PLAY);
        return buffer.array();
    }

    private static byte[] padded(String text) {
        final byte[] bytes = new byte[STRING_LENGTH];
        final byte[] source = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, bytes, 0, Math.min(source.length, STRING_LENGTH));
        return bytes;
    }
}

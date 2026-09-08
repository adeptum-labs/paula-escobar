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

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Reads an MO3 file the way the PC wrote it, smallest byte first. Reading past the end is an error rather than
 * a zero, so a truncated file is refused instead of played as silence.
 */
final class Mo3Bytes {

    private final byte[] file;
    private int at;

    Mo3Bytes(byte[] file) {
        this.file = file;
    }

    int position() {
        return at;
    }

    void seek(int position) throws IOException {
        if (position < 0 || position > file.length) {
            throw new EOFException("Offset " + position + " lies outside the module");
        }
        at = position;
    }

    void skip(int count) throws IOException {
        seek(at + count);
    }

    int u8() throws IOException {
        require(1);
        return file[at++] & 0xFF;
    }

    int s8() throws IOException {
        return (byte) u8();
    }

    int u16() throws IOException {
        return u8() | u8() << Byte.SIZE;
    }

    int s16() throws IOException {
        return (short) u16();
    }

    int s32() throws IOException {
        return u16() | u16() << Short.SIZE;
    }

    /**
     * A count or a length, which the format writes as a word it never means to be negative.
     */
    int u32() throws IOException {
        final int value = s32();
        if (value < 0) {
            throw new EOFException("Length or count larger than the module can hold");
        }
        return value;
    }

    byte[] bytes(int count) throws IOException {
        require(count);
        final byte[] read = new byte[count];
        System.arraycopy(file, at, read, 0, count);
        at += count;
        return read;
    }

    /**
     * Text out of a fixed field, ending at the first zero and stripped of whatever pads it out.
     */
    String text(int length) throws IOException {
        return terminated(bytes(length));
    }

    /**
     * Text of no stated length, ending at the first zero, which is how the song name and message are stored.
     */
    String textZ() throws IOException {
        final int start = at;
        while (at < file.length && file[at] != 0) {
            at++;
        }
        require(1);
        final String read = terminated(Arrays.copyOfRange(file, start, at));
        at++;
        return read;
    }

    boolean has(int count) {
        return count >= 0 && at + count <= file.length;
    }

    int length() {
        return file.length;
    }

    int remaining() {
        return file.length - at;
    }

    private static String terminated(byte[] read) {
        int end = 0;
        while (end < read.length && read[end] != 0) {
            end++;
        }
        return new String(read, 0, end, StandardCharsets.ISO_8859_1).strip();
    }

    private void require(int count) throws IOException {
        if (!has(count)) {
            throw new EOFException("The module ends in the middle of what it says it holds");
        }
    }
}

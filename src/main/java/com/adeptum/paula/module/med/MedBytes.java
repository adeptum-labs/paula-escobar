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

package com.adeptum.paula.module.med;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads an OctaMED file the way the Amiga wrote it, biggest byte first, with a position that can be moved to
 * wherever the offset in a header points. Reading past the end is an error rather than a zero, so a truncated
 * file is refused instead of played as silence.
 */
final class MedBytes {

    private final byte[] file;
    private int at;

    MedBytes(byte[] file) {
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
        return u8() << Byte.SIZE | u8();
    }

    int s16() throws IOException {
        return (short) u16();
    }

    /**
     * An offset or a length, which the format writes as a signed word it never means to be negative.
     */
    int u32() throws IOException {
        final int value = u16() << Short.SIZE | u16();
        if (value < 0) {
            throw new EOFException("Length or offset larger than the module can hold");
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
     * Text as the Amiga stored it, ending at the first zero and stripped of the spaces a fixed field is padded
     * out with.
     */
    String text(int length) throws IOException {
        final byte[] read = bytes(length);
        int end = 0;
        while (end < read.length && read[end] != 0) {
            end++;
        }
        return new String(read, 0, end, StandardCharsets.ISO_8859_1).strip();
    }

    boolean has(int count) {
        return count >= 0 && at + count <= file.length;
    }

    int length() {
        return file.length;
    }

    private void require(int count) throws IOException {
        if (!has(count)) {
            throw new EOFException("The module ends in the middle of what it says it holds");
        }
    }
}

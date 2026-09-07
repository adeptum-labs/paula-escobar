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

package com.adeptum.paula.module.ape;

import java.io.EOFException;
import java.io.IOException;

/**
 * A Monkey's Audio file held in memory for the decoder to read. The decoder seeks about a file freely, which
 * the stream the library offers refuses to do, and the one that does keeps a handle open for as long as it
 * lives; a renderer is never closed, so the bytes are kept instead, as the other sampled formats keep theirs.
 */
final class ApeBytes extends de.quippy.jmac.tools.File {

    private final byte[] file;
    private final String name;
    private int at;
    private int marked;

    ApeBytes(byte[] file, String name) {
        this.file = file;
        this.name = name;
    }

    @Override
    public int read() {
        return at < file.length ? file[at++] & 0xFF : -1;
    }

    @Override
    public int read(byte[] into) {
        return read(into, 0, into.length);
    }

    @Override
    public int read(byte[] into, int from, int wanted) {
        final int read = Math.min(wanted, file.length - at);
        if (read <= 0) {
            return -1;
        }
        System.arraycopy(file, at, into, from, read);
        at += read;
        return read;
    }

    @Override
    public void readFully(byte[] into) {
        readFully(into, 0, into.length);
    }

    /**
     * Filling short is not an error here. The decoder reads a whole frame's worth of bytes past the end of the
     * last frame and expects to be handed however much there was, which is what the library's own readers do;
     * refusing, as the contract this inherits would have it, loses the last frame of every file.
     */
    @Override
    public void readFully(byte[] into, int from, int wanted) {
        read(into, from, wanted);
    }

    @Override
    public boolean readBoolean() throws IOException {
        return readByte() != 0;
    }

    @Override
    public byte readByte() throws IOException {
        return (byte) readUnsignedByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        final int value = read();
        if (value < 0) {
            throw new EOFException(name);
        }
        return value;
    }

    @Override
    public short readShort() throws IOException {
        return (short) readUnsignedShort();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return readUnsignedByte() << Byte.SIZE | readUnsignedByte();
    }

    @Override
    public char readChar() throws IOException {
        return (char) readUnsignedShort();
    }

    @Override
    public int readInt() throws IOException {
        return readUnsignedShort() << Short.SIZE | readUnsignedShort();
    }

    @Override
    public long readLong() throws IOException {
        return (long) readInt() << Integer.SIZE | readInt() & 0xFFFFFFFFL;
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Text lines are no part of an audio file; the decoder never asks for one.
     */
    @Override
    public String readLine() {
        throw new UnsupportedOperationException(name);
    }

    @Override
    public String readUTF() {
        throw new UnsupportedOperationException(name);
    }

    @Override
    public int skipBytes(int count) {
        final int skipped = Math.max(0, Math.min(count, file.length - at));
        at += skipped;
        return skipped;
    }

    @Override
    public void mark(int limit) {
        marked = at;
    }

    @Override
    public void reset() {
        at = marked;
    }

    @Override
    public void seek(long position) {
        at = (int) Math.max(0, Math.min(file.length, position));
    }

    @Override
    public long getFilePointer() {
        return at;
    }

    @Override
    public long length() {
        return file.length;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public String getFilename() {
        return name;
    }

    @Override
    public void close() {
    }
}

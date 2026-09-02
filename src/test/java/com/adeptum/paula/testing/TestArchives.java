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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jp.gr.java_conf.dangan.util.lha.LhaHeader;
import jp.gr.java_conf.dangan.util.lha.LhaOutputStream;

/**
 * Builds small archives in memory; entry order follows the map's iteration order.
 */
public final class TestArchives {

    private TestArchives() {
    }

    public static byte[] zip(Map<String, byte[]> entries) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /**
     * Wraps content in an XPK container using stored chunks, which every packer id accepts, so the container
     * logic can be tested without a compressor.
     */
    public static byte[] xpk(byte[] content, String packer, int chunkSize) {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int offset = 0; offset < content.length; offset += chunkSize) {
            final byte[] chunk = java.util.Arrays.copyOfRange(content, offset, Math.min(content.length, offset + chunkSize));
            body.writeBytes(xpkChunkHeader(0, chunk));
            body.writeBytes(chunk);
            body.writeBytes(new byte[(4 - chunk.length % 4) % 4]);
        }
        body.writeBytes(xpkChunkHeader(15, new byte[0]));
        final ByteBuffer header = ByteBuffer.allocate(36);
        header.put("XPKF".getBytes(StandardCharsets.US_ASCII))
                .putInt(36 + body.size() - 8)
                .put(packer.getBytes(StandardCharsets.US_ASCII))
                .putInt(content.length)
                .put(java.util.Arrays.copyOf(content, 16))
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0);
        final byte[] bytes = header.array();
        bytes[33] = xor(bytes, 0, bytes.length);
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(bytes);
        file.writeBytes(body.toByteArray());
        return file.toByteArray();
    }

    private static byte[] xpkChunkHeader(int type, byte[] chunk) {
        final byte[] header = ByteBuffer.allocate(8).put((byte) type).put((byte) 0)
                .put(xorOfEveryOther(chunk, 0)).put(xorOfEveryOther(chunk, 1))
                .putShort((short) chunk.length).putShort((short) chunk.length).array();
        header[1] = xor(header, 0, header.length);
        return header;
    }

    private static byte xor(byte[] bytes, int from, int to) {
        byte result = 0;
        for (int i = from; i < to; i++) {
            result ^= bytes[i];
        }
        return result;
    }

    private static byte xorOfEveryOther(byte[] bytes, int from) {
        byte result = 0;
        for (int i = from; i < bytes.length; i += 2) {
            result ^= bytes[i];
        }
        return result;
    }

    public static byte[] lha(Map<String, byte[]> entries, String method) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (LhaOutputStream lha = new LhaOutputStream(bytes)) {
            for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
                final LhaHeader header = new LhaHeader(entry.getKey());
                header.setCompressMethod(method);
                lha.putNextEntry(header);
                lha.write(entry.getValue());
                lha.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}

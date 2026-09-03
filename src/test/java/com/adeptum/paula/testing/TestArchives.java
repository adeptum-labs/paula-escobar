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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import jp.gr.java_conf.dangan.util.lha.LhaHeader;
import jp.gr.java_conf.dangan.util.lha.LhaOutputStream;

/**
 * Builds small archives in memory; entry order follows the map's iteration order.
 */
public final class TestArchives {

    private static final int D64_LENGTH = 174848;
    private static final int D64_DIRECTORY_TRACK = 18;
    private static final int D64_BLOCK_DATA = 254;

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
     * Packs entries the way 7-Zip does by default, which is LZMA2 over the lot of them at once.
     */
    public static byte[] sevenZip(Map<String, byte[]> entries) throws IOException {
        final SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel();
        try (SevenZOutputFile sevenZip = new SevenZOutputFile(channel)) {
            for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
                final SevenZArchiveEntry header = new SevenZArchiveEntry();
                header.setName(entry.getKey());
                header.setSize(entry.getValue().length);
                sevenZip.putArchiveEntry(header);
                sevenZip.write(entry.getValue());
                sevenZip.closeArchiveEntry();
            }
            sevenZip.finish();
        }
        return Arrays.copyOf(channel.array(), (int) channel.size());
    }

    /**
     * Lays out a 35 track 1541 disk image: the programs go on track 17 as block chains and the directory on
     * track 18 names them, the way a real disk holds them.
     */
    public static byte[] d64(Map<String, byte[]> programs) {
        final byte[] image = new byte[D64_LENGTH];
        final int directory = blockOffset(D64_DIRECTORY_TRACK, 1);
        image[directory + 1] = (byte) 0xFF;
        int track = 17;
        int sector = 0;
        int entry = 0;
        for (final Map.Entry<String, byte[]> program : programs.entrySet()) {
            final int at = directory + 2 + entry++ * 32;
            image[at] = (byte) 0x82;
            image[at + 1] = (byte) track;
            image[at + 2] = (byte) sector;
            final byte[] name = program.getKey().getBytes(StandardCharsets.US_ASCII);
            for (int character = 0; character < 16; character++) {
                image[at + 3 + character] = character < name.length ? name[character] : (byte) 0xA0;
            }
            sector = writeChain(image, track, sector, program.getValue());
        }
        return image;
    }

    private static int writeChain(byte[] image, int track, int firstSector, byte[] data) {
        int sector = firstSector;
        for (int written = 0; written < Math.max(1, data.length); sector++) {
            final int at = blockOffset(track, sector);
            final int chunk = Math.min(D64_BLOCK_DATA, data.length - written);
            final boolean last = written + chunk >= data.length;
            image[at] = (byte) (last ? 0 : track);
            image[at + 1] = (byte) (last ? chunk + 1 : sector + 1);
            System.arraycopy(data, written, image, at + 2, chunk);
            written += chunk;
        }
        return sector;
    }

    private static int blockOffset(int track, int sector) {
        int offset = 0;
        for (int before = 1; before < track; before++) {
            offset += sectorsOn(before) * 256;
        }
        return offset + sector * 256;
    }

    private static int sectorsOn(int track) {
        if (track <= 17) {
            return 21;
        }
        if (track <= 24) {
            return 19;
        }
        return track <= 30 ? 18 : 17;
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

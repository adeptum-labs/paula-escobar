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
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
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

    private static final int RAR_MAIN_HEAD = 0x73;
    private static final int RAR_FILE_HEAD = 0x74;
    private static final int RAR_LONG_BLOCK = 0x8000;
    private static final int RAR_STORED = 0x30;
    private static final int RAR_VERSION = 20;
    private static final int RAR_DOS_TIME = 0x2D3A7BB0;

    private static final byte[] PP_MAGIC = {'P', 'P', '2', '0'};
    private static final int PP_EFFICIENCY = 0x09;
    private static final int PP_HEADER_LENGTH = 8;
    private static final int PP_INDEX_BITS = 2;
    private static final int PP_RUN_CONTINUES = 3;
    private static final int PP_SHORTEST_OFFSET_WIDTH = 9;
    private static final int PP_MATCHED_TAIL = 2;
    private static final int PP_MATCH_LENGTH = 2;

    private static final byte[] UMX_MAGIC = {(byte) 0xC1, (byte) 0x83, 0x2A, (byte) 0x9E};
    private static final int UMX_VERSION_AT = 4;
    private static final int UMX_VERSION = 68;
    private static final int UMX_HEADER_LENGTH = 0x40;

    private static final String T64_DESCRIPTOR = "C64S tape image file";
    private static final int T64_MAX_ENTRIES_AT = 0x22;
    private static final int T64_DIRECTORY_AT = 0x40;
    private static final int T64_ENTRY_LENGTH = 32;

    private static final int D64_LENGTH = 174848;
    private static final int D64_DIRECTORY_TRACK = 18;
    private static final int D64_BLOCK_DATA = 254;

    private TestArchives() {
    }

    public static byte[] zip(Map<String, byte[]> entries) throws IOException {
        return zip(entries, StandardCharsets.UTF_8);
    }

    /**
     * Names in any charset but UTF-8 are written the way the zippers of old wrote them: without the flag that
     * says what they are.
     */
    public static byte[] zip(Map<String, byte[]> entries, Charset names) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, names)) {
            for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }


    public static byte[] gzip(byte[] content) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(content);
        }
        return bytes.toByteArray();
    }

    /**
     * A RAR 4 archive whose entries are stored rather than packed, there being no RAR compressor to hand and
     * the format allowing a file to be kept as it is.
     */
    public static byte[] rar(Map<String, byte[]> entries) {
        final ByteArrayOutputStream archive = new ByteArrayOutputStream();
        archive.writeBytes(new byte[] {'R', 'a', 'r', '!', 0x1A, 0x07, 0x00});
        archive.writeBytes(block(RAR_MAIN_HEAD, 0, ByteBuffer.allocate(6).array()));
        for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
            archive.writeBytes(rarFileHeader(entry.getKey(), entry.getValue()));
            archive.writeBytes(entry.getValue());
        }
        return archive.toByteArray();
    }

    private static byte[] rarFileHeader(String name, byte[] content) {
        final byte[] named = name.getBytes(StandardCharsets.ISO_8859_1);
        final CRC32 crc = new CRC32();
        crc.update(content);
        final ByteBuffer body = ByteBuffer.allocate(25 + named.length).order(ByteOrder.LITTLE_ENDIAN);
        body.putInt(content.length).putInt(content.length).put((byte) 0).putInt((int) crc.getValue());
        body.putInt(RAR_DOS_TIME).put((byte) RAR_VERSION).put((byte) RAR_STORED);
        body.putShort((short) named.length).putInt(0).put(named);
        return block(RAR_FILE_HEAD, RAR_LONG_BLOCK, body.array());
    }

    /**
     * A block is its own checksum, type, flags and size, then whatever the type carries; the checksum covers
     * everything after itself.
     */
    private static byte[] block(int type, int flags, byte[] body) {
        final ByteBuffer block = ByteBuffer.allocate(7 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        block.putShort((short) 0).put((byte) type).putShort((short) flags);
        block.putShort((short) (7 + body.length)).put(body);
        final CRC32 crc = new CRC32();
        crc.update(block.array(), 2, block.capacity() - 2);
        return block.putShort(0, (short) crc.getValue()).array();
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

    /**
     * A tape image of the given programs, each of which arrives with its load address at the head and is
     * stored without it, as a tape does.
     */
    public static byte[] t64(Map<String, byte[]> programs) {
        final int directory = T64_DIRECTORY_AT + programs.size() * T64_ENTRY_LENGTH;
        final byte[] image = new byte[directory + programs.values().stream().mapToInt(p -> p.length - 2).sum()];
        System.arraycopy(T64_DESCRIPTOR.getBytes(StandardCharsets.US_ASCII), 0, image, 0, T64_DESCRIPTOR.length());
        image[T64_MAX_ENTRIES_AT] = (byte) programs.size();
        int at = T64_DIRECTORY_AT;
        int offset = directory;
        for (final Map.Entry<String, byte[]> program : programs.entrySet()) {
            final byte[] bytes = program.getValue();
            final int start = (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8;
            image[at] = 1;
            image[at + 1] = (byte) 0x82;
            writeWord(image, at + 2, start);
            writeWord(image, at + 4, start + bytes.length - 2);
            writeWord(image, at + 8, offset);
            final byte[] name = program.getKey().getBytes(StandardCharsets.US_ASCII);
            for (int character = 0; character < 16; character++) {
                image[at + 16 + character] = character < name.length ? name[character] : (byte) ' ';
            }
            System.arraycopy(bytes, 2, image, offset, bytes.length - 2);
            offset += bytes.length - 2;
            at += T64_ENTRY_LENGTH;
        }
        return image;
    }

    /**
     * The given bytes crunched as one long run of literals, which is all a cruncher has to emit when nothing
     * in the file repeats.
     */
    public static byte[] powerPacker(byte[] content) {
        final Bits bits = new Bits();
        bits.add(0, 1);
        literals(bits, content, 0, content.length);
        return crunched(bits, content.length);
    }

    /**
     * The same, with a match in the middle: two bytes copied from the two written just before them, which is
     * the shortest back-reference the format has.
     */
    public static byte[] powerPackerWithMatch(byte[] content) {
        final Bits bits = new Bits();
        bits.add(0, 1);
        literals(bits, content, content.length - PP_MATCHED_TAIL, content.length);
        bits.add(0, PP_INDEX_BITS);
        bits.add(0, PP_SHORTEST_OFFSET_WIDTH);
        bits.add(0, 1);
        literals(bits, content, 0, content.length - PP_MATCHED_TAIL - PP_MATCH_LENGTH);
        return crunched(bits, content.length);
    }

    /**
     * A run is written as its length less one, in twos, every one of them the largest a pair of bits holds
     * until the last; the bytes themselves follow in the order the output is filled, which is backwards.
     */
    private static void literals(Bits bits, byte[] content, int from, int to) {
        int count = to - from - 1;
        while (count >= PP_RUN_CONTINUES) {
            bits.add(PP_RUN_CONTINUES, PP_INDEX_BITS);
            count -= PP_RUN_CONTINUES;
        }
        bits.add(count, PP_INDEX_BITS);
        for (int at = to - 1; at >= from; at--) {
            bits.add(content[at] & 0xFF, Byte.SIZE);
        }
    }

    /**
     * The stream is laid down backwards a longword at a time, so the first bits read sit in the last longword
     * of the file, above however many bits of padding the trailer counts off.
     */
    private static byte[] crunched(Bits bits, int length) {
        final int skip = (Integer.SIZE - bits.count % Integer.SIZE) % Integer.SIZE;
        final int words = (bits.count + skip) / Integer.SIZE;
        final byte[] file = new byte[PP_HEADER_LENGTH + words * Integer.BYTES + Integer.BYTES];
        System.arraycopy(PP_MAGIC, 0, file, 0, PP_MAGIC.length);
        Arrays.fill(file, PP_MAGIC.length, PP_HEADER_LENGTH, (byte) PP_EFFICIENCY);
        for (int bit = 0; bit < bits.count; bit++) {
            if (bits.set.get(bit)) {
                final int placed = bit + skip;
                final int at = file.length - Integer.BYTES - (placed / Integer.SIZE + 1) * Integer.BYTES;
                file[at + Integer.BYTES - 1 - placed % Integer.SIZE / Byte.SIZE] |=
                        (byte) (1 << placed % Byte.SIZE);
            }
        }
        writeInt(file, file.length - Integer.BYTES, length << Byte.SIZE | skip);
        return file;
    }

    private static void writeInt(byte[] file, int at, int value) {
        for (int byteIndex = 0; byteIndex < Integer.BYTES; byteIndex++) {
            file[at + byteIndex] = (byte) (value >>> (Integer.BYTES - 1 - byteIndex) * Byte.SIZE);
        }
    }

    /**
     * The bits a crunched file is read in, kept in the order they are read rather than the order they lie in.
     */
    private static final class Bits {

        private final BitSet set = new BitSet();
        private int count;

        private void add(int value, int width) {
            for (int bit = width - 1; bit >= 0; bit--) {
                set.set(count++, (value >> bit & 1) != 0);
            }
        }
    }

    /**
     * An Unreal package around one module: the tag the engine knows the file by, a header of the size the
     * versions that shipped music wrote, and then the tune whole.
     */
    public static byte[] umx(byte[] module) {
        final byte[] file = new byte[UMX_HEADER_LENGTH + module.length];
        System.arraycopy(UMX_MAGIC, 0, file, 0, UMX_MAGIC.length);
        writeWord(file, UMX_VERSION_AT, UMX_VERSION);
        System.arraycopy(module, 0, file, UMX_HEADER_LENGTH, module.length);
        return file;
    }

    private static void writeWord(byte[] image, int at, int value) {
        image[at] = (byte) value;
        image[at + 1] = (byte) (value >> 8);
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

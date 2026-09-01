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
 *
 * The archive layout follows XADLZXParser.m from XADMaster,
 * Copyright © 2017-present MacPaw Inc., licensed under the GNU Lesser
 * General Public License version 2.1 or later and used here under the
 * GNU General Public License as section 3 of the LGPL permits.
 */

package com.adeptum.paula.archive.lzx;

import com.adeptum.paula.archive.ArchiveExtractor;
import com.adeptum.paula.archive.Archives;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.CRC32;

/**
 * Reads Amiga LZX archives. Small files are merged: entries with a packed size of zero share the compressed
 * stream of the next entry that has one, and the stream unpacks to their contents back to back.
 */
public final class LzxExtractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {'L', 'Z', 'X'};
    private static final int INFO_HEADER_LENGTH = 10;
    private static final int ENTRY_HEADER_LENGTH = 31;
    private static final int METHOD_STORED = 0;
    private static final int METHOD_LZX = 2;
    private static final int BYTE_MASK = 0xFF;
    private static final Charset NAMES = StandardCharsets.ISO_8859_1;

    private record Entry(String name, int size, int packedSize, int method, int crc) {
    }

    @Override
    public boolean matches(byte[] head) {
        return Archives.startsWith(head, MAGIC);
    }

    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        try {
            extractGroups(Files.readAllBytes(archive), into, wanted);
        } catch (RuntimeException e) {
            throw new IOException("Corrupt LZX archive " + archive.getFileName() + ": " + e, e);
        }
    }

    private static void extractGroups(byte[] data, Path into, Predicate<String> wanted) throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).position(INFO_HEADER_LENGTH);
        final List<Entry> merged = new ArrayList<>();
        while (buffer.remaining() >= ENTRY_HEADER_LENGTH) {
            final Entry entry = readEntry(buffer);
            merged.add(entry);
            if (entry.packedSize() > 0) {
                final int dataOffset = buffer.position();
                if (dataOffset + entry.packedSize() > data.length) {
                    throw new IOException("Truncated LZX entry " + entry.name());
                }
                writeGroup(data, dataOffset, merged, into, wanted);
                buffer.position(dataOffset + entry.packedSize());
                merged.clear();
            }
        }
        if (!merged.isEmpty()) {
            throw new IOException("Truncated LZX archive, no data for " + merged.get(merged.size() - 1).name());
        }
    }

    private static Entry readEntry(ByteBuffer buffer) throws IOException {
        buffer.getShort();
        final int size = buffer.getInt();
        final int packedSize = buffer.getInt();
        buffer.get();
        final int method = buffer.get();
        buffer.getShort();
        final int commentLength = buffer.get() & BYTE_MASK;
        buffer.get();
        buffer.getShort();
        buffer.getInt();
        final int crc = buffer.getInt();
        buffer.getInt();
        final int nameLength = buffer.get() & BYTE_MASK;
        if (buffer.remaining() < nameLength + commentLength) {
            throw new IOException("Truncated LZX header");
        }
        final byte[] name = new byte[nameLength];
        buffer.get(name);
        buffer.position(buffer.position() + commentLength);
        final Entry entry = new Entry(new String(name, NAMES), size, packedSize, method, crc);
        if (size < 0 || packedSize < 0) {
            throw new IOException("Corrupt size in LZX entry " + entry.name());
        }
        return entry;
    }

    private static void writeGroup(byte[] data, int offset, List<Entry> group, Path into, Predicate<String> wanted) throws IOException {
        if (group.stream().noneMatch(entry -> wanted.test(entry.name()))) {
            return;
        }
        final Entry carrier = group.get(group.size() - 1);
        final int total = group.stream().mapToInt(Entry::size).sum();
        final byte[] unpacked = switch (carrier.method()) {
            case METHOD_STORED -> Arrays.copyOfRange(data, offset, offset + total);
            case METHOD_LZX -> LzxDecoder.decode(data, offset, carrier.packedSize(), total);
            default -> throw new IOException("Unsupported LZX method " + carrier.method() + " for " + carrier.name());
        };
        int position = 0;
        for (final Entry entry : group) {
            final byte[] content = Arrays.copyOfRange(unpacked, position, position + entry.size());
            position += entry.size();
            if (wanted.test(entry.name())) {
                verifyCrc(entry, content);
                Files.write(Archives.target(into, entry.name()), content);
            }
        }
    }

    private static void verifyCrc(Entry entry, byte[] content) throws IOException {
        final CRC32 crc = new CRC32();
        crc.update(content);
        if ((int) crc.getValue() != entry.crc()) {
            throw new IOException("CRC mismatch in LZX entry " + entry.name());
        }
    }
}

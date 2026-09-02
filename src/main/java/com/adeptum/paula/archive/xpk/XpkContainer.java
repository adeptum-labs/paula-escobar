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
 * The container layout follows XPKMain.cpp from Teemu Suutari's ancient,
 * Copyright © Teemu Suutari, BSD 2-Clause License.
 */

package com.adeptum.paula.archive.xpk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * The XPK container: a 36-byte header naming the packer, then chunks that are stored, packed or the end marker,
 * each with a header and XOR checksums.
 */
final class XpkContainer {

    private static final int HEADER_LENGTH = 36;
    private static final int MIN_LENGTH = 44;
    private static final int PACKED_SIZE = 4;
    private static final int PACKER = 8;
    private static final int RAW_SIZE = 12;
    private static final int FIRST_BYTES = 16;
    private static final int FIRST_BYTES_LENGTH = 16;
    private static final int FLAGS = 32;
    private static final int EXTRA_HEADER_LENGTH = 36;
    private static final int FLAG_LONG_HEADERS = 1;
    private static final int FLAG_PASSWORD = 2;
    private static final int FLAG_EXTRA_HEADER = 4;
    private static final int SHORT_CHUNK_HEADER = 8;
    private static final int LONG_CHUNK_HEADER = 12;
    private static final int CHUNK_STORED = 0;
    private static final int CHUNK_PACKED = 1;
    private static final int CHUNK_END = 15;
    private static final int ALIGNMENT = 4;
    private static final Map<String, ChunkUnpacker> UNPACKERS = Map.of(
            "NUKE", new NukeUnpacker(false),
            "DUKE", new NukeUnpacker(true),
            "SQSH", new SqshUnpacker());

    private XpkContainer() {
    }

    static byte[] unpack(byte[] data) throws IOException {
        if (data.length < MIN_LENGTH || !"XPKF".equals(ascii(data, 0))) {
            throw new IOException("Not an XPK file");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        final int packedSize = buffer.getInt(PACKED_SIZE);
        final String packer = ascii(data, PACKER);
        final int rawSize = buffer.getInt(RAW_SIZE);
        final int flags = data[FLAGS] & 0xFF;
        if (rawSize <= 0 || packedSize <= 0 || packedSize + 8 > data.length) {
            throw new IOException("Truncated XPK file");
        }
        if ((flags & FLAG_PASSWORD) != 0) {
            throw new IOException("XPK file is password protected");
        }
        if (xor(data, 0, HEADER_LENGTH) != 0) {
            throw new IOException("Corrupt XPK header");
        }
        final ChunkUnpacker unpacker = UNPACKERS.get(packer);
        if (unpacker == null) {
            throw new IOException("XPK packer " + packer + " is not supported");
        }
        final boolean longHeaders = (flags & FLAG_LONG_HEADERS) != 0;
        int offset = (flags & FLAG_EXTRA_HEADER) != 0 ? EXTRA_HEADER_LENGTH + 2 + (buffer.getShort(EXTRA_HEADER_LENGTH) & 0xFFFF) : HEADER_LENGTH;
        final byte[] out = new byte[rawSize];
        int produced = 0;
        while (true) {
            final int headerLength = longHeaders ? LONG_CHUNK_HEADER : SHORT_CHUNK_HEADER;
            if (offset + headerLength > packedSize + 8 || xor(data, offset, offset + headerLength) != 0) {
                throw new IOException("Corrupt XPK chunk header");
            }
            final int type = data[offset] & 0xFF;
            final int chunkPacked = longHeaders ? buffer.getInt(offset + 4) : buffer.getShort(offset + 4) & 0xFFFF;
            final int chunkRaw = longHeaders ? buffer.getInt(offset + 8) : buffer.getShort(offset + 6) & 0xFFFF;
            final int dataOffset = offset + headerLength;
            if (chunkPacked < 0 || chunkRaw < 0 || dataOffset + chunkPacked > data.length) {
                throw new IOException("Truncated XPK chunk");
            }
            final byte[] chunk = Arrays.copyOfRange(data, dataOffset, dataOffset + chunkPacked);
            if (chunk.length > 0 && !checksumMatches(chunk, buffer.getShort(offset + 2) & 0xFFFF)) {
                throw new IOException("Corrupt XPK chunk");
            }
            if (type == CHUNK_END) {
                break;
            }
            if (produced + chunkRaw > rawSize) {
                throw new IOException("XPK chunks exceed the declared size");
            }
            if (type == CHUNK_STORED) {
                if (chunkRaw != chunk.length) {
                    throw new IOException("Stored XPK chunk has the wrong size");
                }
                System.arraycopy(chunk, 0, out, produced, chunkRaw);
            } else if (type == CHUNK_PACKED) {
                System.arraycopy(unpacker.unpack(chunk, chunkRaw), 0, out, produced, chunkRaw);
            } else {
                throw new IOException("Unknown XPK chunk type " + type);
            }
            produced += chunkRaw;
            offset = dataOffset + ((chunkPacked + ALIGNMENT - 1) & -ALIGNMENT);
        }
        if (produced != rawSize) {
            throw new IOException("XPK file ended before the declared size");
        }
        if (!Arrays.equals(out, 0, Math.min(rawSize, FIRST_BYTES_LENGTH), data, FIRST_BYTES, FIRST_BYTES + Math.min(rawSize, FIRST_BYTES_LENGTH))) {
            throw new IOException("XPK content does not match its header");
        }
        return out;
    }

    private static String ascii(byte[] data, int offset) {
        return new String(data, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int xor(byte[] data, int from, int to) {
        int result = 0;
        for (int i = from; i < to; i++) {
            result ^= data[i] & 0xFF;
        }
        return result;
    }

    private static boolean checksumMatches(byte[] chunk, int expected) {
        int even = 0;
        int odd = 0;
        for (int i = 0; i < chunk.length; i++) {
            if ((i & 1) == 0) {
                even ^= chunk[i] & 0xFF;
            } else {
                odd ^= chunk[i] & 0xFF;
            }
        }
        return even == (expected >> 8) && odd == (expected & 0xFF);
    }
}

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
 *
 * The container layout and the password scheme follow xDMS by Andre Rodrigues
 * de la Rocha, released to the Public Domain.
 */

package com.adeptum.paula.archive.dms;

import com.adeptum.paula.archive.ArchiveExtractor;
import com.adeptum.paula.archive.Archives;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Unpacks a DiskMasher archive into the Amiga floppy image it wraps, track by track, and offers the result as
 * an .adf entry for the archive reader to take from there; the disk's own file_id.diz, if it carried one, is
 * offered alongside it. A DMS "password" is a 16-bit rolling state XORed byte by byte rather than real
 * encryption, so a locked archive is tried at every starting state rather than asked for one.
 */
public final class DmsExtractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {'D', 'M', 'S', '!'};
    private static final int FILE_HEADER_LENGTH = 56;
    private static final int TRACK_HEADER_LENGTH = 20;

    private static final int INFO_AT = 10;
    private static final int ENCRYPTED_FLAG = 0x02;
    private static final int HD_FLAG = 0x10;
    private static final int MSDOS_FLAG = 0x20;
    private static final int DISK_TYPE_AT = 50;
    private static final int MAX_DISK_TYPE = 6;

    private static final int TRACK_NUMBER_AT = 2;
    private static final int PACKED_LENGTH_AT = 6;
    private static final int INTERMEDIATE_LENGTH_AT = 8;
    private static final int UNPACKED_LENGTH_AT = 10;
    private static final int FLAGS_AT = 12;
    private static final int MODE_AT = 13;
    private static final int CHECKSUM_AT = 14;

    private static final int MODE_NOCOMP = 0;
    private static final int MODE_HEAVY1 = 5;
    private static final int MODE_HEAVY2 = 6;
    private static final int KEEP_WINDOW_FLAG = 0x01;
    private static final int READ_TABLES_FLAG = 0x02;
    private static final int APPLY_RLE_FLAG = 0x04;

    private static final int FILE_ID_TRACK = 80;
    private static final int NO_MORE_TRACKS = 0x8000;

    private static final int DD_TRACK_LENGTH = 11264;
    private static final int HD_TRACK_LENGTH = 22528;
    private static final int DD_IMAGE_LENGTH = 901120;
    private static final int HD_IMAGE_LENGTH = 1802240;

    private static final int PASSWORD_STATES = 0x20000;
    private static final String FILE_ID = "file_id.diz";
    private static final String IMAGE_EXTENSION = ".adf";

    @Override
    public boolean matches(byte[] head) {
        return Archives.startsWith(head, MAGIC);
    }

    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        try {
            unpack(Files.readAllBytes(archive), archive.getFileName().toString(), into, wanted);
        } catch (RuntimeException e) {
            throw new IOException("Corrupt DMS archive " + archive.getFileName() + ": " + e, e);
        }
    }

    private static void unpack(byte[] data, String archiveName, Path into, Predicate<String> wanted) throws IOException {
        if (data.length < FILE_HEADER_LENGTH) {
            throw new IOException("Truncated DMS header");
        }
        if ((word(data, INFO_AT) & MSDOS_FLAG) != 0) {
            throw new IOException("DMS archive holds an MS-DOS disk, not an Amiga one");
        }
        if (word(data, DISK_TYPE_AT) > MAX_DISK_TYPE) {
            throw new IOException("DMS archive is not a disk image");
        }
        final boolean encrypted = (word(data, INFO_AT) & ENCRYPTED_FLAG) != 0;
        final boolean highDensity = (word(data, INFO_AT) & HD_FLAG) != 0;
        final int trackLength = highDensity ? HD_TRACK_LENGTH : DD_TRACK_LENGTH;

        final List<DmsTrack> tracks = readTrackHeaders(data);
        final BitReader bits = new BitReader(data, encrypted);
        if (encrypted) {
            bits.setPassword(findPassword(data, tracks, bits));
        }

        final byte[] image = new byte[highDensity ? HD_IMAGE_LENGTH : DD_IMAGE_LENGTH];
        byte[] fileId = null;
        final HeavyUnpacker heavy = new HeavyUnpacker();
        heavy.resetWindow();
        for (final DmsTrack track : tracks) {
            if (track.number() < NO_MORE_TRACKS) {
                final byte[] unpacked = unpackTrack(track, data, bits, heavy);
                if (track.number() == FILE_ID_TRACK) {
                    fileId = unpacked;
                } else if (track.number() < FILE_ID_TRACK) {
                    System.arraycopy(unpacked, 0, image, track.number() * trackLength,
                            Math.min(unpacked.length, trackLength));
                }
            }
            if ((track.flags() & KEEP_WINDOW_FLAG) == 0) {
                heavy.resetWindow();
            }
        }

        final String name = withoutExtension(archiveName) + IMAGE_EXTENSION;
        if (wanted.test(name)) {
            Files.write(Archives.target(into, name), image);
        }
        if (fileId != null && wanted.test(FILE_ID)) {
            Files.write(Archives.target(into, FILE_ID), fileId);
        }
    }

    private static List<DmsTrack> readTrackHeaders(byte[] data) {
        final List<DmsTrack> tracks = new ArrayList<>();
        int offset = FILE_HEADER_LENGTH;
        while (offset + TRACK_HEADER_LENGTH <= data.length && isTrackHeader(data, offset)) {
            final int packedLength = word(data, offset + PACKED_LENGTH_AT);
            final int dataOffset = offset + TRACK_HEADER_LENGTH;
            if (dataOffset + packedLength > data.length) {
                break;
            }
            tracks.add(new DmsTrack(word(data, offset + TRACK_NUMBER_AT), data[offset + FLAGS_AT] & 0xFF,
                    data[offset + MODE_AT] & 0xFF, dataOffset, packedLength,
                    word(data, offset + INTERMEDIATE_LENGTH_AT), word(data, offset + UNPACKED_LENGTH_AT),
                    word(data, offset + CHECKSUM_AT)));
            offset = dataOffset + packedLength;
        }
        return tracks;
    }

    private static boolean isTrackHeader(byte[] data, int offset) {
        return data[offset] == 'T' && data[offset + 1] == 'R';
    }

    private static byte[] unpackTrack(DmsTrack track, byte[] data, BitReader bits, HeavyUnpacker heavy) throws IOException {
        bits.reset(track.packedOffset(), track.packedLength());
        final byte[] unpacked = switch (track.mode()) {
            case MODE_NOCOMP -> copyRaw(bits, track.unpackedLength());
            case MODE_HEAVY1, MODE_HEAVY2 -> {
                final boolean readTables = (track.flags() & READ_TABLES_FLAG) != 0;
                final boolean applyRle = (track.flags() & APPLY_RLE_FLAG) != 0;
                final int target = applyRle ? track.intermediateLength() : track.unpackedLength();
                final byte[] heavyOutput = heavy.unpack(bits, readTables, track.mode() == MODE_HEAVY2, target);
                yield applyRle ? Rle.unpack(heavyOutput, track.unpackedLength()) : heavyOutput;
            }
            default -> throw new IOException(
                    "Unsupported DMS compression mode " + track.mode() + " for track " + track.number());
        };
        bits.skipToEnd();
        return unpacked;
    }

    private static byte[] copyRaw(BitReader bits, int length) throws IOException {
        final byte[] output = new byte[length];
        for (int i = 0; i < length; i++) {
            output[i] = (byte) bits.bits(Byte.SIZE);
        }
        return output;
    }

    /**
     * The state is only sixteen bits, so every starting value is tried against the first track's own checksum
     * rather than asking for a password that was never really needed to unpack the disk.
     */
    private static int findPassword(byte[] data, List<DmsTrack> tracks, BitReader bits) throws IOException {
        if (tracks.isEmpty()) {
            throw new IOException("DMS archive holds no tracks");
        }
        final DmsTrack first = tracks.get(0);
        for (int seed = 0; seed < PASSWORD_STATES; seed++) {
            bits.setPassword(seed);
            try {
                final byte[] unpacked = unpackTrack(first, data, bits, freshHeavyUnpacker());
                if (checksumOf(unpacked) == first.checksum()) {
                    return seed;
                }
            } catch (IOException | RuntimeException wrongState) {
                // most states decode to nonsense long before the checksum is even reached
            }
        }
        throw new IOException("DMS archive is password protected and no password matched");
    }

    private static HeavyUnpacker freshHeavyUnpacker() {
        final HeavyUnpacker heavy = new HeavyUnpacker();
        heavy.resetWindow();
        return heavy;
    }

    private static int checksumOf(byte[] bytes) {
        int sum = 0;
        for (final byte value : bytes) {
            sum = (sum + (value & 0xFF)) & 0xFFFF;
        }
        return sum;
    }

    private static String withoutExtension(String name) {
        final int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static int word(byte[] data, int at) {
        return (data[at] & 0xFF) << 8 | (data[at + 1] & 0xFF);
    }
}

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

package com.adeptum.paula.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads the programs off a 1541 disk image. A D64 has no header to know it by, only its size: a disk of 35 or
 * 40 tracks, with or without the error bytes some copiers append. The directory sits on track 18 and every file
 * is a chain of blocks, each pointing at the next.
 */
public final class D64Extractor implements ArchiveExtractor {

    private static final int BLOCK = 256;
    private static final int DIRECTORY_TRACK = 18;
    private static final int FIRST_DIRECTORY_SECTOR = 1;
    private static final int ENTRIES_PER_BLOCK = 8;
    private static final int ENTRY_LENGTH = 32;
    private static final int NAME_LENGTH = 16;
    private static final int NAME_AT = 3;
    private static final int TRACK_AT = 1;
    private static final int SECTOR_AT = 2;
    private static final int PROGRAM = 0x02;
    private static final int TYPE_MASK = 0x07;
    private static final int CLOSED = 0x80;
    private static final int NAME_PADDING = 0xA0;
    private static final int LOAD_ADDRESS_LENGTH = 2;
    private static final char REPLACEMENT = '-';
    private static final String EXTENSION = ".prg";
    private static final Set<Long> SIZES = Set.of(174848L, 175531L, 196608L, 197376L);

    /**
     * A disk image carries no magic bytes; only its size gives it away.
     */
    @Override
    public boolean matches(byte[] head) {
        return false;
    }

    @Override
    public boolean matches(byte[] head, long size) {
        return SIZES.contains(size);
    }

    /**
     * Programs are written out named after their directory entry, with the extension the players know them by,
     * since the disk keeps the name and the type apart.
     */
    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        final byte[] image = Files.readAllBytes(archive);
        for (final byte[] entry : directory(image)) {
            final String name = fileName(entry) + EXTENSION;
            if (wanted.test(name)) {
                Files.write(Archives.target(into, name), contents(image, unsigned(entry, TRACK_AT), unsigned(entry, SECTOR_AT)));
            }
        }
    }

    private static List<byte[]> directory(byte[] image) {
        final List<byte[]> entries = new ArrayList<>();
        final Set<Integer> visited = new HashSet<>();
        int track = DIRECTORY_TRACK;
        int sector = FIRST_DIRECTORY_SECTOR;
        while (visited.add(track << Byte.SIZE | sector)) {
            final int at = offsetOf(track, sector, image.length);
            if (at < 0) {
                break;
            }
            for (int entry = 0; entry < ENTRIES_PER_BLOCK; entry++) {
                final byte[] bytes = new byte[ENTRY_LENGTH];
                System.arraycopy(image, at + LOAD_ADDRESS_LENGTH + entry * ENTRY_LENGTH, bytes, 0, ENTRY_LENGTH);
                if (isProgram(bytes)) {
                    entries.add(bytes);
                }
            }
            track = image[at] & 0xFF;
            sector = image[at + 1] & 0xFF;
            if (track == 0) {
                break;
            }
        }
        return entries;
    }

    private static boolean isProgram(byte[] entry) {
        return (entry[0] & CLOSED) != 0 && (entry[0] & TYPE_MASK) == PROGRAM;
    }

    /**
     * The last block of a file says in its link how many of its bytes are used.
     */
    private static byte[] contents(byte[] image, int firstTrack, int firstSector) {
        final List<byte[]> blocks = new ArrayList<>();
        final Set<Integer> visited = new HashSet<>();
        int track = firstTrack;
        int sector = firstSector;
        int length = 0;
        while (track != 0 && visited.add(track << Byte.SIZE | sector)) {
            final int at = offsetOf(track, sector, image.length);
            if (at < 0) {
                break;
            }
            final int next = image[at] & 0xFF;
            final int used = next == 0 ? Math.max(1, image[at + 1] & 0xFF) - 1 : BLOCK - LOAD_ADDRESS_LENGTH;
            final byte[] block = new byte[used];
            System.arraycopy(image, at + LOAD_ADDRESS_LENGTH, block, 0, Math.min(used, image.length - at - LOAD_ADDRESS_LENGTH));
            blocks.add(block);
            length += block.length;
            sector = image[at + 1] & 0xFF;
            track = next;
        }
        final byte[] file = new byte[length];
        int written = 0;
        for (final byte[] block : blocks) {
            System.arraycopy(block, 0, file, written, block.length);
            written += block.length;
        }
        return file;
    }

    /**
     * Tracks are numbered from one and hold fewer sectors the further out they lie.
     */
    private static int offsetOf(int track, int sector, int size) {
        if (track < 1 || sector < 0 || sector >= sectorsOn(track)) {
            return -1;
        }
        int offset = 0;
        for (int before = 1; before < track; before++) {
            offset += sectorsOn(before) * BLOCK;
        }
        offset += sector * BLOCK;
        return offset + BLOCK <= size ? offset : -1;
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
     * Disk names are padded with shifted spaces and may hold the slashes and dots of a scene handle, none of
     * which belong in a file name.
     */
    private static String fileName(byte[] entry) {
        final StringBuilder name = new StringBuilder(NAME_LENGTH);
        for (int at = 0; at < NAME_LENGTH; at++) {
            final int character = entry[NAME_AT + at] & 0xFF;
            if (character == NAME_PADDING || character == 0) {
                break;
            }
            name.append(character < ' ' || character > '~' || character == '/' || character == '\\' ? REPLACEMENT : (char) character);
        }
        final String trimmed = name.toString().strip();
        return trimmed.isEmpty() ? "program" : trimmed;
    }

    private static int unsigned(byte[] entry, int at) {
        return entry[at] & 0xFF;
    }
}

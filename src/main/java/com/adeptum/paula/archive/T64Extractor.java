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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads the programs out of a C64 tape image. The directory follows a header that names the tape, and each
 * entry says where in the file its program lies and which addresses it was saved between.
 */
public final class T64Extractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {'C', '6', '4'};
    private static final String TAPE = "tape";
    private static final int MAX_ENTRIES_AT = 0x22;
    private static final int DIRECTORY_AT = 0x40;
    private static final int ENTRY_LENGTH = 32;
    private static final int TYPE_AT = 0;
    private static final int START_AT = 2;
    private static final int END_AT = 4;
    private static final int OFFSET_AT = 8;
    private static final int NAME_AT = 16;
    private static final int FREE = 0;
    private static final int LOAD_ADDRESS_LENGTH = 2;
    private static final String EXTENSION = ".prg";
    private static final String REPEAT = "-";

    /**
     * All four spellings of the header begin with the machine and name the medium within the bytes read to
     * tell the formats apart.
     */
    @Override
    public boolean matches(byte[] head) {
        return Archives.startsWith(head, MAGIC)
                && new String(head, StandardCharsets.ISO_8859_1).contains(TAPE);
    }

    /**
     * A program is written out under the name its entry carries, with the two bytes of its load address put
     * back at the head, since a tape keeps those in the directory rather than with the bytes.
     */
    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        final byte[] image = Files.readAllBytes(archive);
        final Set<String> taken = new HashSet<>();
        for (int entry = 0; entry < entries(image); entry++) {
            final int at = DIRECTORY_AT + entry * ENTRY_LENGTH;
            if (at + ENTRY_LENGTH > image.length || image[at + TYPE_AT] == FREE || !holdsProgram(image, at)) {
                continue;
            }
            final String name = unique(C64Names.of(image, at + NAME_AT), taken) + EXTENSION;
            if (wanted.test(name)) {
                Files.write(Archives.target(into, name), program(image, at));
            }
        }
    }

    private static int entries(byte[] image) {
        final int room = Math.max(0, image.length - DIRECTORY_AT) / ENTRY_LENGTH;
        return Math.min(word(image, MAX_ENTRIES_AT), room);
    }

    /**
     * A tape holds several programs of one name as readily as a disk holds none, so a name already given out
     * is counted up rather than written over.
     */
    private static String unique(String name, Set<String> taken) {
        String unique = name;
        for (int repeat = 2; !taken.add(unique); repeat++) {
            unique = name + REPEAT + repeat;
        }
        return unique;
    }

    /**
     * An entry pointing outside the image is passed over rather than taken for the whole tape being unreadable.
     */
    private static boolean holdsProgram(byte[] image, int at) {
        final long offset = unsignedInt(image, at + OFFSET_AT);
        return word(image, at + END_AT) > word(image, at + START_AT)
                && offset >= DIRECTORY_AT && offset < image.length;
    }

    /**
     * Writers of the day left the end address as anything from the truth to a constant, so the program is cut
     * at whichever of the two ends comes first.
     */
    private static byte[] program(byte[] image, int at) {
        final int start = word(image, at + START_AT);
        final int offset = (int) unsignedInt(image, at + OFFSET_AT);
        final int length = Math.min(word(image, at + END_AT) - start, image.length - offset);
        final byte[] program = new byte[LOAD_ADDRESS_LENGTH + length];
        program[0] = (byte) start;
        program[1] = (byte) (start >> Byte.SIZE);
        System.arraycopy(image, offset, program, LOAD_ADDRESS_LENGTH, length);
        return program;
    }

    private static int word(byte[] image, int at) {
        return at + 1 < image.length ? (image[at] & 0xFF) | (image[at + 1] & 0xFF) << Byte.SIZE : 0;
    }

    private static long unsignedInt(byte[] image, int at) {
        return (long) word(image, at) | (long) word(image, at + 2) << Short.SIZE;
    }
}

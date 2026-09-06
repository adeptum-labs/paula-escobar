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
 * The decruncher follows the PowerPacker reader of libsidplay2, as vendored
 * in JavaMod, Copyright © Michael Schwendt and Dag Lem, licensed under the
 * GNU General Public License.
 */

package com.adeptum.paula.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Decrunches the PowerPacker files the Amiga scene stored its modules in. A crunched file is read from its end
 * backwards: the last word says how long the file was and how many bits of the word before it are padding, and
 * the bit stream that follows runs backwards through the file, filling the output from its end towards its
 * start.
 */
public final class PowerPackerExtractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {'P', 'P', '2', '0'};

    /**
     * The four widths an offset is written in, one per length of match. A cruncher wrote one of five sets and
     * nothing else, which together with the identifier is enough to know the format by.
     */
    private static final Set<Integer> EFFICIENCIES =
            Set.of(0x09090909, 0x090A0A0A, 0x090A0B0B, 0x090A0C0C, 0x090A0C0D);

    private static final int EFFICIENCY_AT = 4;
    private static final int HEADER_LENGTH = 8;
    private static final int TRAILER_LENGTH = 4;
    private static final int WORD_BITS = 32;
    private static final int SKIP_MASK = 0xFF;
    private static final int LENGTH_SHIFT = 8;
    private static final int LONGEST_MATCH_INDEX = 3;
    private static final int WIDE_OFFSET_WIDTH = 7;
    private static final int RUN_BITS = 2;
    private static final int RUN_CONTINUES = 3;
    private static final int MATCH_BITS = 3;
    private static final int MATCH_CONTINUES = 7;
    private static final int SHORTEST_MATCH = 2;
    private static final String SUFFIX = ".pp";

    @Override
    public boolean matches(byte[] head) {
        return Archives.startsWith(head, MAGIC) && head.length >= HEADER_LENGTH
                && EFFICIENCIES.contains(bigEndianAt(head, EFFICIENCY_AT));
    }

    @Override
    public boolean wrapsSingleFile() {
        return true;
    }

    /**
     * Most crunched modules kept the name they were tracked under, so only a file that marks the wrapping in
     * its name loses anything when it is unwrapped.
     */
    @Override
    public String unwrappedName(String archive) {
        return archive.toLowerCase(Locale.ROOT).endsWith(SUFFIX)
                ? archive.substring(0, archive.length() - SUFFIX.length()) : archive;
    }

    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        final String name = unwrappedName(archive.getFileName().toString());
        if (wanted.test(name)) {
            Files.write(Archives.target(into, name), decrunch(archive, Files.readAllBytes(archive)));
        }
    }

    private static byte[] decrunch(Path archive, byte[] file) throws IOException {
        if (file.length < HEADER_LENGTH + TRAILER_LENGTH) {
            throw corrupt(archive);
        }
        final int trailer = bigEndianAt(file, file.length - TRAILER_LENGTH);
        final byte[] out = new byte[trailer >>> LENGTH_SHIFT];
        final Crunched crunched = new Crunched(file, archive, trailer & SKIP_MASK);
        int writePtr = out.length;
        while (writePtr > 0) {
            if (crunched.bits(1) == 0) {
                writePtr = literals(crunched, out, writePtr);
            }
            if (writePtr > 0) {
                writePtr = match(crunched, out, writePtr, file);
            }
        }
        return out;
    }

    /**
     * A run of two-bit counts, each adding to the length and the last one short of its own maximum.
     */
    private static int literals(Crunched crunched, byte[] out, int from) throws IOException {
        int add = crunched.bits(RUN_BITS);
        int count = add;
        while (add == RUN_CONTINUES) {
            add = crunched.bits(RUN_BITS);
            count += add;
        }
        int writePtr = from;
        for (count++; count > 0; count--) {
            if (writePtr == 0) {
                throw crunched.corrupt();
            }
            out[--writePtr] = (byte) crunched.bits(Byte.SIZE);
        }
        return writePtr;
    }

    /**
     * The two-bit index chooses both how far back the offset is written and how long the match is; only the
     * longest of the four carries a length of its own, and a short offset there is written narrower still.
     */
    private static int match(Crunched crunched, byte[] out, int from, byte[] file) throws IOException {
        final int index = crunched.bits(RUN_BITS);
        int width = file[EFFICIENCY_AT + index] & 0xFF;
        int length = index + SHORTEST_MATCH;
        final int offset;
        if (index == LONGEST_MATCH_INDEX) {
            if (crunched.bits(1) == 0) {
                width = WIDE_OFFSET_WIDTH;
            }
            offset = crunched.bits(width);
            int add;
            do {
                add = crunched.bits(MATCH_BITS);
                length += add;
            } while (add == MATCH_CONTINUES);
        } else {
            offset = crunched.bits(width);
        }
        int writePtr = from;
        for (; length > 0; length--) {
            if (writePtr == 0 || writePtr + offset >= out.length) {
                throw crunched.corrupt();
            }
            writePtr--;
            out[writePtr] = out[writePtr + 1 + offset];
        }
        return writePtr;
    }

    private static int bigEndianAt(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16
                | (bytes[at + 2] & 0xFF) << 8 | bytes[at + 3] & 0xFF;
    }

    private static IOException corrupt(Path archive) {
        return new IOException("Corrupt PowerPacker file " + archive.getFileName());
    }

    /**
     * The bit stream, read backwards a longword at a time and lowest bit first.
     */
    private static final class Crunched {

        private final byte[] file;
        private final Path archive;
        private int readPtr;
        private int current;
        private int left;

        private Crunched(byte[] file, Path archive, int skip) throws IOException {
            this.file = file;
            this.archive = archive;
            this.readPtr = file.length - TRAILER_LENGTH;
            this.left = WORD_BITS - skip;
            nextWord();
            if (left != WORD_BITS) {
                current >>>= WORD_BITS - left;
            }
        }

        private int bits(int count) throws IOException {
            int result = 0;
            for (int bit = 0; bit < count; bit++) {
                result = result << 1 | current & 1;
                current >>>= 1;
                if (--left == 0) {
                    nextWord();
                    left = WORD_BITS;
                }
            }
            return result;
        }

        /**
         * The last bits of a stream are followed by a word that is fetched and never read, so the header is
         * fair game to land on; only running off the front of the file says the stream is broken.
         */
        private void nextWord() throws IOException {
            readPtr -= TRAILER_LENGTH;
            if (readPtr < 0) {
                throw corrupt();
            }
            current = bigEndianAt(file, readPtr);
        }

        private IOException corrupt() {
            return PowerPackerExtractor.corrupt(archive);
        }
    }
}

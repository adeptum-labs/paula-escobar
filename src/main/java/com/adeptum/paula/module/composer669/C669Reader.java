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

package com.adeptum.paula.module.composer669;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a 669 file: a header of 497 bytes, a 25-byte header for each sample, the patterns at 1536 bytes each
 * and then the samples themselves as unsigned bytes. The mark at the head is "if" for Composer 669 and "JN"
 * for UNIS 669, and since two letters are easily matched by chance the rest of the header is checked the way
 * OpenMPT checks it before a file is taken for one.
 */
final class C669Reader {

    private static final int HEADER_LENGTH = 497;
    private static final int MARK_LENGTH = 2;
    private static final String STANDARD_MARK = "if";
    private static final String EXTENDED_MARK = "JN";
    private static final int MESSAGE_LINES = 3;
    private static final int MESSAGE_LINE_LENGTH = 36;
    private static final int MAX_CONTROL_CHARACTERS = 40;
    private static final int LAST_CONTROL_CHARACTER = 31;
    private static final int LIST_LENGTH = 128;
    private static final int MAX_SAMPLES = 64;
    private static final int MAX_PATTERNS = 128;
    private static final int MAX_SPEED = 15;
    private static final int SAMPLE_HEADER_LENGTH = 25;
    private static final int SAMPLE_NAME_LENGTH = 13;
    private static final long LONGEST_SAMPLE = 0x4000000;
    private static final int CELL_LENGTH = 3;
    private static final int PATTERN_LENGTH = C669Pattern.ROWS * C669Pattern.CHANNELS * CELL_LENGTH;
    private static final int END_OF_ORDERS = 0xFF;
    private static final int SKIPPED_ORDER = 0xFE;
    private static final int VOLUME_ONLY = 0xFE;
    private static final int EMPTY = 0xFF;
    private static final int NIBBLE = 0x0F;
    private static final int NOTE_SHIFT = 2;
    private static final int INSTRUMENT_HIGH_MASK = 0x03;
    private static final int UNSIGNED_MIDDLE = 128;
    private static final int WIDEN = 8;

    private record Header(String name, int length, long loopStart, long loopEnd) {
    }

    private C669Reader() {
    }

    static C669File read(byte[] file) throws IOException {
        final ByteBuffer in = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        require(in, HEADER_LENGTH);
        final boolean extended = extended(in);
        final List<String> message = message(in);
        final int samples = unsigned(in.get());
        final int patterns = unsigned(in.get());
        final int restart = unsigned(in.get());
        if (samples > MAX_SAMPLES || patterns > MAX_PATTERNS || restart >= LIST_LENGTH) {
            throw new IOException("more samples, patterns or positions than Composer 669 allows");
        }
        final int[] orderList = list(in);
        final int[] speeds = list(in);
        final int[] breaks = list(in);
        validate(orderList, speeds, breaks);
        final List<Header> headers = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            headers.add(header(in));
        }
        final List<C669Pattern> patternList = new ArrayList<>(patterns);
        for (int number = 0; number < patterns; number++) {
            patternList.add(pattern(in, speeds[number], breaks[number]));
        }
        final List<C669Sample> sampleList = new ArrayList<>(samples);
        for (final Header header : headers) {
            sampleList.add(sample(in, header));
        }
        return new C669File(extended, message, sampleList, patternList, orders(orderList, patterns), restart);
    }

    private static boolean extended(ByteBuffer in) throws IOException {
        final String mark = new String(bytes(in, MARK_LENGTH), StandardCharsets.US_ASCII);
        if (mark.equals(EXTENDED_MARK)) {
            return true;
        }
        if (mark.equals(STANDARD_MARK)) {
            return false;
        }
        throw new IOException("not a 669 module");
    }

    private static List<String> message(ByteBuffer in) throws IOException {
        final List<String> lines = new ArrayList<>(MESSAGE_LINES);
        int controlCharacters = 0;
        for (int line = 0; line < MESSAGE_LINES; line++) {
            final byte[] text = bytes(in, MESSAGE_LINE_LENGTH);
            for (final byte c : text) {
                controlCharacters += c > 0 && c <= LAST_CONTROL_CHARACTER ? 1 : 0;
            }
            lines.add(text(text));
        }
        if (controlCharacters > MAX_CONTROL_CHARACTERS) {
            throw new IOException("the message is not text");
        }
        return lines;
    }

    private static int[] list(ByteBuffer in) {
        final int[] list = new int[LIST_LENGTH];
        for (int i = 0; i < LIST_LENGTH; i++) {
            list[i] = unsigned(in.get());
        }
        return list;
    }

    /**
     * The checks OpenMPT makes, including its reading of the speed list against the order it shares an index
     * with, since a file that fails them is far more likely another format that happens to start with "if".
     */
    private static void validate(int[] orders, int[] speeds, int[] breaks) throws IOException {
        for (int i = 0; i < LIST_LENGTH; i++) {
            final boolean playable = orders[i] < LIST_LENGTH;
            if ((!playable && orders[i] < SKIPPED_ORDER) || (playable && speeds[i] == 0) || speeds[i] > MAX_SPEED
                    || breaks[i] >= C669Pattern.ROWS) {
                throw new IOException("the order, speed or break list is not one Composer 669 wrote");
            }
        }
    }

    private static int[] orders(int[] list, int patterns) {
        final List<Integer> orders = new ArrayList<>();
        for (int i = 0; i < LIST_LENGTH && list[i] != END_OF_ORDERS; i++) {
            if (list[i] < patterns) {
                orders.add(list[i]);
            }
        }
        return orders.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Header header(ByteBuffer in) throws IOException {
        require(in, SAMPLE_HEADER_LENGTH);
        final String name = text(bytes(in, SAMPLE_NAME_LENGTH));
        final long length = Integer.toUnsignedLong(in.getInt());
        if (length >= LONGEST_SAMPLE) {
            throw new IOException("a sample longer than any Composer 669 wrote");
        }
        return new Header(name, (int) length, Integer.toUnsignedLong(in.getInt()), Integer.toUnsignedLong(in.getInt()));
    }

    private static C669Pattern pattern(ByteBuffer in, int speed, int breakRow) throws IOException {
        require(in, PATTERN_LENGTH);
        final C669Cell[][] cells = new C669Cell[C669Pattern.ROWS][C669Pattern.CHANNELS];
        for (final C669Cell[] row : cells) {
            for (int channel = 0; channel < C669Pattern.CHANNELS; channel++) {
                row[channel] = cell(unsigned(in.get()), unsigned(in.get()), unsigned(in.get()));
            }
        }
        return new C669Pattern(speed, breakRow, cells);
    }

    /**
     * The first byte holds the note over the instrument's top two bits, or a mark that only a volume follows,
     * or one that nothing does; the second the instrument's low nibble over the volume; the third the effect.
     */
    private static C669Cell cell(int first, int second, int third) {
        final boolean hasNote = first < VOLUME_ONLY;
        final int note = hasNote ? first >> NOTE_SHIFT : C669Cell.NONE;
        final int instrument = hasNote ? (first & INSTRUMENT_HIGH_MASK) << 4 | second >> 4 : C669Cell.NONE;
        final int volume = first <= VOLUME_ONLY ? second & NIBBLE : C669Cell.NONE;
        final int command = third != EMPTY ? third >> 4 : C669Cell.NONE;
        final int parameter = third != EMPTY ? third & NIBBLE : 0;
        return new C669Cell(note, instrument, volume, command, parameter);
    }

    /**
     * A loop end past the sample with a start at zero is how the tracker writes no loop at all; any other end
     * loops, held within the sample.
     */
    private static C669Sample sample(ByteBuffer in, Header header) throws IOException {
        final byte[] raw = bytes(in, header.length());
        final short[] data = new short[raw.length];
        for (int i = 0; i < raw.length; i++) {
            data[i] = (short) ((unsigned(raw[i]) - UNSIGNED_MIDDLE) << WIDEN);
        }
        final boolean unlooped = header.loopEnd() == 0 || (header.loopEnd() > header.length() && header.loopStart() == 0);
        final int end = unlooped ? 0 : (int) Math.min(header.loopEnd(), header.length());
        final int start = (int) Math.min(header.loopStart(), end);
        return new C669Sample(header.name(), data, start, end);
    }

    private static String text(byte[] bytes) {
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, StandardCharsets.ISO_8859_1).stripTrailing();
    }

    private static byte[] bytes(ByteBuffer in, int count) throws IOException {
        require(in, count);
        final byte[] bytes = new byte[count];
        in.get(bytes);
        return bytes;
    }

    private static void require(ByteBuffer in, int count) throws IOException {
        if (in.remaining() < count) {
            throw new IOException("the file is cut short");
        }
    }

    private static int unsigned(byte value) {
        return value & EMPTY;
    }
}

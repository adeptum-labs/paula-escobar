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
 * The layout follows Load_ult.cpp of OpenMPT, Copyright © 2004-2026 the
 * OpenMPT project developers and Copyright © 1997-2003 Olivier Lapicque,
 * which ports Storlek's reader from Schism Tracker; licensed under the
 * three-clause BSD licence and used here under the GNU General Public
 * License.
 */

package com.adeptum.paula.module.ult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an UltraTracker module. After the header come the message, the sample headers, the order, the channel
 * count and panning, then the patterns written channel by channel rather than row by row, and last the
 * sample data one sample after another.
 */
final class UltReader {

    private static final String SIGNATURE = "MAS_UTrack_V00";
    private static final char OLDEST = '1';
    private static final char NEWEST = '4';
    private static final char WITH_PANNING = '3';
    private static final char WITH_SAMPLE_SPEED = '4';
    private static final int NAME_LENGTH = 32;
    private static final int FILE_NAME_LENGTH = 12;
    private static final int MESSAGE_LINE = 32;
    private static final int ORDERS = 256;
    private static final int ORDER_END = 0xff;
    private static final int REPEAT = 0xfc;
    private static final int EVENT_BYTES = 5;
    private static final int OLD_SPEED = 8363;
    private static final int LEFT = 64;
    private static final int RIGHT = 192;
    private static final int MOST_CHANNELS = 127;
    private static final int LAST_NOTE = 96;

    private final byte[] file;
    private int at;

    private UltReader(byte[] file) {
        this.file = file;
    }

    static UltFile read(byte[] file) throws IOException {
        return new UltReader(file).module();
    }

    private UltFile module() throws IOException {
        if (file.length < SIGNATURE.length() + 1 || !text(SIGNATURE.length()).equals(SIGNATURE)) {
            throw new IOException("Not an UltraTracker module");
        }
        final char version = (char) byteValue();
        if (version < OLDEST || version > NEWEST) {
            throw new IOException("UltraTracker module of a version that never existed");
        }
        final String title = text(NAME_LENGTH).strip();
        final String message = message(byteValue());
        final UltSample[] headers = sampleHeaders(version);
        final int[] orders = orders();
        final int channels = byteValue() + 1;
        final int patterns = byteValue() + 1;
        if (channels > MOST_CHANNELS) {
            throw new IOException("UltraTracker module with more channels than it can hold");
        }
        final int[] panning = panning(version, channels);
        final UltEvent[][][] events = patterns(channels, patterns);
        return new UltFile(version, title, message, withData(headers), orders, channels, panning, events);
    }

    private String message(int lines) throws IOException {
        final List<String> read = new ArrayList<>();
        for (int line = 0; line < lines; line++) {
            read.add(text(MESSAGE_LINE).stripTrailing());
        }
        return String.join("\n", read).strip();
    }

    /**
     * Version four put the speed in front of the finetune; before it the finetune sits where the speed went
     * and every sample sounds at the same speed.
     */
    private UltSample[] sampleHeaders(char version) throws IOException {
        final UltSample[] samples = new UltSample[byteValue()];
        for (int number = 0; number < samples.length; number++) {
            final String name = text(NAME_LENGTH).strip();
            final String fileName = text(FILE_NAME_LENGTH).strip();
            final int loopStart = intValue();
            final int loopEnd = intValue();
            final int sizeStart = intValue();
            final int sizeEnd = intValue();
            final int volume = byteValue();
            final int flags = byteValue();
            final int speed = version >= WITH_SAMPLE_SPEED ? shortValue() : OLD_SPEED;
            final int finetune = (short) shortValue();
            final int length = Math.max(0, sizeEnd - sizeStart);
            samples[number] = new UltSample(name, fileName, loopStart, loopEnd, volume, flags, speed, finetune,
                    new int[length]);
        }
        return samples;
    }

    private int[] orders() throws IOException {
        final List<Integer> orders = new ArrayList<>();
        for (int order = 0; order < ORDERS; order++) {
            final int pattern = byteValue();
            if (pattern == ORDER_END) {
                at += ORDERS - order - 1;
                break;
            }
            orders.add(pattern);
        }
        return orders.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] panning(char version, int channels) throws IOException {
        final int[] panning = new int[channels];
        for (int channel = 0; channel < channels; channel++) {
            panning[channel] = version >= WITH_PANNING ? (byteValue() & 0x0f) << 4 | 8 : (channel & 1) == 0 ? LEFT : RIGHT;
        }
        return panning;
    }

    /**
     * An event marked to repeat stands for that many identical rows, cut short at the end of the pattern;
     * one repeated no times at all ends that channel's pattern where it stands. A file that runs out part way
     * leaves the rest silent, as the tracker would have.
     */
    private UltEvent[][][] patterns(int channels, int patterns) {
        final UltEvent[][][] events = new UltEvent[patterns][channels][UltFile.ROWS];
        for (final UltEvent[][] pattern : events) {
            for (final UltEvent[] channel : pattern) {
                java.util.Arrays.fill(channel, UltEvent.EMPTY);
            }
        }
        for (int channel = 0; channel < channels; channel++) {
            for (int pattern = 0; pattern < patterns && at + EVENT_BYTES <= file.length; pattern++) {
                int row = 0;
                while (row < UltFile.ROWS) {
                    int repeat = 1;
                    int note = byteOrNothing();
                    if (note == REPEAT) {
                        repeat = byteOrNothing();
                        note = byteOrNothing();
                    }
                    final int instrument = byteOrNothing();
                    final int effects = byteOrNothing();
                    final UltEvent event = new UltEvent(note > 0 && note <= LAST_NOTE ? note : 0, instrument,
                            effects & 0x0f, byteOrNothing(), effects >> 4, byteOrNothing());
                    repeat = Math.min(repeat, UltFile.ROWS - row);
                    if (repeat == 0) {
                        break;
                    }
                    for (; repeat > 0; repeat--) {
                        events[pattern][channel][row++] = event;
                    }
                }
            }
        }
        return events;
    }

    /**
     * The data follows the patterns in the order of the headers, sixteen-bit samples two bytes a frame; a
     * sample the file stops short of keeps what it has and is silent past it.
     */
    private UltSample[] withData(UltSample[] headers) {
        final UltSample[] samples = new UltSample[headers.length];
        for (int number = 0; number < headers.length; number++) {
            final UltSample header = headers[number];
            final boolean wide = header.has(UltSample.SIXTEEN_BIT);
            final int[] data = header.data();
            for (int frame = 0; frame < data.length && at + (wide ? 2 : 1) <= file.length; frame++) {
                data[frame] = wide ? (short) (file[at++] & 0xff | file[at++] << 8) : file[at++];
            }
            samples[number] = header;
        }
        return samples;
    }

    private String text(int length) throws IOException {
        ensure(length);
        final String text = new String(file, at, length, StandardCharsets.ISO_8859_1).replace('\0', ' ');
        at += length;
        return text;
    }

    private int byteValue() throws IOException {
        ensure(1);
        return file[at++] & 0xff;
    }

    /**
     * A pattern the file stops short of reads on as nothing, as the tracker's own reader did.
     */
    private int byteOrNothing() {
        return at < file.length ? file[at++] & 0xff : 0;
    }

    private int shortValue() throws IOException {
        return byteValue() | byteValue() << Byte.SIZE;
    }

    private int intValue() throws IOException {
        return shortValue() | shortValue() << Short.SIZE;
    }

    private void ensure(int length) throws IOException {
        if (at + length > file.length) {
            throw new IOException("UltraTracker module ends before its header does");
        }
    }
}

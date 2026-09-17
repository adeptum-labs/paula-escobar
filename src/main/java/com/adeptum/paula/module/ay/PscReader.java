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


package com.adeptum.paula.module.ay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * Reads a module of Pro Sound Creator, following ZXTune's reading of the format. A header naming the editor,
 * title and author is followed by the tables of sample and ornament offsets, the samples and ornaments, the
 * patterns' streams of commands and last the order, where each position names its length and where its three
 * channels' commands begin.
 */
final class PscReader {

    /**
     * The player only ever reached this far into a module, so anything past it is not part of the tune.
     */
    private static final int LARGEST = 0x4200;
    private static final int SMALLEST = 256;
    private static final String MARK = "PSC V";
    private static final String COMPILATION = " COMPILATION OF ";
    private static final int VERSION_AT = 5;
    private static final int COMPILATION_AT = 9;
    private static final int TITLE_AT = 25;
    private static final int BY_AT = 45;
    private static final int AUTHOR_AT = 49;
    private static final int NAME_LENGTH = 20;
    private static final int SAMPLES_START_AT = 69;
    private static final int POSITIONS_AT = 71;
    private static final int TEMPO_AT = 73;
    private static final int ORNAMENTS_TABLE_AT = 74;
    private static final int HEADER = 76;
    private static final int FIRST_RELATIVE_VERSION = 103;
    private static final int SLOWEST_TEMPO = 3;
    private static final int FASTEST_TEMPO = 0x1f;
    private static final int LOWEST_POSITIONS_PAGE = 3;
    private static final int HIGHEST_POSITIONS_PAGE = 0x3f;
    private static final int LOWEST_ORNAMENTS_TABLE = 0x50;
    private static final int HIGHEST_ORNAMENTS_TABLE = 0x90;
    private static final int LOWEST_FIRST_SAMPLE = 0x08;
    private static final int HIGHEST_FIRST_SAMPLE = 0xcf;
    private static final int POSITION_BYTES = 8;
    private static final int POSITIONS_END = 0xff;
    private static final int END_BYTES = 4;
    private static final int SAMPLE_LINE_BYTES = 6;
    private static final int ORNAMENT_LINE_BYTES = 2;
    private static final int LONGEST_OBJECT = 32;
    private static final int NO_AREA = Integer.MAX_VALUE;

    private final byte[] file;
    private final int samplesBase;
    private final int ornamentsBase;
    private final TreeSet<Integer> usedSamples = new TreeSet<>(List.of(0));
    private final TreeSet<Integer> usedOrnaments = new TreeSet<>(List.of(0));

    private PscReader(byte[] file) {
        this.file = file;
        final boolean relative = version() == 0 || version() >= FIRST_RELATIVE_VERSION;
        this.samplesBase = relative ? HEADER : 0;
        this.ornamentsBase = relative ? shortValue(ORNAMENTS_TABLE_AT) : 0;
    }

    static PscFile read(byte[] bytes) throws IOException {
        final PscReader reader = new PscReader(Arrays.copyOf(bytes, Math.min(bytes.length, LARGEST)));
        reader.check();
        return reader.module();
    }

    /**
     * Not every module carries the editor's mark, so a module is known by its shape: fields in the ranges the
     * editor wrote, and the areas the header points at lying in the order the editor laid them out, each large
     * enough for what it holds.
     */
    private void check() throws IOException {
        if (file.length < SMALLEST || byteAt(SAMPLES_START_AT + 1) != 0
                || !within(byteAt(POSITIONS_AT + 1), LOWEST_POSITIONS_PAGE, HIGHEST_POSITIONS_PAGE)
                || !within(byteAt(TEMPO_AT), SLOWEST_TEMPO, FASTEST_TEMPO)
                || !within(shortValue(ORNAMENTS_TABLE_AT), LOWEST_ORNAMENTS_TABLE, HIGHEST_ORNAMENTS_TABLE)
                || !within(shortValue(HEADER), LOWEST_FIRST_SAMPLE, HIGHEST_FIRST_SAMPLE)) {
            throw notAModule();
        }
        final int ornamentsTable = shortValue(ORNAMENTS_TABLE_AT);
        final int positions = shortValue(POSITIONS_AT);
        final int firstSample = shortValue(HEADER) + samplesBase;
        final int samples = firstSample == shortValue(SAMPLES_START_AT) + 1 ? firstSample : NO_AREA;
        final int ornaments = shortValue(ornamentsTable) + ornamentsBase;
        final int patterns = IntStream.of(shortValue(positions + 2), shortValue(positions + 4),
                shortValue(positions + 6)).min().orElseThrow();
        final int[] areas = {0, HEADER, ornamentsTable, positions, file.length, samples, ornaments, patterns};
        final int samplesTableSize = sizeOf(HEADER, areas);
        if (sizeOf(0, areas) != HEADER || sizeOf(file.length, areas) != NO_AREA || ornamentsTable <= HEADER
                || samples >= ornaments || samplesTableSize < 2 || samplesTableSize % 2 != 0
                || sizeOf(samples, areas) < SAMPLE_LINE_BYTES || sizeOf(ornamentsTable, areas) < 2
                || sizeOf(ornaments, areas) < ORNAMENT_LINE_BYTES || sizeOf(positions, areas) < POSITION_BYTES + END_BYTES
                || sizeOf(patterns, areas) == NO_AREA) {
            throw notAModule();
        }
    }

    /**
     * How far an area runs, which is up to the nearest area beginning after it.
     */
    private static int sizeOf(int area, int[] areas) {
        if (area == NO_AREA) {
            return NO_AREA;
        }
        final int next = Arrays.stream(areas).filter(other -> other > area).min().orElse(NO_AREA);
        return next == NO_AREA ? NO_AREA : next - area;
    }

    private static boolean within(int value, int lowest, int highest) {
        return value >= lowest && value <= highest;
    }

    private PscFile module() throws IOException {
        final List<int[]> starts = new ArrayList<>();
        final List<Integer> lengths = new ArrayList<>();
        final int[] positions = positions(starts, lengths);
        final PscPattern[] patterns = new PscPattern[starts.size()];
        boolean anyLines = false;
        for (int pattern = 0; pattern < patterns.length; pattern++) {
            patterns[pattern] = pattern(starts.get(pattern), lengths.get(pattern));
            anyLines |= lengths.get(pattern) > 0;
        }
        if (!anyLines) {
            throw notAModule();
        }
        return new PscFile(byteAt(TEMPO_AT), loop(positions.length), title(), author(), positions, samples(),
                ornaments(), patterns);
    }

    /**
     * Each position names a pattern by its length and where its channels begin; positions naming the same are
     * read as the one pattern.
     */
    private int[] positions(List<int[]> starts, List<Integer> lengths) throws IOException {
        final List<Integer> order = new ArrayList<>();
        for (int at = shortValue(POSITIONS_AT); ; at += POSITION_BYTES) {
            if (at + END_BYTES > file.length) {
                throw notAModule();
            }
            if (byteAt(at + 1) == POSITIONS_END) {
                break;
            }
            if (at + POSITION_BYTES > file.length) {
                throw notAModule();
            }
            final int[] channels = {shortValue(at + 2), shortValue(at + 4), shortValue(at + 6)};
            final int length = byteAt(at + 1);
            int pattern = 0;
            while (pattern < starts.size()
                    && !(lengths.get(pattern) == length && Arrays.equals(starts.get(pattern), channels))) {
                pattern++;
            }
            if (pattern == starts.size()) {
                starts.add(channels);
                lengths.add(length);
            }
            order.add(pattern);
        }
        if (order.isEmpty()) {
            throw notAModule();
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    private int loop(int positions) {
        int at = shortValue(POSITIONS_AT);
        while (byteAt(at + 1) != POSITIONS_END) {
            at += POSITION_BYTES;
        }
        return Math.min(byteAt(at), positions - 1);
    }

    private String title() {
        if (!marked()) {
            return "";
        }
        return hasAuthor() ? name(TITLE_AT, NAME_LENGTH) : name(TITLE_AT, AUTHOR_AT + NAME_LENGTH - TITLE_AT);
    }

    private String author() {
        return hasAuthor() ? name(AUTHOR_AT, NAME_LENGTH) : "";
    }

    private boolean hasAuthor() {
        return marked() && name(BY_AT, AUTHOR_AT - BY_AT).toUpperCase(Locale.ROOT).equals("BY");
    }

    private boolean marked() {
        return text(0, MARK.length()).equals(MARK) && text(COMPILATION_AT, COMPILATION.length()).equals(COMPILATION);
    }

    /**
     * The version as written after the mark, one hundred and seven for "1.07", or none where it is not written
     * as a version.
     */
    private int version() {
        final int[] digits = {byteAt(VERSION_AT) - '0', byteAt(VERSION_AT + 2) - '0', byteAt(VERSION_AT + 3) - '0'};
        final boolean written =
                byteAt(VERSION_AT + 1) == '.' && Arrays.stream(digits).allMatch(digit -> within(digit, 0, 9));
        return written ? digits[0] * 100 + digits[1] * 10 + digits[2] : 0;
    }

    private String name(int at, int length) {
        return text(at, length).replace('\0', ' ').strip();
    }

    private String text(int at, int length) {
        return new String(file, at, length, StandardCharsets.ISO_8859_1);
    }

    /**
     * A line where every channel is still waiting is passed over in one go.
     */
    private PscPattern pattern(int[] starts, int length) {
        final Channel[] channels =
                {new Channel(starts[0], 0), new Channel(starts[1], 1), new Channel(starts[2], 2)};
        final PscLine[] lines = new PscLine[Math.max(length, 1)];
        Arrays.fill(lines, PscLine.EMPTY);
        for (int line = 0; line < length; line++) {
            final int skipped = Arrays.stream(channels).mapToInt(channel -> channel.counter).min().orElseThrow();
            if (skipped > 0) {
                for (final Channel channel : channels) {
                    channel.counter -= skipped;
                }
                line += skipped - 1;
            } else {
                lines[line] = line(channels);
            }
        }
        return new PscPattern(lines);
    }

    private PscLine line(Channel[] channels) {
        final PscCell[] cells = new PscCell[PscFile.CHANNELS];
        int tempo = PscLine.NO_TEMPO;
        for (final Channel reading : channels) {
            if (reading.counter > 0) {
                reading.counter--;
                cells[reading.number] = PscCell.EMPTY;
                continue;
            }
            final CellBuilder cell = reading.cell();
            tempo = cell.tempo == PscLine.NO_TEMPO ? tempo : cell.tempo;
            cells[reading.number] = cell.build();
            reading.counter = reading.period;
        }
        return new PscLine(tempo, cells);
    }

    private PscSample[] samples() throws IOException {
        final PscSample[] samples = new PscSample[PscFile.MOST_SAMPLES];
        Arrays.fill(samples, PscSample.EMPTY);
        for (final int number : usedSamples) {
            final int offset = HEADER + number * 2;
            final int at = samplesBase + shortValue(offset);
            if (offset + 2 > file.length || at + SAMPLE_LINE_BYTES > file.length) {
                throw notAModule();
            }
            samples[number] = sample(at);
        }
        return samples;
    }

    /**
     * A sample runs to the line marked as its last, or to as many lines as it may have.
     */
    private PscSample sample(int at) {
        final List<PscSampleLine> lines = new ArrayList<>();
        for (int line = at; line + SAMPLE_LINE_BYTES <= file.length && lines.size() < LONGEST_OBJECT;
                line += SAMPLE_LINE_BYTES) {
            final int flags = byteAt(line + 4);
            final int volumeStep = ((flags & 2) != 0 ? 1 : 0) - ((flags & 4) != 0 ? 1 : 0);
            lines.add(new PscSampleLine(byteAt(line + 3) & 0x0f, shortValue(line), (flags & 1) != 0,
                    (flags & 8) != 0, file[line + 2], (flags & 16) == 0, volumeStep, (flags & 128) == 0,
                    (flags & 64) == 0));
            if ((flags & 32) == 0) {
                break;
            }
        }
        return new PscSample(lines.toArray(PscSampleLine[]::new));
    }

    /**
     * Some modules name more ornaments than their table holds; those play as no ornament at all.
     */
    private PscOrnament[] ornaments() throws IOException {
        final PscOrnament[] ornaments = new PscOrnament[PscFile.MOST_ORNAMENTS];
        Arrays.fill(ornaments, PscOrnament.EMPTY);
        final int table = shortValue(ORNAMENTS_TABLE_AT);
        final int held = (shortValue(SAMPLES_START_AT) - table) / 2;
        for (final int number : usedOrnaments.headSet(held)) {
            final int at = ornamentsBase + shortValue(table + number * 2);
            if (table + number * 2 + 2 > file.length || at + ORNAMENT_LINE_BYTES > file.length) {
                throw notAModule();
            }
            ornaments[number] = ornament(at);
        }
        return ornaments;
    }

    private PscOrnament ornament(int at) {
        final List<PscOrnamentLine> lines = new ArrayList<>();
        for (int line = at; line + ORNAMENT_LINE_BYTES <= file.length && lines.size() < LONGEST_OBJECT;
                line += ORNAMENT_LINE_BYTES) {
            final int flags = byteAt(line);
            lines.add(new PscOrnamentLine(file[line + 1], flags & 0x1f, (flags & 128) == 0, (flags & 64) == 0));
            if ((flags & 32) == 0) {
                break;
            }
        }
        return new PscOrnament(lines.toArray(PscOrnamentLine[]::new));
    }

    private int byteAt(int at) {
        return at < file.length ? file[at] & 0xff : 0;
    }

    private int shortValue(int at) {
        return byteAt(at) | byteAt(at + 1) << Byte.SIZE;
    }

    private static IOException notAModule() {
        return new IOException("Not a Pro Sound Creator module");
    }

    /**
     * One channel's walk through its commands, keeping the count of lines it waits between events.
     */
    private final class Channel {

        private static final int ENVELOPE_CHANNEL = 1;

        private final int number;
        private int at;
        private int period;
        private int counter;

        private Channel(int at, int number) {
            this.at = at;
            this.number = number;
        }

        /**
         * Commands are read until a wait, which ends the cell; a rest silences the channel whether a note comes
         * before or after it. The envelope and the noise base belong to the second channel alone, and elsewhere
         * their numbers are not read.
         */
        private CellBuilder cell() {
            final CellBuilder cell = new CellBuilder();
            while (at < file.length) {
                final int command = byteAt(at++);
                if (command >= 0xc0) {
                    period = command - 0xc0;
                    break;
                } else if (command >= 0xa0) {
                    cell.ornament = command - 0xa0;
                    usedOrnaments.add(cell.ornament);
                } else if (command >= 0x80) {
                    cell.sample = command - 0x80;
                    usedSamples.add(cell.sample);
                } else if (command == 0x7d) {
                    cell.add(PscCommand.Kind.BREAK_SAMPLE, 0, 0);
                } else if (command == 0x7c) {
                    cell.enabled = PscCell.OFF;
                } else if (command == 0x7b && number == ENVELOPE_CHANNEL) {
                    cell.add(PscCommand.Kind.NOISE_BASE, byteAt(at++), 0);
                } else if (command == 0x7a && number == ENVELOPE_CHANNEL) {
                    cell.add(PscCommand.Kind.ENVELOPE, byteAt(at) & 0x0f, shortValue(at + 1));
                    at += 3;
                } else if (command == 0x71) {
                    cell.add(PscCommand.Kind.BREAK_ORNAMENT, 0, 0);
                    at++;
                } else if (command == 0x70) {
                    final int slide = byteAt(at++);
                    final boolean down = (slide & 0x40) != 0;
                    cell.add(PscCommand.Kind.VOLUME_SLIDE, down ? -(byte) (slide | 0x80) : slide, down ? -1 : 1);
                } else if (command == 0x6f) {
                    cell.add(PscCommand.Kind.NO_ORNAMENT, 0, 0);
                    at++;
                } else if (command == 0x6e) {
                    cell.tempo = byteAt(at++);
                } else if (command == 0x6d) {
                    cell.add(PscCommand.Kind.GLISS, byteAt(at++), 0);
                } else if (command == 0x6c) {
                    cell.add(PscCommand.Kind.SLIDE, -(byte) byteAt(at++), 0);
                } else if (command == 0x6b) {
                    cell.add(PscCommand.Kind.SLIDE, byteAt(at++), 0);
                } else if (command >= 0x58 && command <= 0x66) {
                    cell.volume = command - 0x57;
                    cell.add(PscCommand.Kind.NO_ENVELOPE, 0, 0);
                } else if (command == 0x57) {
                    cell.volume = 0x0f;
                    cell.add(PscCommand.Kind.ENVELOPE_ON, 0, 0);
                } else if (command <= 0x56) {
                    cell.enabled = cell.enabled == PscCell.OFF ? PscCell.OFF : PscCell.ON;
                    cell.note = command;
                }
            }
            return cell;
        }
    }

    /**
     * A cell being put together while its channel's commands are read.
     */
    private static final class CellBuilder {

        private final List<PscCommand> commands = new ArrayList<>();
        private int enabled = PscCell.KEEP;
        private int note = PscCell.KEEP;
        private int sample = PscCell.KEEP;
        private int ornament = PscCell.KEEP;
        private int volume = PscCell.KEEP;
        private int tempo = PscLine.NO_TEMPO;

        private void add(PscCommand.Kind kind, int first, int second) {
            commands.add(new PscCommand(kind, first, second));
        }

        private PscCell build() {
            return new PscCell(enabled, note, sample, ornament, volume, commands);
        }
    }
}

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
import java.util.TreeSet;

/**
 * Reads a module of Pro Tracker 2, following ZXTune's reading of the format. After a header of offsets and
 * the order comes a table naming, for each pattern, where its three channels' commands begin; each channel is
 * a stream of bytes that change what the next note sounds with, ended by the note, a rest or a quit.
 */
final class Pt2Reader {

    /**
     * The player only ever reached this far into a module, so anything past it is not part of the tune.
     */
    private static final int LARGEST = 0x3800;
    private static final int SMALLEST = 100;
    private static final int SAMPLES_AT = 3;
    private static final int ORNAMENTS_AT = 67;
    private static final int PATTERNS_AT = 99;
    private static final int TITLE_AT = 101;
    private static final int TITLE_LENGTH = 30;
    private static final int POSITIONS_AT = 131;
    private static final int POSITIONS_END = 0xff;
    private static final int MOST_POSITIONS = 255;
    private static final int SLOWEST_TEMPO = 2;
    private static final int HIGHEST_OBJECT_PAGE = 0x36;
    private static final int HIGHEST_PATTERNS_PAGE = 1;
    private static final int PATTERN_BYTES = 6;
    private static final int OBJECT_HEADER = 2;
    private static final int SAMPLE_LINE_BYTES = 3;
    private static final int SAMPLE_WINDOW = 256;
    private static final int DEFAULT_SAMPLE = 1;
    private static final int DEFAULT_ORNAMENT = 0;

    private final byte[] file;
    private final TreeSet<Integer> usedSamples = new TreeSet<>(List.of(DEFAULT_SAMPLE));
    private final TreeSet<Integer> usedOrnaments = new TreeSet<>(List.of(DEFAULT_ORNAMENT));

    private Pt2Reader(byte[] file) {
        this.file = file;
    }

    static Pt2File read(byte[] bytes) throws IOException {
        final Pt2Reader reader = new Pt2Reader(Arrays.copyOf(bytes, Math.min(bytes.length, LARGEST)));
        reader.check();
        return reader.module();
    }

    /**
     * There is no mark to know the format by, so a module is known by its shape: a tempo of two or more, offsets
     * that fall inside the space the player loaded modules into, an order of patterns that exist, and the
     * pattern table right after the order's end.
     */
    private void check() throws IOException {
        if (file.length < SMALLEST || file.length <= POSITIONS_AT + 1) {
            throw notAModule();
        }
        if (byteAt(0) < SLOWEST_TEMPO || byteAt(1) == 0 || byteAt(2) == POSITIONS_END
                || byteAt(PATTERNS_AT + 1) > HIGHEST_PATTERNS_PAGE) {
            throw notAModule();
        }
        for (int at = SAMPLES_AT + 1; at < PATTERNS_AT; at += 2) {
            if (byteAt(at) > HIGHEST_OBJECT_PAGE) {
                throw notAModule();
            }
        }
        final int positionsEnd = positionsEnd();
        final int positions = positionsEnd - POSITIONS_AT;
        if (positions < 1 || positions > MOST_POSITIONS || shortValue(PATTERNS_AT) != positionsEnd + 1
                || positionsEnd + 1 >= file.length) {
            throw notAModule();
        }
    }

    private int positionsEnd() throws IOException {
        for (int at = POSITIONS_AT; at < file.length; at++) {
            final int position = byteAt(at);
            if (position == POSITIONS_END) {
                return at;
            }
            if (position >= Pt2File.MOST_PATTERNS) {
                throw notAModule();
            }
        }
        throw notAModule();
    }

    private Pt2File module() throws IOException {
        final int[] positions = new int[positionsEnd() - POSITIONS_AT];
        for (int position = 0; position < positions.length; position++) {
            positions[position] = byteAt(POSITIONS_AT + position);
        }
        final Pt2Pattern[] patterns = patterns(positions);
        return new Pt2File(byteAt(0), byteAt(2), title(), positions, samples(), ornaments(), patterns);
    }

    private String title() {
        return new String(file, TITLE_AT, TITLE_LENGTH, StandardCharsets.ISO_8859_1).replace('\0', ' ').strip();
    }

    /**
     * Only the patterns the order plays are read, and each channel must begin past the table's last entry the
     * order reaches and inside the file; at least one of them must run to the shortest length a pattern has.
     */
    private Pt2Pattern[] patterns(int[] positions) throws IOException {
        final Pt2Pattern[] patterns = new Pt2Pattern[Pt2File.MOST_PATTERNS];
        final int table = shortValue(PATTERNS_AT);
        final int firstData = table + Arrays.stream(positions).max().orElseThrow() * PATTERN_BYTES;
        boolean anyWhole = false;
        for (final int number : new TreeSet<>(Arrays.stream(positions).boxed().toList())) {
            final int at = table + number * PATTERN_BYTES;
            if (at + PATTERN_BYTES > file.length) {
                throw notAModule();
            }
            final int[] starts = {shortValue(at), shortValue(at + 2), shortValue(at + 4)};
            for (final int start : starts) {
                if (start < firstData || start >= file.length) {
                    throw notAModule();
                }
            }
            final List<Pt2Line> lines = new ArrayList<>();
            anyWhole |= readPattern(starts, lines) >= Pt2File.SHORTEST_PATTERN;
            patterns[number] = new Pt2Pattern(lines.toArray(Pt2Line[]::new));
        }
        if (!anyWhole) {
            throw notAModule();
        }
        return patterns;
    }

    /**
     * Reads a pattern's lines and answers how many were read. A line where every channel is still waiting is
     * passed over in one go, and the pattern is made up to the shortest length where it ends sooner.
     */
    private int readPattern(int[] starts, List<Pt2Line> lines) {
        final Channel[] channels = {new Channel(starts[0]), new Channel(starts[1]), new Channel(starts[2])};
        int line = 0;
        for (; line < Pt2File.LONGEST_PATTERN; line++) {
            final int skipped = Arrays.stream(channels).mapToInt(channel -> channel.counter).min().orElseThrow();
            if (skipped > 0) {
                for (final Channel channel : channels) {
                    channel.counter -= skipped;
                }
                line += skipped;
            }
            if (!hasLine(channels)) {
                padTo(lines, Math.max(line, Pt2File.SHORTEST_PATTERN));
                return line;
            }
            padTo(lines, line);
            lines.add(line(channels));
        }
        return line;
    }

    private static void padTo(List<Pt2Line> lines, int length) {
        while (lines.size() < length) {
            lines.add(new Pt2Line(Pt2Line.NO_TEMPO, new Pt2Cell[]{Pt2Cell.EMPTY, Pt2Cell.EMPTY, Pt2Cell.EMPTY}));
        }
    }

    /**
     * A channel still waiting out its count keeps the pattern going; the first channel ends it by meeting a
     * zero where its next command should be.
     */
    private boolean hasLine(Channel[] channels) {
        for (int channel = 0; channel < channels.length; channel++) {
            final Channel reading = channels[channel];
            if (reading.counter > 0) {
                continue;
            }
            if (reading.at >= file.length || (channel == 0 && file[reading.at] == 0)) {
                return false;
            }
        }
        return true;
    }

    private Pt2Line line(Channel[] channels) {
        final Pt2Cell[] cells = new Pt2Cell[Pt2File.CHANNELS];
        int tempo = Pt2Line.NO_TEMPO;
        for (int channel = 0; channel < channels.length; channel++) {
            final Channel reading = channels[channel];
            if (reading.counter > 0) {
                reading.counter--;
                cells[channel] = Pt2Cell.EMPTY;
                continue;
            }
            final CellBuilder cell = reading.cell();
            tempo = cell.tempo == Pt2Line.NO_TEMPO ? tempo : cell.tempo;
            cells[channel] = cell.build();
            reading.counter = reading.period;
        }
        return new Pt2Line(tempo, cells);
    }

    /**
     * Every sample the patterns name is read, the first always; one the module leaves unset points at the
     * start of the file and plays its first three bytes as its only line. A sample the file stops short of is
     * read as far as it goes, and at least one must have been there to read.
     */
    private Pt2Sample[] samples() throws IOException {
        final Pt2Sample[] samples = new Pt2Sample[Pt2File.MOST_SAMPLES];
        Arrays.fill(samples, Pt2Sample.EMPTY);
        boolean anyRead = false;
        for (final int number : usedSamples) {
            final int at = shortValue(SAMPLES_AT + number * 2);
            if (at == 0) {
                samples[number] = new Pt2Sample(new Pt2SampleLine[]{sampleLine(0)}, 0);
            } else if (at + OBJECT_HEADER <= file.length) {
                samples[number] = sample(at);
                anyRead = true;
            }
        }
        if (!anyRead) {
            throw notAModule();
        }
        return samples;
    }

    /**
     * A line is three bytes and only the first two hundred and fifty-six bytes of lines are ever reached, the
     * lines beyond them landing back on the first ones.
     */
    private Pt2Sample sample(int at) {
        final int size = byteAt(at);
        final int available = file.length - at;
        final int wanted = OBJECT_HEADER + Math.min(size * SAMPLE_LINE_BYTES, SAMPLE_WINDOW);
        final int read = wanted <= available ? size : (available - OBJECT_HEADER) / SAMPLE_LINE_BYTES;
        final Pt2SampleLine[] lines = new Pt2SampleLine[size];
        for (int line = 0; line < size; line++) {
            lines[line] = line < read
                    ? sampleLine(at + OBJECT_HEADER + (line * SAMPLE_LINE_BYTES & 0xff))
                    : Pt2SampleLine.SILENT;
        }
        return new Pt2Sample(lines, Math.min(byteAt(at + 1), size));
    }

    /**
     * The first byte holds the noise above the vibrato's sign and the tone and noise masks; the second the
     * level above the vibrato's high bits; the third the rest of the vibrato.
     */
    private Pt2SampleLine sampleLine(int at) {
        final int flags = byteAt(at);
        final int levelAndHigh = byteAt(at + 1);
        final int vibrato = (levelAndHigh & 0x0f) << Byte.SIZE | byteAt(at + 2);
        return new Pt2SampleLine(levelAndHigh >> 4, flags >> 3, (flags & 2) != 0, (flags & 1) != 0,
                (flags & 4) != 0 ? vibrato : -vibrato);
    }

    private Pt2Ornament[] ornaments() {
        final Pt2Ornament[] ornaments = new Pt2Ornament[Pt2File.MOST_ORNAMENTS];
        Arrays.fill(ornaments, Pt2Ornament.EMPTY);
        for (final int number : usedOrnaments) {
            final int at = shortValue(ORNAMENTS_AT + number * 2);
            if (at == 0) {
                ornaments[number] = new Pt2Ornament(new int[]{file[0]}, 0);
            } else if (at + OBJECT_HEADER <= file.length) {
                ornaments[number] = ornament(at);
            }
        }
        return ornaments;
    }

    private Pt2Ornament ornament(int at) {
        final int size = byteAt(at);
        final int read = Math.min(size, file.length - at - OBJECT_HEADER);
        final int[] offsets = new int[size];
        for (int line = 0; line < read; line++) {
            offsets[line] = file[at + OBJECT_HEADER + (line & 0xff)];
        }
        return new Pt2Ornament(offsets, Math.min(byteAt(at + 1), size));
    }

    private int byteAt(int at) {
        return at < file.length ? file[at] & 0xff : 0;
    }

    private int shortValue(int at) {
        return byteAt(at) | byteAt(at + 1) << Byte.SIZE;
    }

    private static IOException notAModule() {
        return new IOException("Not a Pro Tracker 2 module");
    }

    /**
     * One channel's walk through its commands, keeping the count of lines it waits between events.
     */
    private final class Channel {

        private int at;
        private int period;
        private int counter;

        private Channel(int at) {
            this.at = at;
        }

        private CellBuilder cell() {
            final CellBuilder cell = new CellBuilder();
            while (at < file.length) {
                final int command = file[at++] & 0xff;
                if (command == 0) {
                    continue;
                } else if (command >= 0xe1) {
                    cell.sample = command - 0xe0;
                    usedSamples.add(cell.sample);
                } else if (command == 0xe0) {
                    cell.enabled = Pt2Cell.OFF;
                    break;
                } else if (command >= 0x80) {
                    cell.sound(command - 0x80);
                    break;
                } else if (command == 0x7f) {
                    cell.commands.add(new Pt2Command(Pt2Command.Kind.NO_ENVELOPE, 0, 0));
                } else if (command >= 0x71) {
                    cell.commands.add(new Pt2Command(Pt2Command.Kind.ENVELOPE, command - 0x70, shortValue(at)));
                    at += 2;
                } else if (command == 0x70) {
                    break;
                } else if (command >= 0x60) {
                    cell.ornament = command - 0x60;
                    usedOrnaments.add(cell.ornament);
                } else if (command >= 0x20) {
                    period = command - 0x20;
                } else if (command >= 0x10) {
                    cell.volume = command - 0x10;
                } else if (command == 0x0f) {
                    cell.tempo = byteAt(at++);
                } else if (command == 0x0e) {
                    cell.commands.add(new Pt2Command(Pt2Command.Kind.GLISS, (byte) byteAt(at++), 0));
                } else if (command == 0x0d) {
                    cell.commands.add(new Pt2Command(Pt2Command.Kind.GLISS_NOTE, (byte) byteAt(at),
                            shortValue(at + 1)));
                    at += 3;
                } else if (command == 0x0c) {
                    cell.commands.add(new Pt2Command(Pt2Command.Kind.NO_GLISS, 0, 0));
                } else {
                    cell.commands.add(new Pt2Command(Pt2Command.Kind.NOISE_ADD, (byte) byteAt(at++), 0));
                }
            }
            return cell;
        }
    }

    /**
     * A cell being put together while its channel's commands are read.
     */
    private static final class CellBuilder {

        private final List<Pt2Command> commands = new ArrayList<>();
        private int enabled = Pt2Cell.KEEP;
        private int note = Pt2Cell.KEEP;
        private int sample = Pt2Cell.KEEP;
        private int ornament = Pt2Cell.KEEP;
        private int volume = Pt2Cell.KEEP;
        private int tempo = Pt2Line.NO_TEMPO;

        /**
         * A note turns the channel on; where a glissando to a note was named, the note is where it is going
         * rather than the note to sound.
         */
        private void sound(int played) {
            enabled = Pt2Cell.ON;
            for (int at = 0; at < commands.size(); at++) {
                final Pt2Command command = commands.get(at);
                if (command.kind() == Pt2Command.Kind.GLISS_NOTE) {
                    commands.set(at, new Pt2Command(command.kind(), command.first(), played));
                    return;
                }
            }
            note = played;
        }

        private Pt2Cell build() {
            return new Pt2Cell(enabled, note, sample, ornament, volume, commands);
        }
    }
}

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
import java.util.List;
import java.util.Locale;

/**
 * Reads a module of Pro Tracker 3 or Vortex Tracker II, following ZXTune's reading of the format. A pattern
 * names three places in the file where its channels' commands begin, and each channel is a stream: bytes that
 * change what the next note sounds with, then the note that ends the line, then the parameters of any effects
 * that were named, read in the reverse of the order they were named in.
 */
final class Pt3Reader {

    private static final int HEADER = 201;
    private static final int FARTHEST_PATTERN_TABLE = 0x200;
    private static final int VERSION_AT = 13;
    private static final int UNNUMBERED_VERSION = 6;
    private static final int TITLE_AT = 30;
    private static final int BY_AT = 62;
    private static final int AUTHOR_AT = 66;
    private static final int NAME_LENGTH = 32;
    private static final int BY_LENGTH = 4;
    private static final int TABLE_AT = 99;
    private static final int TEMPO_AT = 100;
    private static final int LOOP_AT = 102;
    private static final int PATTERNS_AT = 103;
    private static final int SAMPLES_AT = 105;
    private static final int ORNAMENTS_AT = 169;
    private static final int POSITIONS_END = 0xff;
    private static final int POSITION_STEP = 3;
    private static final int PATTERN_BYTES = 6;
    private static final int SAMPLE_LINE_BYTES = 4;
    private static final int SAMPLE_WINDOW = 256;
    private static final int OBJECT_HEADER = 2;

    private final byte[] file;

    private Pt3Reader(byte[] file) {
        this.file = file;
    }

    /**
     * The words at the head of the file are not to be trusted, since some modules were saved with them
     * written over, so a module is known by its shape instead: a tempo, and a pattern table lying where only
     * the header and the order come before it.
     */
    static Pt3File read(byte[] file) throws IOException {
        if (file.length <= HEADER || file[TEMPO_AT] == 0) {
            throw new IOException("Not a Pro Tracker 3 module");
        }
        final Pt3Reader reader = new Pt3Reader(file);
        final int patterns = reader.shortValue(PATTERNS_AT);
        if (patterns <= HEADER || patterns >= FARTHEST_PATTERN_TABLE || patterns >= file.length) {
            throw new IOException("Not a Pro Tracker 3 module");
        }
        return reader.module();
    }

    private Pt3File module() throws IOException {
        final int[] positions = positions();
        return new Pt3File(version(), file[TABLE_AT] & 0xff, file[TEMPO_AT] & 0xff, file[LOOP_AT] & 0xff,
                name(TITLE_AT, NAME_LENGTH), author(), positions, samples(), ornaments(), patterns(positions));
    }

    /**
     * Vortex Tracker II names itself in words where Pro Tracker writes its version digit, and plays as six.
     */
    private int version() {
        final char digit = (char) (file[VERSION_AT] & 0xff);
        return Character.isDigit(digit) ? digit - '0' : UNNUMBERED_VERSION;
    }

    private String author() {
        return name(BY_AT, BY_LENGTH).toUpperCase(Locale.ROOT).equals("BY") ? name(AUTHOR_AT, NAME_LENGTH) : "";
    }

    private String name(int at, int length) {
        return new String(file, at, length, StandardCharsets.ISO_8859_1).replace('\0', ' ').strip();
    }

    private int[] positions() throws IOException {
        final List<Integer> patterns = new ArrayList<>();
        for (int at = HEADER; ; at++) {
            if (at >= file.length) {
                throw new IOException("Pro Tracker 3 module never ends its order");
            }
            final int position = file[at] & 0xff;
            if (position == POSITIONS_END) {
                break;
            }
            if (position % POSITION_STEP != 0 || position / POSITION_STEP >= Pt3File.MOST_PATTERNS) {
                throw new IOException("Pro Tracker 3 module names a pattern that cannot exist");
            }
            patterns.add(position / POSITION_STEP);
        }
        if (patterns.isEmpty()) {
            throw new IOException("Pro Tracker 3 module plays no patterns");
        }
        return patterns.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * A sample the file points past its own end is read as far as it goes; a line is four bytes and only the
     * first sixty-four are ever reached, the lines beyond them landing back on the first.
     */
    private Pt3Sample[] samples() {
        final Pt3Sample[] samples = new Pt3Sample[Pt3File.MOST_SAMPLES];
        for (int number = 0; number < samples.length; number++) {
            final int at = shortValue(SAMPLES_AT + number * 2);
            samples[number] = at == 0 || at + OBJECT_HEADER > file.length ? Pt3Sample.EMPTY : sample(at);
        }
        return samples;
    }

    private Pt3Sample sample(int at) {
        final int size = file[at + 1] & 0xff;
        final int available = file.length - at;
        final int wanted = OBJECT_HEADER + Math.min(size * SAMPLE_LINE_BYTES, SAMPLE_WINDOW);
        final int read = wanted <= available ? size : (available - OBJECT_HEADER) / SAMPLE_LINE_BYTES;
        final Pt3SampleLine[] lines = new Pt3SampleLine[size];
        for (int line = 0; line < size; line++) {
            lines[line] = line < read
                    ? sampleLine(at + OBJECT_HEADER + (line % (SAMPLE_WINDOW / SAMPLE_LINE_BYTES)) * SAMPLE_LINE_BYTES)
                    : Pt3SampleLine.SILENT;
        }
        return new Pt3Sample(lines, Math.min(file[at] & 0xff, size));
    }

    /**
     * The first byte carries the volume slide, a five-bit signed offset and the envelope mask; the second the
     * noise mask, the two keepers, the tone mask and the level; the last two the tone offset.
     */
    private Pt3SampleLine sampleLine(int at) {
        final int slides = file[at] & 0xff;
        final int flags = file[at + 1] & 0xff;
        final int offset = (slides & 0x3e) >> 1;
        final int volumeSlide = (slides & 0x80) != 0 ? ((slides & 0x40) != 0 ? 1 : -1) : 0;
        return new Pt3SampleLine(flags & 0xf, volumeSlide, (flags & 0x10) != 0, (short) shortValue(at + 2),
                (flags & 0x40) != 0, (flags & 0x80) != 0, (slides & 1) != 0,
                (offset & 0x10) != 0 ? offset - 0x20 : offset, (flags & 0x20) != 0);
    }

    private Pt3Ornament[] ornaments() {
        final Pt3Ornament[] ornaments = new Pt3Ornament[Pt3File.MOST_ORNAMENTS];
        for (int number = 0; number < ornaments.length; number++) {
            final int at = shortValue(ORNAMENTS_AT + number * 2);
            ornaments[number] = at == 0 || at + OBJECT_HEADER > file.length ? Pt3Ornament.EMPTY : ornament(at);
        }
        return ornaments;
    }

    private Pt3Ornament ornament(int at) {
        final int size = file[at + 1] & 0xff;
        final int read = Math.min(size, file.length - at - OBJECT_HEADER);
        final int[] offsets = new int[size];
        for (int line = 0; line < read; line++) {
            offsets[line] = file[at + OBJECT_HEADER + line];
        }
        return new Pt3Ornament(offsets, Math.min(file[at] & 0xff, size));
    }

    /**
     * Only the patterns the order plays are read, since the table may point anywhere for the rest.
     */
    private Pt3Pattern[] patterns(int[] positions) throws IOException {
        final Pt3Pattern[] patterns = new Pt3Pattern[Pt3File.MOST_PATTERNS];
        final int table = shortValue(PATTERNS_AT);
        for (final int number : positions) {
            if (patterns[number] == null) {
                final int at = table + number * PATTERN_BYTES;
                if (at + PATTERN_BYTES > file.length) {
                    throw new IOException("Pro Tracker 3 module names a pattern past its own end");
                }
                patterns[number] = pattern(shortValue(at), shortValue(at + 2), shortValue(at + 4));
            }
        }
        return patterns;
    }

    private Pt3Pattern pattern(int... starts) throws IOException {
        final Channel[] channels = new Channel[Pt3File.CHANNELS];
        for (int channel = 0; channel < channels.length; channel++) {
            if (starts[channel] >= file.length) {
                throw new IOException("Pro Tracker 3 module starts a channel past its own end");
            }
            channels[channel] = new Channel(starts[channel]);
        }
        final List<Pt3Line> lines = new ArrayList<>();
        while (lines.size() < Pt3File.LONGEST_PATTERN && hasLine(channels)) {
            lines.add(line(channels));
        }
        if (lines.isEmpty()) {
            lines.add(new Pt3Line(Pt3Line.NO_TEMPO, new Pt3Cell[]{Pt3Cell.EMPTY, Pt3Cell.EMPTY, Pt3Cell.EMPTY}));
        }
        return new Pt3Pattern(lines.toArray(Pt3Line[]::new));
    }

    /**
     * A channel still waiting out its count keeps the pattern going; the first channel ends it by meeting a
     * zero where its next command should be.
     */
    private boolean hasLine(Channel[] channels) {
        for (int channel = 0; channel < channels.length; channel++) {
            final Channel reading = channels[channel];
            if (reading.waited > 0) {
                continue;
            }
            if (reading.at >= file.length || (channel == 0 && file[reading.at] == 0)) {
                return false;
            }
        }
        return true;
    }

    private Pt3Line line(Channel[] channels) {
        final Pt3Cell[] cells = new Pt3Cell[Pt3File.CHANNELS];
        int tempo = Pt3Line.NO_TEMPO;
        for (int channel = 0; channel < channels.length; channel++) {
            final Channel reading = channels[channel];
            if (reading.waited > 0) {
                reading.waited--;
                cells[channel] = Pt3Cell.EMPTY;
                continue;
            }
            final CellBuilder cell = reading.cell();
            tempo = cell.tempo == Pt3Line.NO_TEMPO ? tempo : cell.tempo;
            cells[channel] = cell.build();
            reading.waited = reading.wait;
        }
        return new Pt3Line(tempo, cells);
    }

    private int shortValue(int at) {
        return (file[at] & 0xff) | (file[at + 1] & 0xff) << Byte.SIZE;
    }

    private int byteAt(int at) {
        return at < file.length ? file[at] & 0xff : 0;
    }

    private int littleShort(int at) {
        return byteAt(at) | byteAt(at + 1) << Byte.SIZE;
    }

    /**
     * One channel's walk through its commands, keeping the count of lines it waits between notes.
     */
    private final class Channel {

        private int at;
        private int wait;
        private int waited;

        private Channel(int at) {
            this.at = at;
        }

        private CellBuilder cell() {
            final CellBuilder cell = new CellBuilder();
            final List<Integer> effects = new ArrayList<>();
            int note = Pt3Cell.KEEP;
            boolean rest = false;
            while (at < file.length) {
                final int command = file[at++] & 0xff;
                if (command < 0x10) {
                    effects.add(command);
                } else if (command < 0x20 || (command >= 0xb2 && command <= 0xbf) || command >= 0xf0) {
                    envelopeOrnamentSample(cell, command);
                } else if (command < 0x40) {
                    cell.commands.add(new Pt3Command(Pt3Command.Kind.NOISE_BASE, command - 0x20, 0, 0));
                } else if (command < 0x50) {
                    cell.ornament = command - 0x40;
                } else if (command < 0xb0) {
                    note = command - 0x50;
                    break;
                } else if (command == 0xb0) {
                    cell.commands.add(new Pt3Command(Pt3Command.Kind.NO_ENVELOPE, 0, 0, 0));
                } else if (command == 0xb1) {
                    wait = (byteAt(at++) - 1) & 0xff;
                } else if (command == 0xc0) {
                    rest = true;
                    break;
                } else if (command < 0xd0) {
                    cell.volume = command - 0xc0;
                } else if (command == 0xd0) {
                    break;
                } else {
                    cell.sample = command - 0xd0;
                }
            }
            for (int effect = effects.size() - 1; effect >= 0; effect--) {
                parameters(cell, effects.get(effect));
            }
            if (rest) {
                cell.enabled = Pt3Cell.OFF;
            } else if (note != Pt3Cell.KEEP) {
                cell.sound(note);
            }
            return cell;
        }

        /**
         * One byte names any of three things at once: an envelope and its period, an ornament, and a sample
         * given as twice its number, an odd or oversized one standing for the first.
         */
        private void envelopeOrnamentSample(CellBuilder cell, int command) {
            if (command >= 0x11 && command <= 0xbf) {
                final int type = command - (command >= 0xb2 ? 0xb1 : 0x10);
                final int period = byteAt(at) << Byte.SIZE | byteAt(at + 1);
                at += 2;
                cell.commands.add(new Pt3Command(Pt3Command.Kind.ENVELOPE, type, period, 0));
            } else {
                cell.commands.add(new Pt3Command(Pt3Command.Kind.NO_ENVELOPE, 0, 0, 0));
            }
            if (command >= 0xf0) {
                cell.ornament = command - 0xf0;
            }
            if (command < 0xb2 || command > 0xbf) {
                final int doubled = byteAt(at++);
                cell.sample = doubled < Pt3File.MOST_SAMPLES * 2 && (doubled & 1) == 0 ? doubled / 2 : 0;
            }
        }

        private void parameters(CellBuilder cell, int effect) {
            switch (effect) {
                case 1 -> {
                    final int period = byteAt(at);
                    final int step = (short) littleShort(at + 1);
                    at += 3;
                    cell.commands.add(new Pt3Command(Pt3Command.Kind.GLISS, period, step, 0));
                }
                case 2 -> {
                    final int period = byteAt(at);
                    final int step = (short) littleShort(at + 3);
                    at += 5;
                    cell.commands.add(new Pt3Command(Pt3Command.Kind.GLISS_NOTE, period, step, 0));
                }
                case 3 -> cell.commands.add(new Pt3Command(Pt3Command.Kind.SAMPLE_OFFSET, byteAt(at++), 0, 0));
                case 4 -> cell.commands.add(new Pt3Command(Pt3Command.Kind.ORNAMENT_OFFSET, byteAt(at++), 0, 0));
                case 5 -> {
                    cell.commands.add(new Pt3Command(Pt3Command.Kind.VIBRATE, byteAt(at), byteAt(at + 1), 0));
                    at += 2;
                }
                case 8 -> {
                    final int period = byteAt(at);
                    final int step = (short) littleShort(at + 1);
                    at += 3;
                    cell.commands.add(new Pt3Command(Pt3Command.Kind.ENVELOPE_SLIDE, period, step, 0));
                }
                case 9 -> cell.tempo = byteAt(at++);
                default -> {
                }
            }
        }
    }

    /**
     * A cell being put together while its channel's commands are read.
     */
    private static final class CellBuilder {

        private final List<Pt3Command> commands = new ArrayList<>();
        private int enabled = Pt3Cell.KEEP;
        private int note = Pt3Cell.KEEP;
        private int sample = Pt3Cell.KEEP;
        private int ornament = Pt3Cell.KEEP;
        private int volume = Pt3Cell.KEEP;
        private int tempo = Pt3Line.NO_TEMPO;

        /**
         * A note turns the channel on; where a glissando to a note was named, the note is where it is going
         * rather than the note to sound.
         */
        private void sound(int played) {
            enabled = Pt3Cell.ON;
            for (int at = 0; at < commands.size(); at++) {
                final Pt3Command command = commands.get(at);
                if (command.kind() == Pt3Command.Kind.GLISS_NOTE) {
                    commands.set(at, new Pt3Command(command.kind(), command.first(), command.second(), played));
                    return;
                }
            }
            note = played;
        }

        private Pt3Cell build() {
            return new Pt3Cell(enabled, note, sample, ornament, volume, commands);
        }
    }
}

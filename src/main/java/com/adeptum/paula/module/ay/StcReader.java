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

/**
 * Reads a module of ST Song Compiler. The file names nothing but offsets, so the samples run from the end of
 * the header to the positions, the ornaments from there to the patterns, and each pattern names three places
 * in the file where its channels' commands begin.
 */
final class StcReader {

    private static final int HEADER_SIZE = 27;
    private static final int TITLE_AT = 7;
    private static final int TITLE_LENGTH = 18;
    private static final int SIZE_AT = 25;
    private static final int SAMPLE_BYTES = 1 + StcFile.SAMPLE_LENGTH * 3 + 2;
    private static final int ORNAMENT_BYTES = 1 + StcFile.ORNAMENT_LENGTH;
    private static final int PATTERN_BYTES = 1 + StcFile.CHANNELS * 2;
    private static final int END_MARK = 0xff;
    private static final int SHORTEST_PATTERN = 1;

    private static final int LAST_NOTE = 0x5f;
    private static final int LAST_SAMPLE = 0x6f;
    private static final int LAST_ORNAMENT = 0x7f;
    private static final int REST = 0x80;
    private static final int EMPTY = 0x81;
    private static final int PLAIN_ORNAMENT = 0x82;
    private static final int LAST_ENVELOPE = 0x8e;
    private static final int FIRST_WAIT = 0xa1;

    private final byte[] file;

    private StcReader(byte[] file) {
        this.file = file;
    }

    static StcFile read(byte[] file) throws IOException {
        if (file.length < HEADER_SIZE) {
            throw new IOException("Not an ST Song Compiler module");
        }
        final StcReader reader = new StcReader(file);
        if (reader.shortValue(SIZE_AT) != file.length) {
            throw new IOException("ST Song Compiler module does not end where it says it does");
        }
        return reader.module();
    }

    private StcFile module() throws IOException {
        final int positionsAt = shortValue(1);
        final int ornamentsAt = shortValue(3);
        final int patternsAt = shortValue(5);
        if (positionsAt < HEADER_SIZE || ornamentsAt < positionsAt || patternsAt < ornamentsAt
                || patternsAt > file.length) {
            throw new IOException("ST Song Compiler module points outside itself");
        }
        return new StcFile(file[0] & 0xff, title(), samples(positionsAt), ornaments(ornamentsAt, patternsAt),
                positions(positionsAt), patterns(patternsAt));
    }

    private String title() {
        return new String(file, TITLE_AT, TITLE_LENGTH, StandardCharsets.ISO_8859_1).strip();
    }

    /**
     * Samples are numbered by a byte of their own rather than by where they lie, so one may be left out
     * altogether and another repeated.
     */
    private StcSample[] samples(int until) {
        final StcSample[] samples = new StcSample[StcFile.MOST_SAMPLES];
        for (int at = HEADER_SIZE; at + SAMPLE_BYTES <= until; at += SAMPLE_BYTES) {
            final int number = file[at] & 0xf;
            final StcSampleLine[] lines = new StcSampleLine[StcFile.SAMPLE_LENGTH];
            for (int line = 0; line < lines.length; line++) {
                lines[line] = sampleLine(at + 1 + line * 3);
            }
            final int loop = file[at + 1 + StcFile.SAMPLE_LENGTH * 3] & 0xff;
            final int length = file[at + 2 + StcFile.SAMPLE_LENGTH * 3] & 0xff;
            samples[number] = new StcSample(lines, Math.min(loop, StcFile.SAMPLE_LENGTH),
                    Math.min(loop + length, StcFile.SAMPLE_LENGTH));
        }
        return samples;
    }

    /**
     * The bend is written across the high nibble of the first byte and the whole of the third, and falls
     * rather than rises unless its sign bit is set.
     */
    private StcSampleLine sampleLine(int at) {
        final int levels = file[at] & 0xff;
        final int masks = file[at + 1] & 0xff;
        final int effect = (levels & 0xf0) * 16 + (file[at + 2] & 0xff);
        return new StcSampleLine(levels & 0xf, masks & 0x1f, (masks & 0x80) != 0, (masks & 0x40) != 0,
                (masks & 0x20) != 0 ? effect : -effect);
    }

    private int[][] ornaments(int from, int until) {
        final int[][] ornaments = new int[StcFile.MOST_ORNAMENTS][];
        for (int at = from; at + ORNAMENT_BYTES <= until; at += ORNAMENT_BYTES) {
            final int[] offsets = new int[StcFile.ORNAMENT_LENGTH];
            for (int line = 0; line < offsets.length; line++) {
                offsets[line] = file[at + 1 + line];
            }
            ornaments[file[at] & 0xf] = offsets;
        }
        return ornaments;
    }

    private StcPosition[] positions(int at) throws IOException {
        final int count = (file[at] & 0xff) + 1;
        if (at + 1 + count * 2 > file.length) {
            throw new IOException("ST Song Compiler module names more positions than it holds");
        }
        final StcPosition[] positions = new StcPosition[count];
        for (int step = 0; step < count; step++) {
            positions[step] = new StcPosition(Math.max(0, (file[at + 1 + step * 2] & 0xff) - 1),
                    file[at + 2 + step * 2]);
        }
        return positions;
    }

    private StcPattern[] patterns(int from) throws IOException {
        final StcPattern[] patterns = new StcPattern[StcFile.MOST_PATTERNS];
        for (int at = from; at + PATTERN_BYTES <= file.length && (file[at] & 0xff) != END_MARK;
                at += PATTERN_BYTES) {
            final int number = (file[at] & 0xff) - 1;
            if (number < 0 || number >= StcFile.MOST_PATTERNS) {
                throw new IOException("ST Song Compiler module names a pattern that cannot exist");
            }
            patterns[number] = pattern(shortValue(at + 1), shortValue(at + 3), shortValue(at + 5));
        }
        return patterns;
    }

    /**
     * The three channels are read side by side: each carries its own wait between notes, so a line exists for
     * as long as the first channel has commands left.
     */
    private StcPattern pattern(int... starts) {
        final Channel[] channels = {new Channel(starts[0]), new Channel(starts[1]), new Channel(starts[2])};
        final List<StcCell[]> lines = new ArrayList<>();
        while (lines.size() < StcFile.LONGEST_PATTERN && channels[0].hasMore()) {
            final StcCell[] line = new StcCell[StcFile.CHANNELS];
            for (int channel = 0; channel < StcFile.CHANNELS; channel++) {
                line[channel] = channels[channel].next();
            }
            lines.add(line);
        }
        while (lines.size() < SHORTEST_PATTERN) {
            lines.add(new StcCell[]{StcCell.EMPTY, StcCell.EMPTY, StcCell.EMPTY});
        }
        return new StcPattern(lines.toArray(StcCell[][]::new));
    }

    private int shortValue(int at) {
        return (file[at] & 0xff) | (file[at + 1] & 0xff) << Byte.SIZE;
    }

    /**
     * One channel's walk through its commands, holding the wait it was last told to keep between notes.
     */
    private final class Channel {

        private int at;
        private int wait;
        private int waited;

        private Channel(int at) {
            this.at = at;
        }

        /**
         * A channel still waiting out its count keeps the pattern going, whatever stands at its cursor: the
         * end mark only ends the pattern once the channel has come back round to reading it.
         */
        private boolean hasMore() {
            return waited > 0 || (at < file.length && (file[at] & 0xff) != END_MARK);
        }

        private StcCell next() {
            if (waited > 0) {
                waited--;
                return StcCell.EMPTY;
            }
            return command();
        }

        private StcCell command() {
            int note = StcCell.NONE;
            int sample = StcCell.NONE;
            int ornament = StcCell.NONE;
            int envelope = StcCell.NONE;
            int period = 0;
            while (at < file.length) {
                final int command = file[at++] & 0xff;
                if (command <= LAST_NOTE) {
                    note = command;
                    break;
                } else if (command <= LAST_SAMPLE) {
                    sample = command - 0x60;
                } else if (command <= LAST_ORNAMENT) {
                    ornament = command - 0x70;
                    envelope = StcCell.NO_ENVELOPE;
                } else if (command == REST) {
                    note = StcCell.REST;
                    break;
                } else if (command == EMPTY) {
                    break;
                } else if (command == PLAIN_ORNAMENT) {
                    ornament = 0;
                    envelope = StcCell.NO_ENVELOPE;
                } else if (command <= LAST_ENVELOPE) {
                    ornament = 0;
                    envelope = command - REST;
                    period = at < file.length ? file[at++] & 0xff : 0;
                } else {
                    wait = (command + 0x100 - FIRST_WAIT) & 0xff;
                }
            }
            waited = wait;
            return new StcCell(note, sample, ornament, envelope, period);
        }
    }
}

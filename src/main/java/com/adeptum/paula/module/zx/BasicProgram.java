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

package com.adeptum.paula.module.zx;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * What a tape's BASIC loader says about the machine code it starts: the RAMTOP of a CLEAR, every LOAD of code or
 * a screen in the order they run, with the address where one is given, and the address a USR jumps to. Nothing
 * is run; the tokenised lines are read for those words, skipping strings, remarks and the hidden five-byte form
 * the editor stores after every number, and numbers that are digits or VAL of a string are the ones understood.
 */
record BasicProgram(OptionalInt clear, OptionalInt entry, List<OptionalInt> loads) {

    private static final int CLEAR = 0xFD;
    private static final int LOAD = 0xEF;
    private static final int CODE = 0xAF;
    private static final int SCREEN = 0xAA;
    private static final int USR = 0xC0;
    private static final int VAL = 0xB0;
    private static final int REM = 0xEA;
    private static final int HIDDEN_NUMBER = 0x0E;
    private static final int HIDDEN_NUMBER_BYTES = 5;
    private static final int QUOTE = '"';
    private static final int SPACE = ' ';
    private static final int LINE_HEADER = 4;
    private static final int LENGTH_AT = 2;
    private static final int LARGEST_ADDRESS = 0xFFFF;

    static BasicProgram parse(byte[] program) {
        final Scan scan = new Scan(program);
        scan.run();
        return new BasicProgram(scan.clear, scan.entry, List.copyOf(scan.loads));
    }

    private static final class Scan {

        private final byte[] program;
        private final List<OptionalInt> loads = new ArrayList<>();
        private OptionalInt clear = OptionalInt.empty();
        private OptionalInt entry = OptionalInt.empty();
        private int at;
        private int end;

        Scan(byte[] program) {
            this.program = program;
        }

        void run() {
            int line = 0;
            while (line + LINE_HEADER <= program.length) {
                end = Math.min(program.length, line + LINE_HEADER + word(line + LENGTH_AT));
                at = line + LINE_HEADER;
                statements();
                line = end;
            }
        }

        private void statements() {
            while (at < end) {
                switch (program[at++] & 0xFF) {
                    case QUOTE -> skipString();
                    case HIDDEN_NUMBER -> at += HIDDEN_NUMBER_BYTES;
                    case REM -> at = end;
                    case CLEAR -> clear = number();
                    case USR -> entry = number();
                    case LOAD -> load();
                    default -> {
                    }
                }
            }
        }

        private void load() {
            skipSpaces();
            if (at < end && program[at] == QUOTE) {
                at++;
                skipString();
            }
            skipSpaces();
            if (at < end && (program[at] & 0xFF) == CODE) {
                at++;
                loads.add(number());
            } else if (at < end && (program[at] & 0xFF) == SCREEN) {
                at++;
                loads.add(OptionalInt.empty());
            }
        }

        private OptionalInt number() {
            skipSpaces();
            if (at < end && (program[at] & 0xFF) == VAL) {
                at++;
                skipSpaces();
                return at < end && program[at] == QUOTE ? quotedNumber() : OptionalInt.empty();
            }
            return digits();
        }

        private OptionalInt quotedNumber() {
            final int start = ++at;
            skipString();
            try {
                return inRange(Double.parseDouble(text(start, at - 1).strip()));
            } catch (NumberFormatException e) {
                return OptionalInt.empty();
            }
        }

        private OptionalInt digits() {
            final int start = at;
            while (at < end && program[at] >= '0' && program[at] <= '9') {
                at++;
            }
            return at == start ? OptionalInt.empty() : inRange(Double.parseDouble(text(start, at)));
        }

        private void skipString() {
            while (at < end && program[at] != QUOTE) {
                at++;
            }
            at++;
        }

        private void skipSpaces() {
            while (at < end && program[at] == SPACE) {
                at++;
            }
        }

        private String text(int from, int to) {
            return new String(program, from, Math.max(0, Math.min(to, end) - from), StandardCharsets.ISO_8859_1);
        }

        private int word(int offset) {
            return (program[offset] & 0xFF) | (program[offset + 1] & 0xFF) << 8;
        }

        private static OptionalInt inRange(double value) {
            return value >= 0 && value <= LARGEST_ADDRESS ? OptionalInt.of((int) value) : OptionalInt.empty();
        }
    }
}

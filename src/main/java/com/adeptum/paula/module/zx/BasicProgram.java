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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * What a tape's BASIC loader says about the machine code it starts: the RAMTOP of a CLEAR, every LOAD of code or
 * a screen in the order they run, with the address where one is given, and the address a USR jumps to. Nothing
 * is run; the tokenised lines are read for those words, skipping strings and remarks. A number after one of them
 * is taken from the hidden five-byte form the editor stores behind its text, or from the text or a VAL of a
 * string where there is none; a later word whose number cannot be read leaves the earlier value standing.
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
    private static final int SMALL_INTEGER = 0;
    private static final int SMALL_INTEGER_RANGE = 0x10000;
    private static final double MANTISSA_SCALE = 0x1p31;
    private static final int EXPONENT_BIAS = 129;
    private static final String NUMBER_CHARACTERS = "0123456789.Ee";
    private static final int LOWER_CASE = 0x20;
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
                    case CLEAR -> clear = latest(number(), clear);
                    case USR -> entry = latest(number(), entry);
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
            return writtenNumber();
        }

        private OptionalInt quotedNumber() {
            final int start = ++at;
            skipString();
            return parsed(text(start, at - 1));
        }

        private OptionalInt writtenNumber() {
            final int start = at;
            while (at < end && (NUMBER_CHARACTERS.indexOf(program[at]) >= 0 || isExponentSign(start))) {
                at++;
            }
            if (at + HIDDEN_NUMBER_BYTES < end && (program[at] & 0xFF) == HIDDEN_NUMBER) {
                final double value = hiddenValue(at + 1);
                at += HIDDEN_NUMBER_BYTES + 1;
                return inRange(value);
            }
            return parsed(text(start, at));
        }

        private boolean isExponentSign(int start) {
            return (program[at] == '+' || program[at] == '-') && at > start && (program[at - 1] | LOWER_CASE) == 'e';
        }

        /**
         * The value BASIC itself uses, which the text in front of it only shows: a small integer, or a floating
         * point number with its exponent in the first byte and the sign in place of the mantissa's leading one.
         */
        private double hiddenValue(int from) {
            final int exponent = program[from] & 0xFF;
            if (exponent == SMALL_INTEGER) {
                return word(from + 2) - (program[from + 1] == 0 ? 0 : SMALL_INTEGER_RANGE);
            }
            final int mantissa = ByteBuffer.wrap(program, from + 1, Integer.BYTES).getInt();
            final double fraction = 1 + (mantissa & Integer.MAX_VALUE) / MANTISSA_SCALE;
            return Math.copySign(Math.scalb(fraction, exponent - EXPONENT_BIAS), mantissa);
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

        private static OptionalInt parsed(String text) {
            try {
                return inRange(Double.parseDouble(text.strip()));
            } catch (NumberFormatException e) {
                return OptionalInt.empty();
            }
        }

        private static OptionalInt latest(OptionalInt read, OptionalInt before) {
            return read.isPresent() ? read : before;
        }

        private static OptionalInt inRange(double value) {
            return value >= 0 && value <= LARGEST_ADDRESS ? OptionalInt.of((int) value) : OptionalInt.empty();
        }
    }
}

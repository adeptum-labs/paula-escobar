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

package com.adeptum.paula.testing;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds tapes as a Spectrum saves them, and small Z80 programs that drive the speaker and the AY.
 */
public final class TestTaps {

    public static final int ENTRY = 0x8000;

    /**
     * DI; then the speaker goes up and down with a 128-step DJNZ between edges: edges at T-states 19, 1700, 3396 …
     */
    public static final byte[] SQUARE_WAVE = bytes(0xF3, 0x3E, 0x10, 0xD3, 0xFE, 0x06, 0x80, 0x10, 0xFE, 0xAF,
            0xD3, 0xFE, 0x06, 0x80, 0x10, 0xFE, 0x18, 0xEF);

    /**
     * One pulse (edges at 19 and 868), then RET: the song ends at T-state 881.
     */
    public static final byte[] ONE_PULSE_THEN_RETURN = bytes(0xF3, 0x3E, 0x10, 0xD3, 0xFE, 0x06, 0x40, 0x10, 0xFE,
            0xAF, 0xD3, 0xFE, 0xC9);

    /**
     * The square wave for 512 periods of 3391 T-states each, about half a second, then RET.
     */
    public static final byte[] HALF_SECOND_THEN_RETURN = bytes(0xF3, 0x11, 0x00, 0x02, 0x3E, 0x10, 0xD3, 0xFE, 0x06,
            0x80, 0x10, 0xFE, 0xAF, 0xD3, 0xFE, 0x06, 0x80, 0x10, 0xFE, 0x1B, 0x7A, 0xB3, 0x20, 0xEC, 0xC9);

    public static final byte[] PULSE_THEN_HALT = bytes(0xF3, 0x3E, 0x10, 0xD3, 0xFE, 0xAF, 0xD3, 0xFE, 0x76);

    public static final byte[] PULSE_THEN_JUMP_TO_ROM = bytes(0xF3, 0x3E, 0x10, 0xD3, 0xFE, 0xAF, 0xD3, 0xFE,
            0xC3, 0xAF, 0x0D);

    public static final byte[] SILENT_JUMP_TO_ROM = bytes(0xF3, 0xC3, 0xAF, 0x0D);

    public static final byte[] WAIT_FOREVER = bytes(0xF3, 0x18, 0xFE);

    /**
     * Selects AY register 7 and writes 0x3E to it, then halts.
     */
    public static final byte[] AY_WRITE = bytes(0xF3, 0x01, 0xFD, 0xFF, 0x3E, 0x07, 0xED, 0x79, 0x01, 0xFD, 0xBF,
            0x3E, 0x3E, 0xED, 0x79, 0x76);

    /**
     * Channel A at full volume with its tone open at period 0x40 (about 1732 Hz), then waits for ever.
     */
    public static final byte[] AY_TONE = bytes(0xF3,
            0x01, 0xFD, 0xFF, 0x3E, 0x08, 0xED, 0x79, 0x01, 0xFD, 0xBF, 0x3E, 0x0F, 0xED, 0x79,
            0x01, 0xFD, 0xFF, 0x3E, 0x07, 0xED, 0x79, 0x01, 0xFD, 0xBF, 0x3E, 0x3E, 0xED, 0x79,
            0x01, 0xFD, 0xFF, 0x3E, 0x00, 0xED, 0x79, 0x01, 0xFD, 0xBF, 0x3E, 0x40, 0xED, 0x79,
            0x18, 0xFE);

    /**
     * Pages RAM bank 1 in at 0xC000, then halts.
     */
    public static final byte[] PAGE_BANK_ONE = bytes(0xF3, 0x01, 0xFD, 0x7F, 0x3E, 0x01, 0xED, 0x79, 0x76);

    private static final Map<String, Integer> TOKENS = Map.of("CLEAR", 0xFD, "LOAD", 0xEF, "CODE", 0xAF,
            "USR", 0xC0, "VAL", 0xB0, "RANDOMIZE", 0xF9, "REM", 0xEA, "SCREEN", 0xAA);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)(?::(\\d+))?}");
    private static final int HIDDEN_NUMBER = 0x0E;

    private TestTaps() {
    }

    public static byte[] bytes(int... values) {
        final byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    public static byte[] block(int flag, byte[] payload) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final int length = payload.length + 2;
        out.write(length & 0xFF);
        out.write(length >> 8);
        out.write(flag);
        int checksum = flag;
        for (final byte b : payload) {
            out.write(b);
            checksum ^= b & 0xFF;
        }
        out.write(checksum);
        return out.toByteArray();
    }

    private static byte[] header(int type, String name, int length, int param1, int param2) {
        final byte[] fields = new byte[17];
        fields[0] = (byte) type;
        final byte[] padded = String.format("%-10.10s", name).getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(padded, 0, fields, 1, 10);
        word(fields, 11, length);
        word(fields, 13, param1);
        word(fields, 15, param2);
        return block(0x00, fields);
    }

    private static void word(byte[] target, int at, int value) {
        target[at] = (byte) value;
        target[at + 1] = (byte) (value >> 8);
    }

    public static byte[] programPart(String name, byte[] basic) {
        return concat(header(0, name, basic.length, 1, basic.length), block(0xFF, basic));
    }

    public static byte[] codePart(String name, int address, byte[] code) {
        return concat(header(3, name, code.length, address, 0x8000), block(0xFF, code));
    }

    public static byte[] tape(byte[]... parts) {
        return concat(parts);
    }

    /**
     * A BASIC loader that loads one machine-code block and starts it, followed by that block.
     */
    public static byte[] loaderTape(byte[] code) {
        return tape(programPart("loader", line(10, tokenised("{CLEAR} {NUM:32767}: {LOAD}\"\" {CODE}: {RANDOMIZE} {USR} {NUM:" + ENTRY + "}"))),
                codePart("music", ENTRY, code));
    }

    /**
     * One BASIC line: the number big-endian, the length little-endian, the content and the end-of-line mark.
     */
    public static byte[] line(int number, byte[] content) {
        final byte[] line = new byte[content.length + 5];
        line[0] = (byte) (number >> 8);
        line[1] = (byte) number;
        word(line, 2, content.length + 1);
        System.arraycopy(content, 0, line, 4, content.length);
        line[line.length - 1] = 0x0D;
        return line;
    }

    /**
     * Source text with {NAME} for a keyword token and {NUM:n} for a number as the editor stores it: the digits,
     * then the hidden five-byte form.
     */
    public static byte[] tokenised(String source) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Matcher matcher = PLACEHOLDER.matcher(source);
        int from = 0;
        while (matcher.find()) {
            out.writeBytes(source.substring(from, matcher.start()).getBytes(StandardCharsets.ISO_8859_1));
            if (matcher.group(2) == null) {
                out.write(TOKENS.get(matcher.group(1)));
            } else {
                final int value = Integer.parseInt(matcher.group(2));
                out.writeBytes(matcher.group(2).getBytes(StandardCharsets.ISO_8859_1));
                out.writeBytes(bytes(HIDDEN_NUMBER, 0, 0, value & 0xFF, value >> 8, 0));
            }
            from = matcher.end();
        }
        out.writeBytes(source.substring(from).getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    public static byte[] concat(byte[]... arrays) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] array : arrays) {
            out.writeBytes(array);
        }
        return out.toByteArray();
    }
}

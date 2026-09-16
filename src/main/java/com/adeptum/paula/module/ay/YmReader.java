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
import java.util.Set;

/**
 * Reads a YM recording of an Atari ST's sound chip. The older marks carry nothing but the registers and are
 * played at the machine's own clock; from YM5 on a header names the clock, the interrupt rate and the tune.
 * A register may be laid out frame by frame or a register at a time across the whole tune.
 *
 * <p>The effects the later marks pack into the spare bits of the tone, noise and timer registers — digidrums
 * and the sync buzzer among them — are left alone: the chip masks each register to the width it really has,
 * so what is written there falls away of its own accord.
 */
final class YmReader {

    private static final Set<String> BARE_MARKS = Set.of("YM2!", "YM3!", "YM3b");
    private static final Set<String> HEADED_MARKS = Set.of("YM4!", "YM5!", "YM6!");
    private static final String CHECK_STRING = "LeOnArD!";
    private static final int MARK_LENGTH = 4;
    private static final int YM_REGISTERS = 16;
    private static final int LOOP_TRAILER = 4;
    private static final int INTERLEAVED = 1;

    private final byte[] file;
    private int at;

    private YmReader(byte[] file) {
        this.file = file;
    }

    static RegisterFrames read(byte[] file) throws IOException {
        if (file.length < MARK_LENGTH + CHECK_STRING.length()) {
            throw new IOException("Not a YM recording");
        }
        final YmReader reader = new YmReader(file);
        final String mark = reader.text(MARK_LENGTH);
        if (BARE_MARKS.contains(mark)) {
            return reader.bare(mark);
        }
        if (!HEADED_MARKS.contains(mark) || !CHECK_STRING.equals(reader.text(CHECK_STRING.length()))) {
            throw new IOException("Not a YM recording");
        }
        return reader.headed();
    }

    /**
     * Nothing but fourteen registers a frame, one register at a time across the tune. A YM3b names the frame
     * it loops back to after them, which playing straight through has no use for.
     */
    private RegisterFrames bare(String mark) {
        final int trailer = "YM3b".equals(mark) ? LOOP_TRAILER : 0;
        final int count = (file.length - at - trailer) / RegisterFrames.REGISTERS;
        return laidOut(count, RegisterFrames.REGISTERS, true, RegisterFrames.ATARI_CLOCK,
                RegisterFrames.INTERRUPTS_A_SECOND);
    }

    private RegisterFrames headed() throws IOException {
        final int count = (int) integer();
        final long attributes = integer();
        final int digidrums = shortValue();
        final int clockRate = (int) integer();
        final int framesPerSecond = shortValue();
        integer();
        final int extra = shortValue();
        at += extra;
        skipDigidrums(digidrums);
        final String title = name();
        final String author = name();
        name();
        if (count < 0 || (long) count * YM_REGISTERS > file.length - at) {
            throw new IOException("YM recording names more frames than it holds");
        }
        final RegisterFrames frames =
                laidOut(count, YM_REGISTERS, (attributes & INTERLEAVED) != 0, clockRate, framesPerSecond);
        return new RegisterFrames(frames.values(), frames.count(), frames.clockRate(),
                frames.framesPerSecond(), title, author);
    }

    private RegisterFrames laidOut(int count, int stride, boolean interleaved, int clockRate,
            int framesPerSecond) {
        final byte[] values = new byte[Math.max(0, count) * RegisterFrames.REGISTERS];
        for (int frame = 0; frame < count; frame++) {
            for (int register = 0; register < RegisterFrames.REGISTERS; register++) {
                values[frame * RegisterFrames.REGISTERS + register] =
                        file[at + (interleaved ? register * count + frame : frame * stride + register)];
            }
        }
        return new RegisterFrames(values, Math.max(0, count), clockRate, framesPerSecond);
    }

    private void skipDigidrums(int count) throws IOException {
        for (int drum = 0; drum < count; drum++) {
            final long size = integer();
            if (size < 0 || size > file.length - at) {
                throw new IOException("YM recording names a sample larger than itself");
            }
            at += (int) size;
        }
    }

    private String text(int length) {
        final String text = new String(file, at, length, StandardCharsets.US_ASCII);
        at += length;
        return text;
    }

    private String name() throws IOException {
        final int from = at;
        while (at < file.length && file[at++] != 0) {
            continue;
        }
        if (at >= file.length) {
            throw new IOException("YM recording ends in the middle of its titles");
        }
        return new String(file, from, at - from - 1, StandardCharsets.ISO_8859_1);
    }

    private long integer() throws IOException {
        return (long) shortValue() << Short.SIZE | shortValue();
    }

    private int shortValue() throws IOException {
        if (at + Short.BYTES > file.length) {
            throw new IOException("YM recording ends in the middle of its header");
        }
        return (file[at++] & 0xff) << Byte.SIZE | (file[at++] & 0xff);
    }
}

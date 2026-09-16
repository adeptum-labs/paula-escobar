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

import com.adeptum.paula.archive.LhaExtractor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * Reads a VTX recording, as Vortex Tracker and the Spectrum utilities wrote them. The mark names the chip
 * outright rather than leaving it to be guessed from the clock, and the registers are packed by the lh5
 * method with no archive around them, kept one register at a time across the whole tune.
 */
final class VtxReader {

    private static final Map<String, AyChip.Voicing> MARKS = Map.of(
            "ay", AyChip.Voicing.AY,
            "ym", AyChip.Voicing.YM,
            "AY", AyChip.Voicing.AY,
            "YM", AyChip.Voicing.YM);

    private static final int MARK_LENGTH = 2;
    private static final int SHORTEST = 16;
    private static final int LAYOUTS = 7;
    private static final int SLOWEST = 25;
    private static final int FASTEST = 100;

    private final byte[] file;
    private int at;

    private VtxReader(byte[] file) {
        this.file = file;
    }

    static boolean marksARecording(byte[] file) {
        return file.length >= SHORTEST
                && MARKS.containsKey(new String(file, 0, MARK_LENGTH, StandardCharsets.US_ASCII));
    }

    static RegisterFrames read(byte[] file) throws IOException {
        if (file.length < SHORTEST) {
            throw new IOException("Not a VTX recording");
        }
        final String mark = new String(file, 0, MARK_LENGTH, StandardCharsets.US_ASCII);
        final AyChip.Voicing voicing = MARKS.get(mark);
        if (voicing == null) {
            throw new IOException("Not a VTX recording");
        }
        return new VtxReader(file).registers(mark, voicing);
    }

    /**
     * The newer kind is marked in small letters and names the year and three further texts; the older kind,
     * in capitals, stops after the author.
     */
    private RegisterFrames registers(String mark, AyChip.Voicing voicing) throws IOException {
        final boolean newer = mark.equals(mark.toLowerCase(java.util.Locale.ROOT));
        at = MARK_LENGTH;
        final int layout = byteValue();
        shortValue();
        final int clockRate = (int) integer();
        final int framesPerSecond = byteValue();
        if (layout >= LAYOUTS || framesPerSecond < SLOWEST || framesPerSecond > FASTEST) {
            throw new IOException("Not a VTX recording: it describes no machine that ever played one");
        }
        if (newer) {
            shortValue();
        }
        final long packedSize = integer();
        final String title = name();
        final String author = name();
        if (newer) {
            name();
            name();
            name();
        }
        return laidOut(unpacked(packedSize), clockRate, framesPerSecond, title, author, voicing);
    }

    private byte[] unpacked(long size) throws IOException {
        if (size <= 0 || size % RegisterFrames.REGISTERS != 0) {
            throw new IOException("VTX recording names an impossible number of registers");
        }
        return LhaExtractor.unpackedLh5(Arrays.copyOfRange(file, at, file.length), (int) size);
    }

    private static RegisterFrames laidOut(byte[] columns, int clockRate, int framesPerSecond, String title,
            String author, AyChip.Voicing voicing) {
        final int count = columns.length / RegisterFrames.REGISTERS;
        final byte[] values = new byte[columns.length];
        for (int frame = 0; frame < count; frame++) {
            for (int register = 0; register < RegisterFrames.REGISTERS; register++) {
                values[frame * RegisterFrames.REGISTERS + register] = columns[register * count + frame];
            }
        }
        return new RegisterFrames(values, count, clockRate, framesPerSecond, title, author, voicing);
    }

    private String name() throws IOException {
        final int from = at;
        while (at < file.length && file[at++] != 0) {
            continue;
        }
        if (at >= file.length) {
            throw new IOException("VTX recording ends in the middle of its titles");
        }
        return new String(file, from, at - from - 1, StandardCharsets.ISO_8859_1);
    }

    private long integer() throws IOException {
        return shortValue() | (long) shortValue() << Short.SIZE;
    }

    private int shortValue() throws IOException {
        return byteValue() | byteValue() << Byte.SIZE;
    }

    private int byteValue() throws IOException {
        if (at >= file.length) {
            throw new IOException("VTX recording ends in the middle of its header");
        }
        return file[at++] & 0xff;
    }
}

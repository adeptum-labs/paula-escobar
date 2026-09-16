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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class YmReaderTest {

    private static final int ATARI_CLOCK = 2000000;
    private static final int FRAMES = 4;
    private static final int YM_REGISTERS = 16;
    private static final int SHAPE = 13;
    private static final int INTERLEAVED = 1;

    @Test
    void readsAnInterleavedRecording() throws IOException {
        final RegisterFrames frames = YmReader.read(ym5(INTERLEAVED));

        assertEquals(FRAMES, frames.count());
        assertEquals(ATARI_CLOCK, frames.clockRate());
        assertEquals(50, frames.framesPerSecond());
        assertEquals(0x20, frames.register(2, 0));
        assertEquals(0x0c, frames.register(2, 8));
    }

    @Test
    void readsARecordingLaidOutFrameByFrame() throws IOException {
        final RegisterFrames frames = YmReader.read(ym5(0));

        assertEquals(FRAMES, frames.count());
        assertEquals(0x20, frames.register(2, 0));
        assertEquals(0x0c, frames.register(2, 8));
    }

    /**
     * The YM formats already say with a value of their own that a frame left the envelope alone, which is
     * where the convention comes from.
     */
    @Test
    void keepsTheMarkForAFrameThatLeftTheEnvelopeAlone() throws IOException {
        final RegisterFrames frames = YmReader.read(ym5(INTERLEAVED));

        assertEquals(0x08, frames.register(0, SHAPE));
        assertEquals(RegisterFrames.SHAPE_UNTOUCHED, frames.register(1, SHAPE));
    }

    /**
     * A YM3 is nothing but the mark and fourteen registers a frame, so the Atari's own clock and interrupt
     * are what it must be played at.
     */
    @Test
    void readsAYm3AtTheAtariClock() throws IOException {
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes("YM3!".getBytes(StandardCharsets.US_ASCII));
        for (int register = 0; register < RegisterFrames.REGISTERS; register++) {
            for (int frame = 0; frame < FRAMES; frame++) {
                file.write(register == 0 ? frame * 0x10 : 0);
            }
        }

        final RegisterFrames frames = YmReader.read(file.toByteArray());

        assertEquals(FRAMES, frames.count());
        assertEquals(ATARI_CLOCK, frames.clockRate());
        assertEquals(50, frames.framesPerSecond());
        assertEquals(0x20, frames.register(2, 0));
    }

    @Test
    void refusesWhatIsNotAYm() {
        assertThrows(IOException.class, () -> YmReader.read("NOPE and then some".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void refusesARecordingThatPromisesMoreFramesThanItHolds() {
        final byte[] file = ym5(INTERLEAVED);
        file[15] = (byte) 0xff;

        assertThrows(IOException.class, () -> YmReader.read(file));
    }

    /**
     * Four frames in which the first register climbs and the eighth holds a volume, with the envelope started
     * once in the first frame and left alone after it.
     */
    private static byte[] ym5(int attributes) {
        final int[][] registers = new int[FRAMES][YM_REGISTERS];
        for (int frame = 0; frame < FRAMES; frame++) {
            registers[frame][0] = frame * 0x10;
            registers[frame][8] = 0x0c;
            registers[frame][SHAPE] = frame == 0 ? 0x08 : RegisterFrames.SHAPE_UNTOUCHED;
        }
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes("YM5!LeOnArD!".getBytes(StandardCharsets.US_ASCII));
        writeInt(file, FRAMES);
        writeInt(file, attributes);
        writeShort(file, 0);
        writeInt(file, ATARI_CLOCK);
        writeShort(file, 50);
        writeInt(file, 0);
        writeShort(file, 0);
        file.writeBytes("Beastbusters\0".getBytes(StandardCharsets.US_ASCII));
        file.writeBytes("4-Mat\0".getBytes(StandardCharsets.US_ASCII));
        file.writeBytes("aldn\0".getBytes(StandardCharsets.US_ASCII));
        if (attributes == INTERLEAVED) {
            for (int register = 0; register < YM_REGISTERS; register++) {
                for (int frame = 0; frame < FRAMES; frame++) {
                    file.write(registers[frame][register]);
                }
            }
        } else {
            for (final int[] frame : registers) {
                for (final int value : frame) {
                    file.write(value);
                }
            }
        }
        file.writeBytes("End!".getBytes(StandardCharsets.US_ASCII));
        return file.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream file, int value) {
        file.write(value >> 24);
        file.write(value >> 16);
        file.write(value >> 8);
        file.write(value);
    }

    private static void writeShort(ByteArrayOutputStream file, int value) {
        file.write(value >> 8);
        file.write(value);
    }
}

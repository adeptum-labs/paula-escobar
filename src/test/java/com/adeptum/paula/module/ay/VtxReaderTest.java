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

import com.adeptum.paula.testing.TestArchives;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VtxReaderTest {

    private static final int FRAMES = 8;
    private static final int SHAPE = 13;
    private static final int YEAR = 1997;

    @Test
    void readsTheNewerKindWithAllItsNames() throws IOException {
        final RegisterFrames frames = VtxReader.read(vtx("ay"));

        assertEquals(FRAMES, frames.count());
        assertEquals(RegisterFrames.SPECTRUM_CLOCK, frames.clockRate());
        assertEquals(50, frames.framesPerSecond());
        assertEquals("Paula VTX", frames.title());
        assertEquals("Adeptum", frames.author());
    }

    /**
     * The registers are kept one at a time across the whole tune rather than frame by frame.
     */
    @Test
    void readsTheRegistersOutOfTheirColumns() throws IOException {
        final RegisterFrames frames = VtxReader.read(vtx("ay"));

        assertEquals(0x20, frames.register(2, 0));
        assertEquals(0x50, frames.register(5, 0));
        assertEquals(0x08, frames.register(0, SHAPE));
        assertEquals(RegisterFrames.SHAPE_UNTOUCHED, frames.register(1, SHAPE));
    }

    /**
     * The mark says which of the two chips was playing, which the clock alone cannot: a Yamaha part turns up
     * in Spectrum clones at the Spectrum's own clock.
     */
    @Test
    void takesTheChipFromTheMarkRatherThanTheClock() throws IOException {
        assertEquals(AyChip.Voicing.AY, VtxReader.read(vtx("ay")).voicing());
        assertEquals(AyChip.Voicing.YM, VtxReader.read(vtx("ym")).voicing());
    }

    /**
     * The older kind was written in capitals and carries neither the year nor the three further names.
     */
    @Test
    void readsTheOlderKindThatNamesLess() throws IOException {
        final RegisterFrames frames = VtxReader.read(vtx("AY"));

        assertEquals(FRAMES, frames.count());
        assertEquals("Paula VTX", frames.title());
        assertEquals(0x20, frames.register(2, 0));
    }

    @Test
    void refusesWhatIsNotAVtx() {
        assertThrows(IOException.class,
                () -> VtxReader.read("not a recording at all".getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] vtx(String mark) throws IOException {
        final boolean newer = mark.equals(mark.toLowerCase(java.util.Locale.ROOT));
        final byte[] registers = new byte[RegisterFrames.REGISTERS * FRAMES];
        for (int frame = 0; frame < FRAMES; frame++) {
            registers[frame] = (byte) (frame * 0x10);
            registers[SHAPE * FRAMES + frame] = (byte) (frame == 0 ? 0x08 : RegisterFrames.SHAPE_UNTOUCHED);
        }
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(mark.getBytes(StandardCharsets.US_ASCII));
        file.write(0);
        writeShort(file, 0);
        writeInt(file, RegisterFrames.SPECTRUM_CLOCK);
        file.write(50);
        if (newer) {
            writeShort(file, YEAR);
        }
        writeInt(file, registers.length);
        file.writeBytes("Paula VTX\0".getBytes(StandardCharsets.US_ASCII));
        file.writeBytes("Adeptum\0".getBytes(StandardCharsets.US_ASCII));
        if (newer) {
            file.writeBytes("Vortex\0".getBytes(StandardCharsets.US_ASCII));
            file.writeBytes("Paula\0".getBytes(StandardCharsets.US_ASCII));
            file.writeBytes("a test\0".getBytes(StandardCharsets.US_ASCII));
        }
        file.writeBytes(TestArchives.lh5Stream(registers));
        return file.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream file, int value) {
        file.write(value);
        file.write(value >> 8);
    }

    private static void writeInt(ByteArrayOutputStream file, int value) {
        writeShort(file, value);
        writeShort(file, value >> 16);
    }
}

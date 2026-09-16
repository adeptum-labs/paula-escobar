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
import org.junit.jupiter.api.Test;

class PsgReaderTest {

    private static final int FRAME = 0xff;
    private static final int SKIP = 0xfe;
    private static final int END = 0xfd;
    private static final int PLAIN_VERSION = 0x00;

    @Test
    void keepsARegisterUntilItIsWrittenAgain() throws IOException {
        final RegisterFrames frames = PsgReader.read(psg(PLAIN_VERSION,
                FRAME, 0x00, 0xfd, 0x07, 0x3e,
                FRAME, 0x00, 0x21,
                END));

        assertEquals(2, frames.count());
        assertEquals(0xfd, frames.register(0, 0));
        assertEquals(0x3e, frames.register(0, 7));
        assertEquals(0x21, frames.register(1, 0));
        assertEquals(0x3e, frames.register(1, 7));
    }

    /**
     * A skip stands for four frames at a time, which is how a long silence is written down.
     */
    @Test
    void countsFourFramesForEveryStepOfASkip() throws IOException {
        final RegisterFrames frames = PsgReader.read(psg(PLAIN_VERSION,
                FRAME, 0x08, 0x0f,
                SKIP, 0x02, 0x08, 0x00,
                END));

        assertEquals(9, frames.count());
        assertEquals(0x0f, frames.register(0, 8));
        assertEquals(0x0f, frames.register(7, 8));
        assertEquals(0x00, frames.register(8, 8));
    }

    /**
     * Some emulators wrote the header four bytes long and left the rest out, which shows as a version byte
     * where the first frame mark should be.
     */
    @Test
    void readsTheShortHeaderSomeEmulatorsWrote() throws IOException {
        final byte[] file = {'P', 'S', 'G', 0x1a, (byte) FRAME, 0x01, 0x0c, 0x00, 0x00,
                0x02, 0x00, 0x03, 0x00, 0x04, 0x00, 0x05, 0x00, (byte) END};

        final RegisterFrames frames = PsgReader.read(file);

        assertEquals(1, frames.count());
        assertEquals(0x0c, frames.register(0, 1));
    }

    /**
     * The shape register is the one that must not be carried over: writing it is what starts the envelope
     * again, so a frame that left it alone has to say so.
     */
    @Test
    void saysWhenAFrameLeftTheEnvelopeShapeAlone() throws IOException {
        final RegisterFrames frames = PsgReader.read(psg(PLAIN_VERSION,
                FRAME, 0x0d, 0x08,
                FRAME, 0x00, 0x11,
                END));

        assertEquals(0x08, frames.register(0, 13));
        assertEquals(RegisterFrames.SHAPE_UNTOUCHED, frames.register(1, 13));
    }

    @Test
    void leavesTheEnvelopeAloneInAFrameThatNeverWroteIt() throws IOException {
        final RegisterFrames frames = PsgReader.read(psg(PLAIN_VERSION, FRAME, 0x00, 0x11, END));

        assertEquals(RegisterFrames.SHAPE_UNTOUCHED, frames.register(0, 13));
    }

    @Test
    void stopsAtTheEndMarkAndIgnoresWhatFollows() throws IOException {
        final RegisterFrames frames = PsgReader.read(psg(PLAIN_VERSION,
                FRAME, 0x02, 0x11,
                END, FRAME, 0x02, 0x22, FRAME));

        assertEquals(1, frames.count());
        assertEquals(0x11, frames.register(0, 2));
    }

    @Test
    void refusesWhatIsNotAPsg() {
        final byte[] file = psg(PLAIN_VERSION, FRAME, 0x02, 0x11, END);
        file[3] = 0x00;

        assertThrows(IOException.class, () -> PsgReader.read(file));
    }

    @Test
    void refusesAFileWithNoRoomForAStream() {
        assertThrows(IOException.class, () -> PsgReader.read(psg(PLAIN_VERSION)));
    }

    private static byte[] psg(int version, int... stream) {
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(new byte[]{'P', 'S', 'G', 0x1a, (byte) version, 0x00});
        file.writeBytes(new byte[10]);
        for (final int value : stream) {
            file.write(value);
        }
        return file.toByteArray();
    }
}

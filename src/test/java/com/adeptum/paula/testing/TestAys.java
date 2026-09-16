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

/**
 * Builds the smallest recordings of the AY's registers that still sound: one second of a steady tone on the
 * first channel, written once as a PSG stream of changes and once as a YM block of whole frames.
 */
public final class TestAys {

    public static final String TITLE = "Paula AY";
    public static final String AUTHOR = "Adeptum";
    public static final int FRAMES = 50;
    public static final int TONE_PERIOD = 0xfd;
    public static final int VOLUME = 14;

    private static final int TONE_A_ONLY = 0x3e;
    private static final int SHAPE_UNTOUCHED = 0xff;
    private static final int YM_REGISTERS = 16;
    private static final int ATARI_CLOCK = 2000000;
    private static final int FRAME_MARK = 0xff;
    private static final int END_MARK = 0xfd;

    private TestAys() {
    }

    /**
     * A PSG says only what changed, so the tone is set up in the first frame and the rest are bare marks.
     */
    public static byte[] psg() {
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(new byte[]{'P', 'S', 'G', 0x1a, 0, 0});
        file.writeBytes(new byte[10]);
        for (int frame = 0; frame < FRAMES; frame++) {
            file.write(FRAME_MARK);
            if (frame == 0) {
                file.write(0);
                file.write(TONE_PERIOD);
                file.write(7);
                file.write(TONE_A_ONLY);
                file.write(8);
                file.write(VOLUME);
            }
        }
        file.write(END_MARK);
        return file.toByteArray();
    }

    /**
     * A YM5 holding the same tune, one register at a time across the whole song, as the Atari tools wrote it.
     */
    public static byte[] ym() {
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes("YM5!LeOnArD!".getBytes(StandardCharsets.US_ASCII));
        writeInt(file, FRAMES);
        writeInt(file, 1);
        writeShort(file, 0);
        writeInt(file, ATARI_CLOCK);
        writeShort(file, 50);
        writeInt(file, 0);
        writeShort(file, 0);
        file.writeBytes((TITLE + '\0').getBytes(StandardCharsets.US_ASCII));
        file.writeBytes((AUTHOR + '\0').getBytes(StandardCharsets.US_ASCII));
        file.writeBytes("\0".getBytes(StandardCharsets.US_ASCII));
        for (int register = 0; register < YM_REGISTERS; register++) {
            for (int frame = 0; frame < FRAMES; frame++) {
                file.write(valueOf(register));
            }
        }
        file.writeBytes("End!".getBytes(StandardCharsets.US_ASCII));
        return file.toByteArray();
    }

    private static int valueOf(int register) {
        return switch (register) {
            case 0 -> TONE_PERIOD;
            case 7 -> TONE_A_ONLY;
            case 8 -> VOLUME;
            case 13 -> SHAPE_UNTOUCHED;
            default -> 0;
        };
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

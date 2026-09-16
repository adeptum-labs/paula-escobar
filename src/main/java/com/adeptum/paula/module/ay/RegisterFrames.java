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

/**
 * What the fourteen sound registers held at each of a tune's interrupts. A recording keeps every frame rather
 * than the writes that made it, since a frame carries only what changed and a player needs the whole picture
 * to start anywhere.
 */
public record RegisterFrames(byte[] values, int count) {

    public static final int REGISTERS = 14;

    /**
     * Writing the envelope shape is what starts the envelope over, so a frame that left the register alone
     * has to say so rather than repeat what stands there. The chip has no such value, which is why the
     * recording formats settled on it.
     */
    public static final int SHAPE_UNTOUCHED = 0xff;

    public int register(int frame, int register) {
        return values[frame * REGISTERS + register] & 0xff;
    }

    /**
     * Lays the frame out where a player wants it, as the fourteen values it must write to the chip.
     */
    public void frame(int at, int[] registers) {
        for (int register = 0; register < REGISTERS; register++) {
            registers[register] = register(at, register);
        }
    }
}

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
 * The fourteen sound registers of an AY, and how they drive a chip. A tune's frames fill the array a frame at a
 * time; a machine writing to the chip changes one register at a time. Either way the chip is brought up to date
 * from the whole picture, and the envelope shape, whose write restarts the envelope, is applied once.
 */
public final class AyRegisters {

    public static final double[] SPREAD = {0.15, 0.5, 0.85};

    /**
     * Each channel reaches full scale on its own, so three of them at once would carry the mix well past what
     * a sample holds. The room left is what the loudest side of the spread can add up to, which keeps the
     * three together inside the scale rather than clipping the sum flat.
     */
    public static final double HEADROOM = 1 / weightOfTheLoudestSide();

    private static final int TONE_REGISTERS = 2;
    private static final int MIXER_REGISTER = 7;
    private static final int FIRST_VOLUME_REGISTER = 8;
    private static final int ENVELOPE_PERIOD_REGISTER = 11;
    private static final int ENVELOPE_SHAPE_REGISTER = 13;
    private static final int ENVELOPE_FLAG = 0x10;
    private static final int VOLUME_MASK = 0x0f;
    private static final int NOISE_SHIFT = 3;

    private final int[] registers = new int[RegisterFrames.REGISTERS];

    public AyRegisters() {
        registers[ENVELOPE_SHAPE_REGISTER] = RegisterFrames.SHAPE_UNTOUCHED;
    }

    public static AyChip chip(AyChip.Voicing voicing, double clockRate, int sampleRate) {
        final AyChip chip = new AyChip(voicing, clockRate, sampleRate);
        for (int channel = 0; channel < AyChip.CHANNELS; channel++) {
            chip.pan(channel, SPREAD[channel]);
        }
        return chip;
    }

    public int[] values() {
        return registers;
    }

    public void write(int register, int value) {
        registers[register] = value;
    }

    /**
     * A channel is heard when it has a volume or runs the envelope, and its mixer lets the tone or the noise
     * through.
     */
    public boolean audible() {
        for (int channel = 0; channel < AyChip.CHANNELS; channel++) {
            final boolean loud = (registers[FIRST_VOLUME_REGISTER + channel] & (VOLUME_MASK | ENVELOPE_FLAG)) != 0;
            if (loud && (!bit(MIXER_REGISTER, channel) || !bit(MIXER_REGISTER, channel + NOISE_SHIFT))) {
                return true;
            }
        }
        return false;
    }

    public void applyTo(AyChip chip) {
        for (int channel = 0; channel < AyChip.CHANNELS; channel++) {
            final int fine = registers[channel * TONE_REGISTERS];
            final int coarse = registers[channel * TONE_REGISTERS + 1];
            chip.tone(channel, fine | (coarse << Byte.SIZE));
            final int level = registers[FIRST_VOLUME_REGISTER + channel];
            chip.mixer(channel, bit(MIXER_REGISTER, channel), bit(MIXER_REGISTER, channel + NOISE_SHIFT),
                    (level & ENVELOPE_FLAG) != 0);
            chip.volume(channel, level & VOLUME_MASK);
        }
        chip.noise(registers[AyChip.CHANNELS * TONE_REGISTERS]);
        chip.envelope(registers[ENVELOPE_PERIOD_REGISTER]
                | (registers[ENVELOPE_PERIOD_REGISTER + 1] << Byte.SIZE));
        if (registers[ENVELOPE_SHAPE_REGISTER] != RegisterFrames.SHAPE_UNTOUCHED) {
            chip.envelopeShape(registers[ENVELOPE_SHAPE_REGISTER]);
            registers[ENVELOPE_SHAPE_REGISTER] = RegisterFrames.SHAPE_UNTOUCHED;
        }
    }

    /**
     * The mixer register turns a generator off where its bit is set, rather than on.
     */
    private boolean bit(int register, int at) {
        return (registers[register] >> at & 1) != 0;
    }

    private static double weightOfTheLoudestSide() {
        double left = 0;
        double right = 0;
        for (final double pan : SPREAD) {
            left += 1 - pan;
            right += pan;
        }
        return Math.max(left, right);
    }
}

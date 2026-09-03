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

package com.adeptum.paula.module.wav;

import java.nio.ByteOrder;
import lombok.RequiredArgsConstructor;

/**
 * How one sample sits in a file: how wide it is, which end its bytes start at, and what the number it holds
 * means. Whatever that is, it comes out as one of the sixteen-bit samples the engine mixes.
 */
public record WavEncoding(int bits, ByteOrder order, WavEncoding.Kind kind) {

    private static final int MU_LAW_BIAS = 0x84;
    private static final int MU_LAW_MANTISSA = 0x0F;
    private static final int MU_LAW_MANTISSA_SHIFT = 3;
    private static final int MU_LAW_EXPONENT = 0x07;
    private static final int MU_LAW_EXPONENT_SHIFT = 4;
    private static final int MU_LAW_SIGN = 0x80;
    private static final int BYTE_MASK = 0xFF;

    @RequiredArgsConstructor
    public enum Kind {
        SIGNED("PCM"),
        UNSIGNED("PCM"),
        FLOAT("float"),
        MU_LAW("µ-law");

        private final String description;
    }

    public int bytes() {
        return bits / Byte.SIZE;
    }

    /**
     * Anything whose samples are whole bytes no wider than the word a sample is read into, and only the widths
     * the companded and floating-point encodings are ever written at.
     */
    public boolean isPlayable() {
        return switch (kind) {
            case SIGNED, UNSIGNED -> bits >= Byte.SIZE && bits <= Integer.SIZE && bits % Byte.SIZE == 0;
            case FLOAT -> bits == Float.SIZE;
            case MU_LAW -> bits == Byte.SIZE;
        };
    }

    public short sampleAt(byte[] file, int at) {
        final int word = word(file, at);
        return switch (kind) {
            case SIGNED -> scaled(word);
            case UNSIGNED -> scaled(word ^ (1 << (bits - 1)));
            case FLOAT -> fromFloat(Float.intBitsToFloat(word));
            case MU_LAW -> fromMuLaw(word);
        };
    }

    public String describe() {
        return bits + "-bit " + kind.description;
    }

    private int word(byte[] file, int at) {
        final boolean big = order == ByteOrder.BIG_ENDIAN;
        int word = 0;
        for (int index = 0; index < bytes(); index++) {
            word = (word << Byte.SIZE) | (file[at + (big ? index : bytes() - 1 - index)] & BYTE_MASK);
        }
        return word;
    }

    /**
     * The word read as a two's-complement number of its own width, brought to the sixteen bits the engine
     * mixes at by throwing away the bits below them or padding with zeroes where there are too few.
     */
    private short scaled(int word) {
        final int signed = (word << (Integer.SIZE - bits)) >> (Integer.SIZE - bits);
        return (short) (bits > Short.SIZE ? signed >> (bits - Short.SIZE) : signed << (Short.SIZE - bits));
    }

    private static short fromFloat(float sample) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample * Short.MAX_VALUE)));
    }

    /**
     * The companding of ITU-T G.711, where the byte holds a sign, a three-bit exponent and a four-bit mantissa,
     * all of them inverted.
     */
    private static short fromMuLaw(int encoded) {
        final int plain = ~encoded & BYTE_MASK;
        final int magnitude = (((plain & MU_LAW_MANTISSA) << MU_LAW_MANTISSA_SHIFT) + MU_LAW_BIAS)
                << ((plain >> MU_LAW_EXPONENT_SHIFT) & MU_LAW_EXPONENT);
        return (short) ((plain & MU_LAW_SIGN) != 0 ? MU_LAW_BIAS - magnitude : magnitude - MU_LAW_BIAS);
    }
}

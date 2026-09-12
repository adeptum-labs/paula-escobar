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
 *
 * The decompression follows Load_mo3.cpp of OpenMPT, Copyright © 2004-2026
 * the OpenMPT project developers and Copyright © 1997-2003 Olivier
 * Lapicque, licensed under the three-clause BSD licence and used here under
 * the GNU General Public License. Those routines came in turn from Laurent
 * Clévy's unmo3 and were relicensed with his permission.
 */

package com.adeptum.paula.module.mo3;

import java.io.IOException;

/**
 * Unpacks a waveform an MO3 kept losslessly, which it does one of two ways.
 *
 * <p>Both write each sample as the distance from the last, coded in as few bits as the last few needed: the
 * width grows and shrinks with the deltas themselves, so a quiet passage costs two bits a sample and a loud
 * one costs what it must. The second way predicts where the waveform is heading from the slope it was on and
 * codes the distance from there instead, which is closer still on anything smooth.</p>
 *
 * <p>The channels of a stereo sample follow one another rather than alternating.</p>
 */
final class Mo3Delta {

    private static final int NARROW_SHIFT = 7;
    private static final int WIDE_SHIFT = 15;
    private static final int NARROW_WIDTH = 4;
    private static final int WIDE_WIDTH = 8;

    /**
     * Below this width a sixteen-bit delta is read two bits at a time rather than one.
     */
    private static final int PAIRED_BELOW = 5;

    private static final int SMALLEST_CODED = 4;

    private Mo3Delta() {
    }

    /**
     * Unpacks {@code waveform.length} channels of {@code waveform[0].length} samples each, stopping where the
     * stream runs out rather than refusing the module for one waveform that was cut short.
     */
    static void unpack(Mo3Bits bits, int[][] waveform, boolean wide, boolean predicted) {
        final int shift = wide ? WIDE_SHIFT : NARROW_SHIFT;
        final int mask = (1 << shift + 1) - 1;
        final int quietest = -(mask + 1) / 2;
        final int loudest = mask / 2;
        int width = wide ? WIDE_WIDTH : NARROW_WIDTH;
        int previous = 0;
        int predictedNext = 0;

        try {
            for (final int[] channel : waveform) {
                for (int at = 0; at < channel.length; at++) {
                    int value = coded(bits, width, wide);
                    width = nextWidth(width, value, shift);

                    final boolean rising = (value & 1) != 0;
                    value >>= 1;
                    if (!rising) {
                        value = ~value & mask;
                    }

                    if (!predicted) {
                        previous = signed(value + previous & mask, shift);
                        channel[at] = previous;
                        continue;
                    }
                    final int delta = signed(value, shift);
                    final int sample = signed(value + predictedNext & mask, shift);
                    channel[at] = sample;
                    predictedNext = Math.clamp((long) sample * 2 + (delta >> 1) - previous, quietest, loudest);
                    previous = sample;
                }
            }
        } catch (IOException cutShort) {
            // A waveform that ends early is played as far as it reached and silent after it.
        }
    }

    /**
     * The delta as it stands in the stream: a run of bits saying how wide it is, then the width the samples
     * before it settled on, then a sign in the lowest bit.
     */
    private static int coded(Mo3Bits bits, int width, boolean wide) throws IOException {
        int value = 0;
        do {
            value = value << 1 | bits.digit();
            if (wide && width < PAIRED_BELOW) {
                value = value << 1 | bits.digit();
            }
        } while (bits.bit());

        for (int left = width; left > 0; left--) {
            value = value << 1 | bits.digit();
        }
        return value;
    }

    /**
     * How wide the next delta is taken to be: halfway between the width just used and the highest bit this
     * delta needed, so the coding follows the waveform up and down.
     */
    private static int nextWidth(int width, int value, int shift) {
        final int used = value < SMALLEST_CODED ? 1
                : Math.clamp(Integer.SIZE - 1 - Integer.numberOfLeadingZeros(value), 1, shift);
        return width + used >> 1;
    }

    private static int signed(int value, int shift) {
        return value << Integer.SIZE - shift - 1 >> Integer.SIZE - shift - 1;
    }
}

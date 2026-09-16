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

import org.junit.jupiter.api.Test;

/**
 * Checks the chip against the numbers ayumi.c produces. The spot values and the hashes were taken from a
 * build of ayumi.c itself, compiled without floating-point contraction so that it multiplies and adds the
 * way Java does, so a difference here is a difference in the sound.
 */
class AyChipTest {

    private static final double SPECTRUM_CLOCK = 1773400;
    private static final int RATE = 44100;
    private static final int FRAMES = 4096;

    private static final long AY_HASH = 0xfae41c9674666b71L;
    private static final long YM_HASH = 0xbedcd4f17680c5f0L;
    private static final double[] AY_AT_63 = {0.75117798599155305, 0.08048069662711009};
    private static final double[] AY_AT_1000 = {0.42204883424952805, 0.070160957159032228};
    private static final double[] YM_AT_63 = {0.70679378502227985, 0.075549118741635293};
    private static final double[] YM_AT_1000 = {0.38762113079307536, 0.054482281487032769};

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 1099511628211L;

    @Test
    void soundsAnAyExactlyAsAyumiDoes() {
        assertSoundsAs(AyChip.Voicing.AY, AY_AT_63, AY_AT_1000, AY_HASH);
    }

    @Test
    void soundsAYmExactlyAsAyumiDoes() {
        assertSoundsAs(AyChip.Voicing.YM, YM_AT_63, YM_AT_1000, YM_HASH);
    }

    /**
     * The three channels are set apart so that every generator has a hand in the sound: a plain tone, a tone
     * and noise together, and noise shaped by the envelope.
     */
    private static void assertSoundsAs(AyChip.Voicing voicing, double[] at63, double[] at1000, long hash) {
        final AyChip chip = new AyChip(voicing, SPECTRUM_CLOCK, RATE);
        chip.pan(0, 0.1);
        chip.pan(1, 0.5);
        chip.pan(2, 0.9);
        chip.tone(0, 0x0fd);
        chip.tone(1, 0x1a4);
        chip.tone(2, 0x07b);
        chip.noise(5);
        chip.envelope(0x0140);
        chip.envelopeShape(10);
        chip.mixer(0, false, true, false);
        chip.mixer(1, false, false, false);
        chip.mixer(2, true, false, true);
        chip.volume(0, 14);
        chip.volume(1, 7);

        long fnv = FNV_OFFSET;
        for (int frame = 0; frame < FRAMES; frame++) {
            chip.next();
            fnv = folded(folded(fnv, chip.left()), chip.right());
            if (frame == 63) {
                assertEquals(at63[0], chip.left());
                assertEquals(at63[1], chip.right());
            }
            if (frame == 1000) {
                assertEquals(at1000[0], chip.left());
                assertEquals(at1000[1], chip.right());
            }
        }
        assertEquals(hash, fnv);
    }

    private static long folded(long fnv, double sample) {
        long hash = fnv;
        final long bits = Double.doubleToRawLongBits(sample);
        for (int byteAt = 0; byteAt < Long.BYTES; byteAt++) {
            hash = (hash ^ (bits >>> (byteAt * Byte.SIZE)) & 0xff) * FNV_PRIME;
        }
        return hash;
    }
}

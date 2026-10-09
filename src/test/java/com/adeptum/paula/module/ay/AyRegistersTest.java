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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AyRegistersTest {

    private final AyRegisters registers = new AyRegisters();

    @Test
    void startsSilent() {
        assertFalse(registers.audible());
    }

    @Test
    void aChannelIsAudibleWithVolumeAndAnOpenMixer() {
        registers.write(8, 12);
        registers.write(7, 0b111_110);

        assertTrue(registers.audible());
    }

    @Test
    void aClosedMixerSilencesTheChannel() {
        registers.write(8, 12);
        registers.write(7, 0b111_111);

        assertFalse(registers.audible());
    }

    @Test
    void theEnvelopeBitCountsAsVolume() {
        registers.write(9, 0x10);
        registers.write(7, 0b111_101);

        assertTrue(registers.audible());
    }

    @Test
    void writesReachTheChipAndTheEnvelopeShapeIsAppliedOnce() {
        final AyChip chip = AyRegisters.chip(AyChip.Voicing.AY, RegisterFrames.SPECTRUM_CLOCK, 48000);
        registers.write(0, 0x34);
        registers.write(1, 0x01);
        registers.write(8, 15);
        registers.write(7, 0b111_110);
        registers.write(13, 8);
        registers.applyTo(chip);

        assertEquals(RegisterFrames.SHAPE_UNTOUCHED, registers.values()[13], "the shape is not re-applied by the next write");
        double peak = 0;
        for (int i = 0; i < 2000; i++) {
            chip.next();
            peak = Math.max(peak, Math.abs(chip.left()));
        }
        assertTrue(peak > 0.01, "the chip should sound, peak was " + peak);
    }
}

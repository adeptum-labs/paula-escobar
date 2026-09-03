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

package com.adeptum.paula.ui.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpectrumTest {

    private static final int BANDS = 32;
    private static final int SAMPLE_RATE = 48000;
    private static final int FRAMES = 2048;

    private final Spectrum spectrum = new Spectrum(BANDS, SAMPLE_RATE);

    @Test
    void aSineRaisesTheBandThatContainsIt() {
        spectrum.feed(Signals.stereoSine(1000, SAMPLE_RATE, FRAMES, 1.0, 1.0));

        final double[] levels = spectrum.levels();
        assertEquals(BANDS, levels.length);
        final int loudest = Signals.argMax(levels);
        assertEquals(loudest, spectrum.bandOf(1000), "the 1 kHz band is the loudest");
        assertTrue(levels[loudest] > 0.6, "a full scale sine fills most of its bar, was " + levels[loudest]);
        assertTrue(levels[spectrum.bandOf(8000)] < 0.2, "distant bands stay low");
    }

    @Test
    void silenceIsQuietAndBandsCoverTheAudibleRange() {
        spectrum.feed(new short[FRAMES * 2]);
        for (final double level : spectrum.levels()) {
            assertEquals(0.0, level);
        }
        assertEquals(0, spectrum.bandOf(40));
        assertEquals(BANDS - 1, spectrum.bandOf(20000));
        assertTrue(spectrum.bandOf(1000) > spectrum.bandOf(100));
    }

    @Test
    void peaksHoldAboveFallingLevelsThenDropAway() {
        spectrum.feed(Signals.stereoSine(1000, SAMPLE_RATE, FRAMES, 1.0, 1.0));
        final int band = spectrum.bandOf(1000);
        final double loud = spectrum.levels()[band];

        for (int i = 0; i < 8; i++) {
            spectrum.feed(new short[FRAMES * 2]);
        }
        assertTrue(spectrum.levels()[band] < loud, "levels fall once the sound stops");
        assertTrue(spectrum.peaks()[band] > spectrum.levels()[band], "the peak marker lags behind");

        for (int i = 0; i < 400; i++) {
            spectrum.feed(new short[FRAMES * 2]);
        }
        assertTrue(spectrum.peaks()[band] < 0.05, "eventually the peak drops too");
    }
}

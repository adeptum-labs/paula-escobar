/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

class FftTest {

    private static final int SIZE = 2048;
    private static final int SAMPLE_RATE = 48000;

    private final Fft fft = new Fft(SIZE);

    @Test
    void aSineShowsUpInItsBin() {
        final double[] magnitudes = fft.magnitudes(Signals.sine(1000, SAMPLE_RATE, SIZE, 1.0));
        final int expectedBin = 1000 * SIZE / SAMPLE_RATE;

        assertEquals(SIZE / 2, magnitudes.length);
        final int loudest = Signals.argMax(magnitudes);
        assertTrue(Math.abs(loudest - expectedBin) <= 1, "loudest bin was " + loudest + ", expected about " + expectedBin);
        assertTrue(magnitudes[loudest] > 0.4, "a full scale sine should read near full scale, was " + magnitudes[loudest]);
        assertTrue(magnitudes[expectedBin * 3] < 0.01, "far bins stay quiet");
    }

    @Test
    void silenceIsZero() {
        for (final double magnitude : fft.magnitudes(new double[SIZE])) {
            assertEquals(0.0, magnitude);
        }
    }
}

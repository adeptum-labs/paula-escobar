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

/**
 * Test signals: sines as doubles for the FFT and as interleaved 16-bit stereo for the state machines.
 */
final class Signals {

    private Signals() {
    }

    static double[] sine(double hertz, int sampleRate, int samples, double amplitude) {
        final double[] out = new double[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = amplitude * Math.sin(2 * Math.PI * hertz * i / sampleRate);
        }
        return out;
    }

    static short[] stereoSine(double hertz, int sampleRate, int frames, double left, double right) {
        final short[] out = new short[frames * 2];
        for (int i = 0; i < frames; i++) {
            final double value = Math.sin(2 * Math.PI * hertz * i / sampleRate) * Short.MAX_VALUE;
            out[i * 2] = (short) (value * left);
            out[i * 2 + 1] = (short) (value * right);
        }
        return out;
    }

    static int argMax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }
}

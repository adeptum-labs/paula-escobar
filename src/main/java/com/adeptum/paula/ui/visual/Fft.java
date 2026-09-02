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

/**
 * A radix-2 fast Fourier transform over a Hann-windowed real signal. Magnitudes are scaled so a full-scale sine
 * reads about one in its bin.
 */
public final class Fft {

    private final int size;
    private final double[] window;
    private final double[] cos;
    private final double[] sin;
    private final double[] real;
    private final double[] imaginary;

    public Fft(int size) {
        if (Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("FFT size must be a power of two, not " + size);
        }
        this.size = size;
        this.window = new double[size];
        this.cos = new double[size / 2];
        this.sin = new double[size / 2];
        this.real = new double[size];
        this.imaginary = new double[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / size);
        }
        for (int i = 0; i < size / 2; i++) {
            cos[i] = Math.cos(2 * Math.PI * i / size);
            sin[i] = Math.sin(2 * Math.PI * i / size);
        }
    }

    public int size() {
        return size;
    }

    public double[] magnitudes(double[] samples) {
        for (int i = 0; i < size; i++) {
            real[i] = samples[i] * window[i];
            imaginary[i] = 0;
        }
        transform();
        final double[] magnitudes = new double[size / 2];
        final double scale = 4.0 / size;
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = Math.hypot(real[i], imaginary[i]) * scale;
        }
        return magnitudes;
    }

    private void transform() {
        for (int i = 1, j = 0; i < size; i++) {
            int bit = size >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                swap(i, j);
            }
        }
        for (int length = 2; length <= size; length <<= 1) {
            final int step = size / length;
            for (int start = 0; start < size; start += length) {
                for (int k = 0; k < length / 2; k++) {
                    final double wr = cos[k * step];
                    final double wi = -sin[k * step];
                    final int even = start + k;
                    final int odd = even + length / 2;
                    final double tr = real[odd] * wr - imaginary[odd] * wi;
                    final double ti = real[odd] * wi + imaginary[odd] * wr;
                    real[odd] = real[even] - tr;
                    imaginary[odd] = imaginary[even] - ti;
                    real[even] += tr;
                    imaginary[even] += ti;
                }
            }
        }
    }

    private void swap(int i, int j) {
        final double r = real[i];
        real[i] = real[j];
        real[j] = r;
        final double im = imaginary[i];
        imaginary[i] = imaginary[j];
        imaginary[j] = im;
    }
}

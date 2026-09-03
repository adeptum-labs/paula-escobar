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

import java.util.Arrays;

/**
 * A spectrum analyser: the newest audio is transformed, grouped into logarithmically spaced bands and shown on a
 * decibel scale. Levels fall gently when the sound stops and a peak marker lingers above them.
 */
public final class Spectrum {

    private static final int FFT_SIZE = 2048;
    private static final double LOWEST_HERTZ = 50;
    private static final double HIGHEST_HERTZ = 16000;
    private static final double RANGE_DB = 60;
    private static final double LEVEL_FALL = 0.06;
    private static final double PEAK_FALL = 0.01;
    private static final double FULL_SCALE = Short.MAX_VALUE;

    private final int bands;
    private final int sampleRate;
    private final Fft fft = new Fft(FFT_SIZE);
    private final double[] mono = new double[FFT_SIZE];
    private final double[] levels;
    private final double[] peaks;
    private final int[] firstBin;
    private final int[] lastBin;

    public Spectrum(int bands, int sampleRate) {
        this.bands = bands;
        this.sampleRate = sampleRate;
        this.levels = new double[bands];
        this.peaks = new double[bands];
        this.firstBin = new int[bands];
        this.lastBin = new int[bands];
        final double hertzPerBin = (double) sampleRate / FFT_SIZE;
        for (int band = 0; band < bands; band++) {
            final int low = (int) Math.round(edge(band) / hertzPerBin);
            final int high = (int) Math.round(edge(band + 1) / hertzPerBin) - 1;
            firstBin[band] = Math.clamp(low, 1, FFT_SIZE / 2 - 1);
            lastBin[band] = Math.clamp(Math.max(high, low), firstBin[band], FFT_SIZE / 2 - 1);
        }
    }

    public int bands() {
        return bands;
    }

    /**
     * The band a frequency falls into, clamped to the first and last bands.
     */
    public int bandOf(double hertz) {
        final double position = Math.log(hertz / LOWEST_HERTZ) / Math.log(HIGHEST_HERTZ / LOWEST_HERTZ);
        return Math.clamp((int) Math.floor(position * bands), 0, bands - 1);
    }

    public void feed(short[] interleavedStereo) {
        final int frames = interleavedStereo.length / 2;
        final int used = Math.min(frames, FFT_SIZE);
        Arrays.fill(mono, 0);
        for (int i = 0; i < used; i++) {
            final int frame = frames - used + i;
            mono[FFT_SIZE - used + i] = (interleavedStereo[frame * 2] + interleavedStereo[frame * 2 + 1]) / (2 * FULL_SCALE);
        }
        final double[] magnitudes = fft.magnitudes(mono);
        for (int band = 0; band < bands; band++) {
            double loudest = 0;
            for (int bin = firstBin[band]; bin <= lastBin[band]; bin++) {
                loudest = Math.max(loudest, magnitudes[bin]);
            }
            final double measured = loudest <= 0 ? 0 : Math.clamp(1 + 20 * Math.log10(loudest) / RANGE_DB, 0, 1);
            levels[band] = Math.max(measured, levels[band] - LEVEL_FALL);
            peaks[band] = Math.max(levels[band], peaks[band] - PEAK_FALL);
        }
    }

    public double[] levels() {
        return levels.clone();
    }

    public double[] peaks() {
        return peaks.clone();
    }

    private double edge(int band) {
        return LOWEST_HERTZ * Math.pow(HIGHEST_HERTZ / LOWEST_HERTZ, (double) band / bands);
    }
}

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

package com.adeptum.paula.module.zx;

/**
 * Turns the speaker's edges into samples without aliasing. Each change of level is added as a windowed-sinc
 * impulse placed to a fraction of a sample, and the running sum of the impulses is the signal, so a step is
 * band-limited and lands on the exact T-state. A high-pass takes off the standing level, since the speaker
 * rests at one of two levels, not at zero. An impulse reaches {@link #LOOKAHEAD} samples, so an edge must be
 * added before the samples it touches are read (one for an already read sample is dropped) and no more than
 * {@link #MAX_AHEAD} samples ahead of the read position (further, it would wrap onto the ring slot of an
 * unread earlier sample and corrupt it without any error).
 */
final class BeeperSynth {

    static final int LOOKAHEAD = 16;

    private static final int TAPS = 16;
    private static final int PHASES = 64;
    private static final int RING = 128;
    static final int MAX_AHEAD = RING - TAPS / 2 - 1;
    private static final double HIGH_PASS_HERTZ = 20;
    private static final double[][] KERNEL = kernel();

    private final double samplesPerTState;
    private final double[] ring = new double[RING];
    private final double pole;
    private long next;
    private double level;
    private double sum;
    private double previousInput;
    private double previousOutput;

    BeeperSynth(int sampleRate, int clock, long firstSample, double level) {
        this.samplesPerTState = (double) sampleRate / clock;
        this.pole = 1 - 2 * Math.PI * HIGH_PASS_HERTZ / sampleRate;
        this.next = firstSample;
        this.level = level;
        this.sum = level;
        this.previousInput = level;
    }

    void level(long tstate, double newLevel) {
        final double delta = newLevel - level;
        level = newLevel;
        final double position = tstate * samplesPerTState;
        final long whole = (long) Math.floor(position);
        final double[] weights = KERNEL[(int) ((position - whole) * PHASES)];
        for (int tap = 0; tap < TAPS; tap++) {
            final long sample = whole - TAPS / 2 + 1 + tap;
            if (sample >= next) {
                ring[(int) (sample & (RING - 1))] += delta * weights[tap];
            }
        }
    }

    double next() {
        final int at = (int) (next++ & (RING - 1));
        sum += ring[at];
        ring[at] = 0;
        previousOutput = sum - previousInput + pole * previousOutput;
        previousInput = sum;
        return previousOutput;
    }

    /**
     * One row per fraction of a sample the impulse can sit at: a sinc under a Hann window, scaled to add up to
     * one so that every step reaches its full height.
     */
    private static double[][] kernel() {
        final double[][] rows = new double[PHASES][TAPS];
        for (int phase = 0; phase < PHASES; phase++) {
            double total = 0;
            for (int tap = 0; tap < TAPS; tap++) {
                final double x = tap - TAPS / 2 + 1 - (double) phase / PHASES;
                final double window = 0.5 * (1 + Math.cos(Math.PI * x / (TAPS / 2)));
                rows[phase][tap] = sinc(x) * window;
                total += rows[phase][tap];
            }
            for (int tap = 0; tap < TAPS; tap++) {
                rows[phase][tap] /= total;
            }
        }
        return rows;
    }

    private static double sinc(double x) {
        return x == 0 ? 1 : Math.sin(Math.PI * x) / (Math.PI * x);
    }
}

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
 * Stereo peak meters that jump up with the signal and fall back a little each frame.
 */
public final class Vu {

    private static final double FALL = 0.85;
    private static final double FULL_SCALE = Short.MAX_VALUE;

    private double left;
    private double right;

    public void feed(short[] interleavedStereo) {
        double peakLeft = 0;
        double peakRight = 0;
        for (int i = 0; i + 1 < interleavedStereo.length; i += 2) {
            peakLeft = Math.max(peakLeft, Math.abs(interleavedStereo[i]) / FULL_SCALE);
            peakRight = Math.max(peakRight, Math.abs(interleavedStereo[i + 1]) / FULL_SCALE);
        }
        left = Math.max(Math.min(peakLeft, 1), left * FALL);
        right = Math.max(Math.min(peakRight, 1), right * FALL);
    }

    public double left() {
        return left;
    }

    public double right() {
        return right;
    }
}

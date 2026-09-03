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
 * The spectrum as it was, a ring of the rows fed to it with the newest at the front. Bands that were never
 * reached, at the start of a song or beyond what is kept, read as silence.
 */
public final class Waterfall {

    private final int bands;
    private final double[][] history;

    private int written;

    public Waterfall(int bands, int depth) {
        this.bands = bands;
        this.history = new double[Math.max(1, depth)][bands];
    }

    public int depth() {
        return history.length;
    }

    public void feed(double[] levels) {
        final double[] row = history[written % history.length];
        for (int band = 0; band < bands; band++) {
            row[band] = band < levels.length ? levels[band] : 0;
        }
        written++;
    }

    /**
     * A row by its age, zero being the one fed last.
     */
    public double[] row(int age) {
        if (age < 0 || age >= history.length || age >= written) {
            return new double[bands];
        }
        return history[Math.floorMod(written - 1 - age, history.length)];
    }
}

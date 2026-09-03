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

import java.util.List;

/**
 * A goniometer: every frame is a point, the left channel against the right, turned a half turn of an eighth so
 * that the two agreeing stands upright. A mono signal draws a vertical line, a wide stereo one opens out, and
 * one channel against the other leans it over. Amiga modules pan their channels hard apart, so a tune of that
 * kind draws a shape of its own rather than a line.
 */
public final class Vectorscope {

    private static final int STEREO = 2;
    private static final double CENTRE = 0.5;
    private static final double HALF = 0.5;
    private static final double MOST_GAIN = 8;

    private Vectorscope() {
    }

    /**
     * Plots interleaved frames in the range -1..1 across the given cell grid. The figure is drawn as wide as
     * it is tall, since a braille dot is about square and a shape stretched to the panel would read as more
     * width than the music has, and it is turned up to fill that square: music rarely comes near full scale,
     * and drawn to that scale it would sit in a knot in the middle. Near silence is left small rather than
     * amplified into a shape of its own.
     */
    public static List<String> plot(double[] interleavedStereo, int widthCells, int heightCells) {
        final BrailleCanvas canvas = new BrailleCanvas(widthCells, heightCells);
        final double reach = reachOf(interleavedStereo);
        final double gain = reach > 0 ? Math.min(MOST_GAIN, 1 / reach) : 0;
        final int square = Math.min(canvas.dotColumns(), canvas.dotRows());
        final double across = square / (double) Math.max(1, canvas.dotColumns());
        final double down = square / (double) Math.max(1, canvas.dotRows());
        for (int frame = 0; frame + 1 < interleavedStereo.length; frame += STEREO) {
            final double left = Math.clamp(interleavedStereo[frame], -1, 1);
            final double right = Math.clamp(interleavedStereo[frame + 1], -1, 1);
            canvas.set(CENTRE + (left - right) * gain * HALF * across,
                    CENTRE + (left + right) * gain * HALF * down);
        }
        return canvas.rows();
    }

    /**
     * How far the widest frame reaches from the middle, along whichever of the two diagonals goes further.
     */
    private static double reachOf(double[] interleavedStereo) {
        double reach = 0;
        for (int frame = 0; frame + 1 < interleavedStereo.length; frame += STEREO) {
            final double left = Math.clamp(interleavedStereo[frame], -1, 1);
            final double right = Math.clamp(interleavedStereo[frame + 1], -1, 1);
            reach = Math.max(reach, Math.max(Math.abs(left - right), Math.abs(left + right)));
        }
        return reach;
    }
}

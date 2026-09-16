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

/**
 * One line of a sample: its level and the slide on the channel volume, the tone offset and whether it adds up
 * from line to line, and an offset that bends the noise or, where the noise is masked, the envelope.
 */
public record Pt3SampleLine(int level, int volumeSlide, boolean toneOff, int toneOffset, boolean keepToneOffset,
        boolean noiseOff, boolean envelopeOff, int noiseOrEnvelopeOffset, boolean keepNoiseOrEnvelopeOffset) {

    public static final Pt3SampleLine SILENT = new Pt3SampleLine(0, 0, true, 0, false, true, true, 0, false);
}

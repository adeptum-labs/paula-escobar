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

class VuTest {

    private static final int SAMPLE_RATE = 48000;
    private static final int FRAMES = 1024;

    private final Vu vu = new Vu();

    @Test
    void followsEachChannelSeparately() {
        vu.feed(Signals.stereoSine(440, SAMPLE_RATE, FRAMES, 1.0, 0.25));

        assertEquals(1.0, vu.left(), 0.02);
        assertEquals(0.25, vu.right(), 0.02);
    }

    @Test
    void decaysAfterSilence() {
        vu.feed(Signals.stereoSine(440, SAMPLE_RATE, FRAMES, 1.0, 1.0));
        vu.feed(new short[FRAMES * 2]);
        final double afterOne = vu.left();
        assertTrue(afterOne > 0.5 && afterOne < 1.0, "one silent frame only starts the fall, was " + afterOne);

        for (int i = 0; i < 60; i++) {
            vu.feed(new short[FRAMES * 2]);
        }
        assertTrue(vu.left() < 0.05);
    }
}

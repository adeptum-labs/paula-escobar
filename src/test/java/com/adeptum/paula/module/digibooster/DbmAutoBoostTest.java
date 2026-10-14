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

package com.adeptum.paula.module.digibooster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class DbmAutoBoostTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 4096;
    private static final int STEREO = 2;

    @Test
    void boostsTheSongToJustShortOfClipping() throws IOException {
        final DbmFile module = DbmReader.read(TestModules.digiBooster());
        final DbmAutoBoost autoBoost = new DbmAutoBoost(module);

        assertTrue(autoBoost.advance(Long.MAX_VALUE), "a short song is through in one go");

        assertTrue(loudest(module, autoBoost.decibels()) < Short.MAX_VALUE, "the boost it found does not clip");
        assertEquals(Short.MAX_VALUE, loudest(module, autoBoost.decibels() + 2), "two decibels more would");
    }

    private static int loudest(DbmFile module, int decibels) {
        final DbmEngine engine = new DbmEngine(module, SAMPLE_RATE, decibels);
        final short[] out = new short[FRAMES * STEREO];
        int loudest = 0;
        for (int mixed = FRAMES; mixed == FRAMES; ) {
            mixed = engine.mix(out, FRAMES);
            for (int slot = 0; slot < mixed * STEREO; slot++) {
                loudest = Math.max(loudest, Math.abs(out[slot]));
            }
        }
        return loudest;
    }
}

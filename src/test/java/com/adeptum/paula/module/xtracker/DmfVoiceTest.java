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

package com.adeptum.paula.module.xtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DmfVoiceTest {

    private static final DmfSample LOOPED =
            new DmfSample("loop", new short[] {0, 1000, 2000, 3000}, 1, 3, 8363, 0, false);
    private static final DmfSample ONE_SHOT =
            new DmfSample("shot", new short[] {0, 1000, 2000, 3000}, 0, 0, 8363, 0, false);

    private final DmfVoice voice = new DmfVoice();

    @Test
    void drawsAStraightLineBetweenTheFramesItFallsBetween() {
        voice.start(ONE_SHOT, 0);
        voice.advance(1.25);

        assertEquals(1250, voice.frameAt());
    }

    @Test
    void goesRoundTheLoopFromItsEndBackToItsStart() {
        voice.start(LOOPED, 2);
        voice.advance(1.5);

        assertEquals(1.5, voice.position, 1e-9);
        assertTrue(voice.sounding);
    }

    @Test
    void playsOutToTheEndOnceTheLoopIsReleased() {
        voice.start(LOOPED, 2);
        voice.released = true;
        voice.advance(1);
        assertTrue(voice.sounding);

        voice.advance(1);
        assertFalse(voice.sounding, "nothing is left past the last frame");
    }

    @Test
    void runsBackwardsFromTheLoopStartToItsEnd() {
        voice.start(LOOPED, 1);
        voice.backwards = true;
        voice.advance(0.5);

        assertEquals(2.5, voice.position, 1e-9);
    }

    @Test
    void stopsAtTheHeadOfASampleWithoutALoopWhenRunningBackwards() {
        voice.start(ONE_SHOT, 1);
        voice.backwards = true;
        voice.advance(2);

        assertFalse(voice.sounding);
    }

    @Test
    void startsSilentWhenAskedToStartPastTheEnd() {
        voice.start(ONE_SHOT, 10);

        assertFalse(voice.sounding);
    }
}

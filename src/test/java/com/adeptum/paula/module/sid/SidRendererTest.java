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

package com.adeptum.paula.module.sid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.testing.TestSids;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SidRendererTest {

    private static final int SAMPLE_RATE = 8000;
    private static final int FRAMES = 1024;
    private static final Duration LENGTH = Duration.ofSeconds(2);
    private static final int LENGTH_FRAMES = SAMPLE_RATE * 2;

    private final Renderer renderer = new SidRenderer(TestSids.psid(), 1, LENGTH, SAMPLE_RATE);
    private final short[] buffer = new short[FRAMES * 2];

    @Test
    void rendersATone() {
        assertEquals(FRAMES, renderer.render(buffer));
        assertTrue(peak(buffer) > 500, "the pulse wave should be audible, peak was " + peak(buffer));
        assertEquals(Duration.ofMillis(FRAMES * 1000L / SAMPLE_RATE), renderer.position());
    }

    @Test
    void finishesExactlyAtTheSongLength() {
        int total = 0;
        for (int frames = renderer.render(buffer); frames > 0; frames = renderer.render(buffer)) {
            total += frames;
        }
        assertEquals(LENGTH_FRAMES, total);
        assertEquals(LENGTH, renderer.position());
        assertEquals(0, renderer.render(buffer), "a finished song stays finished");
    }

    @Test
    void seeksForwardToTheTarget() {
        renderer.seek(Duration.ofSeconds(1));
        assertEquals(Duration.ofSeconds(1), renderer.position());
        assertEquals(FRAMES, renderer.render(buffer));
        assertTrue(peak(buffer) > 500);
    }

    @Test
    void seeksBackwardByRestarting() {
        renderer.seek(Duration.ofMillis(1500));
        renderer.seek(Duration.ofMillis(500));
        assertEquals(Duration.ofMillis(500), renderer.position());
        assertEquals(FRAMES, renderer.render(buffer));
    }

    @Test
    void seekingBeforeTheStartClampsToZero() {
        renderer.render(buffer);
        renderer.seek(Duration.ofSeconds(-5));
        assertEquals(Duration.ZERO, renderer.position());
    }

    @Test
    void seekingPastTheEndFinishesTheSong() {
        renderer.seek(Duration.ofSeconds(10));
        assertEquals(LENGTH, renderer.position());
        assertEquals(0, renderer.render(buffer));
    }

    private static int peak(short[] samples) {
        int peak = 0;
        for (final short sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}

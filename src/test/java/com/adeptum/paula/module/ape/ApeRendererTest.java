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

package com.adeptum.paula.module.ape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.playback.Renderer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApeRendererTest {

    private static final int SOURCE_RATE = 44100;
    private static final int ENGINE_RATE = 48000;
    private static final int FRAMES = 4096;
    private static final int STEREO = 2;
    private static final int AUDIBLE = 1000;
    private static final int NEAR_ENOUGH_MILLIS = 30;

    private final short[] out = new short[FRAMES * STEREO];

    @Test
    void playsTheWholeFileAtTheRateTheEngineMixesAt(@TempDir Path dir) throws IOException {
        final Renderer renderer = renderer(dir, ENGINE_RATE);

        final long frames = playToTheEnd(renderer);

        assertTrue(Math.abs(frames - ENGINE_RATE) < ENGINE_RATE / 10,
                "a second resampled to forty-eight kilohertz, was " + frames + " frames");
    }

    @Test
    void playsAFileAlreadyAtTheOutputRateWithoutResampling(@TempDir Path dir) throws IOException {
        final long frames = playToTheEnd(renderer(dir, SOURCE_RATE));

        assertTrue(Math.abs(frames - SOURCE_RATE) < SOURCE_RATE / 10,
                "a second at its own rate, was " + frames + " frames");
    }

    @Test
    void soundsTheToneItHolds(@TempDir Path dir) throws IOException {
        final Renderer renderer = renderer(dir, ENGINE_RATE);

        renderer.render(out);
        renderer.render(out);

        assertTrue(loudest() > AUDIBLE, "the sine wave is heard, peak was " + loudest());
    }

    /**
     * The decoder seeks by itself, so the moment asked for is the moment landed on rather than the start of
     * whichever frame holds it.
     */
    @Test
    void seeksToTheMomentAskedFor(@TempDir Path dir) throws IOException {
        final Renderer renderer = renderer(dir, ENGINE_RATE);

        renderer.seek(Duration.ofMillis(500));

        assertTrue(Math.abs(renderer.position().toMillis() - 500) < NEAR_ENOUGH_MILLIS, "was " + renderer.position());
        assertTrue(renderer.render(out) > 0, "and there is still sound after it");
        assertTrue(loudest() > AUDIBLE);
    }

    @Test
    void seekingBackToTheStartPlaysItAllAgain(@TempDir Path dir) throws IOException {
        final Renderer renderer = renderer(dir, ENGINE_RATE);
        playToTheEnd(renderer);

        renderer.seek(Duration.ZERO);

        assertEquals(Duration.ZERO, renderer.position());
        assertTrue(playToTheEnd(renderer) > ENGINE_RATE / 2, "the whole file is there a second time");
    }

    @Test
    void seekingPastTheEndFinishesTheSong(@TempDir Path dir) throws IOException {
        final Renderer renderer = renderer(dir, ENGINE_RATE);

        renderer.seek(Duration.ofMinutes(5));

        assertEquals(0, renderer.render(out), "nothing is left to play");
    }

    @Test
    void knowsHowLongTheFileIs(@TempDir Path dir) throws IOException {
        assertEquals(1000, renderer(dir, ENGINE_RATE).length().orElseThrow().toMillis());
    }

    private Renderer renderer(Path dir, int rate) throws IOException {
        return new ApeLoader().load(ApeLoaderTest.fixture(dir)).createRenderer(rate);
    }

    private long playToTheEnd(Renderer renderer) {
        long frames = 0;
        for (int rendered = renderer.render(out); rendered > 0; rendered = renderer.render(out)) {
            frames += rendered;
        }
        return frames;
    }

    private int loudest() {
        int peak = 0;
        for (final short sample : out) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}

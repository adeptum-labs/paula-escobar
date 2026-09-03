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
package com.adeptum.paula.module.ogg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.playback.Renderer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OggRendererTest {

    private static final int SOURCE_RATE = 44100;
    private static final int ENGINE_RATE = 48000;
    private static final int HALF_RATE = 22050;
    private static final int FRAMES = 4096;
    private static final int STEREO = 2;

    private final short[] out = new short[FRAMES * STEREO];

    @Test
    void playsTheWholeFileAtTheRateTheEngineMixesAt(@TempDir Path dir) throws IOException {
        final Renderer renderer = renderer(dir, ENGINE_RATE);

        final long frames = playToTheEnd(renderer);

        assertTrue(Math.abs(frames - ENGINE_RATE) < ENGINE_RATE / 10,
                "a second resampled to forty-eight kilohertz, was " + frames + " frames");
        assertEquals(frames * 1000 / ENGINE_RATE, renderer.position().toMillis(),
                "the position follows what was handed out");
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

        assertTrue(loudest() > 1000, "the sine wave is heard, peak was " + loudest());
    }

    /**
     * The fixture holds a 440 Hz sine, so a resampler that got its ratio wrong would move the pitch rather than
     * merely colour it, which counting the times the wave crosses zero catches at either rate.
     */
    @Test
    void keepsThePitchWhateverRateItIsPlayedAt(@TempDir Path dir) throws IOException {
        assertEquals(440, toneOf(renderer(dir, ENGINE_RATE), ENGINE_RATE), 15, "resampled to forty-eight kilohertz");
        assertEquals(440, toneOf(renderer(dir, SOURCE_RATE), SOURCE_RATE), 15, "at its own rate");
        assertEquals(440, toneOf(renderer(dir, HALF_RATE), HALF_RATE), 15, "and resampled down to half of it");
    }

    @Test
    void seekingLandsOnThePageTheMomentFallsIn(@TempDir Path dir) throws IOException {
        final Renderer renderer = renderer(dir, ENGINE_RATE);

        renderer.seek(Duration.ofMillis(500));

        assertTrue(Math.abs(renderer.position().toMillis() - 500) < 30, "was " + renderer.position());
        assertTrue(renderer.render(out) > 0, "and there is still sound after it");
        assertTrue(loudest() > 1000);
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
        return new OggLoader().load(OggLoaderTest.fixture(dir)).createRenderer(rate);
    }

    private long playToTheEnd(Renderer renderer) {
        long frames = 0;
        int rendered = renderer.render(out);
        while (rendered > 0) {
            frames += rendered;
            rendered = renderer.render(out);
        }
        return frames;
    }

    /**
     * The pitch in the middle of the file, counted as the waves that swing the whole way from below half the
     * peak to above it. Quantisation noise crosses zero on its own, which is why half the peak and not zero.
     */
    private double toneOf(Renderer renderer, int rate) {
        renderer.seek(Duration.ofMillis(200));
        renderer.render(out);
        int cycles = 0;
        int frames = 0;
        boolean high = false;
        for (int rendered = renderer.render(out); rendered > 0 && frames < rate / 2; rendered = renderer.render(out)) {
            final int threshold = loudest() / 2;
            for (int frame = 0; frame < rendered; frame++) {
                final short sample = out[frame * STEREO];
                if (!high && sample > threshold) {
                    high = true;
                    cycles++;
                } else if (high && sample < -threshold) {
                    high = false;
                }
            }
            frames += rendered;
        }
        return (double) cycles * rate / frames;
    }

    private int loudest() {
        int loudest = 0;
        for (final short sample : out) {
            loudest = Math.max(loudest, Math.abs(sample));
        }
        return loudest;
    }
}

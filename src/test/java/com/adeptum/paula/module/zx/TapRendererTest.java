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

package com.adeptum.paula.module.zx;

import static com.adeptum.paula.testing.TestTaps.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.module.Module;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TapRendererTest {

    private static final int SAMPLE_RATE = 8000;
    private static final int FRAMES = 1024;

    @TempDir
    Path dir;

    private final short[] buffer = new short[FRAMES * 2];

    private Renderer renderer(byte[] code) throws Exception {
        final Path file = Files.write(dir.resolve("a.tap"), loaderTape(code));
        final Module module = new TapLoader(new TapeLengths(new CacheDirectory(dir.resolve("cache")))).load(file);
        return module.createRenderer(SAMPLE_RATE);
    }

    private static int peak(short[] samples) {
        int peak = 0;
        for (final short sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    private static int crossings(short[] samples, int frames) {
        int crossings = 0;
        for (int i = 1; i < frames; i++) {
            if (samples[(i - 1) * 2] < 0 != samples[i * 2] < 0) {
                crossings++;
            }
        }
        return crossings;
    }

    @Test
    void rendersTheSquareWaveAtItsPitch() throws Exception {
        final Renderer renderer = renderer(SQUARE_WAVE);
        renderer.render(buffer);

        assertEquals(FRAMES, renderer.render(buffer));

        assertTrue(peak(buffer) > 3000, "peak was " + peak(buffer));
        final double hertz = crossings(buffer, FRAMES) / 2.0 * SAMPLE_RATE / FRAMES;
        assertEquals(1036, hertz, 80, "the 3377 T-state period of the test program");
    }

    @Test
    void finishesExactlyAtTheLengthTheLoaderFound() throws Exception {
        final Renderer renderer = renderer(HALF_SECOND_THEN_RETURN);
        final long expected = renderer.length().orElseThrow().toMillis() * SAMPLE_RATE / 1000;

        long total = 0;
        for (int frames = renderer.render(buffer); frames > 0; frames = renderer.render(buffer)) {
            total += frames;
        }

        assertEquals(expected, total);
        assertEquals(0, renderer.render(buffer), "a finished song stays finished");
    }

    @Test
    void reportsTheLengthAndPosition() throws Exception {
        final Renderer renderer = renderer(SQUARE_WAVE);

        assertEquals(TapeLengths.CAP, renderer.length().orElseThrow());
        renderer.render(buffer);
        assertEquals(Duration.ofMillis(FRAMES * 1000L / SAMPLE_RATE), renderer.position());
    }

    @Test
    void seeksForwardAndStillSounds() throws Exception {
        final Renderer renderer = renderer(SQUARE_WAVE);
        renderer.seek(Duration.ofSeconds(20));
        assertEquals(Duration.ofSeconds(20), renderer.position());

        int buffers = 0;
        do {
            renderer.render(buffer);
        } while (peak(buffer) == 0 && ++buffers < 200);

        assertTrue(peak(buffer) > 3000, "sound returns once the emulation has caught up");
    }

    @Test
    void seekingToWhereTheCatchUpHasReachedStillSounds() throws Exception {
        final Renderer renderer = renderer(SQUARE_WAVE);
        renderer.seek(Duration.ofSeconds(100));
        renderer.render(buffer);
        renderer.seek(Duration.ofMillis(16384));

        renderer.render(buffer);
        renderer.render(buffer);

        assertTrue(peak(buffer) > 3000, "peak was " + peak(buffer));
    }

    @Test
    void seeksBackwardByRestarting() throws Exception {
        final Renderer renderer = renderer(SQUARE_WAVE);
        renderer.seek(Duration.ofSeconds(10));
        renderer.seek(Duration.ofSeconds(2));

        assertEquals(Duration.ofSeconds(2), renderer.position());
    }

    @Test
    void seekingBeforeTheStartClampsToZero() throws Exception {
        final Renderer renderer = renderer(SQUARE_WAVE);
        renderer.render(buffer);
        renderer.seek(Duration.ofSeconds(-5));

        assertEquals(Duration.ZERO, renderer.position());
    }

    @Test
    void seekingPastTheEndFinishesTheSong() throws Exception {
        final Renderer renderer = renderer(SQUARE_WAVE);
        renderer.seek(Duration.ofMinutes(30));

        assertEquals(TapeLengths.CAP, renderer.position());
        assertEquals(0, renderer.render(buffer));
    }

    @Test
    void mixesTheAyIntoATapeThatWritesIt() throws Exception {
        final Renderer renderer = renderer(AY_TONE);

        renderer.render(buffer);
        renderer.render(buffer);

        assertTrue(peak(buffer) > 1000, "an AY tone with no beeper edges is audible, peak was " + peak(buffer));
    }
}


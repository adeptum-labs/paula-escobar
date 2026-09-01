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

package com.adeptum.paula.playback.javamod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.javamod.JavaModLoader;
import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.testing.TestModules;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaModRendererTest {

    private static final int SAMPLE_RATE = 8000;
    private static final int FRAMES = 1024;
    private static final int MAX_BUFFERS = SAMPLE_RATE * 120 / FRAMES;

    @Test
    void rendersAudioAndEventuallyFinishes(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeProTracker(dir)).createRenderer(SAMPLE_RATE);
        final short[] buffer = new short[FRAMES * 2];

        assertEquals(FRAMES, renderer.render(buffer));
        assertTrue(peak(buffer) > 1000, "first buffer should contain the square wave");
        assertEquals(Duration.ofMillis(FRAMES * 1000L / SAMPLE_RATE), renderer.position());

        int buffers = 1;
        while (renderer.render(buffer) > 0 && buffers < MAX_BUFFERS) {
            buffers++;
        }
        assertTrue(buffers < MAX_BUFFERS, "song should end once it loops and fades out");
    }

    @Test
    void reducesThirtyTwoBitSamplesToSixteenBitWithClamping() {
        assertEquals(0, JavaModRenderer.toPcm16(0));
        assertEquals(1, JavaModRenderer.toPcm16(1 << 16));
        assertEquals(Short.MAX_VALUE, JavaModRenderer.toPcm16(Long.MAX_VALUE / 2));
        assertEquals(Short.MIN_VALUE, JavaModRenderer.toPcm16(Long.MIN_VALUE / 2));
    }

    private static int peak(short[] samples) {
        int peak = 0;
        for (short sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}

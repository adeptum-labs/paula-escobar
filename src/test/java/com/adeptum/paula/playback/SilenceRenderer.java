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

package com.adeptum.paula.playback;

import java.time.Duration;
import java.util.Arrays;

/**
 * Placeholder renderer that outputs silence for a fixed duration until real synthesis lands.
 */
public final class SilenceRenderer implements Renderer {

    private static final int CHANNELS = 2;

    private final int sampleRate;
    private final long totalFrames;
    private long renderedFrames;

    public SilenceRenderer(Duration length, int sampleRate) {
        this.sampleRate = sampleRate;
        this.totalFrames = length.toMillis() * sampleRate / 1000;
    }

    @Override
    public int render(short[] interleavedStereo) {
        final int frames = (int) Math.min(interleavedStereo.length / CHANNELS, totalFrames - renderedFrames);
        Arrays.fill(interleavedStereo, 0, frames * CHANNELS, (short) 0);
        renderedFrames += frames;
        return frames;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis(renderedFrames * 1000 / sampleRate);
    }
}

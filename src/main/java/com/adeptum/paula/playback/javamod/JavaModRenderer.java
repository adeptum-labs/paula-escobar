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

import com.adeptum.paula.playback.Renderer;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import java.time.Duration;

/**
 * Pulls 32-bit stereo audio from JavaMod's mixer and reduces it to Paula's 16-bit interleaved frames.
 */
public final class JavaModRenderer implements Renderer {

    private static final int OUTPUT_CHANNELS = 2;
    private static final int BITS_TO_DROP = 32 - Short.SIZE;
    private static final long ROUNDING = 1L << (BITS_TO_DROP - 1);
    private static final int MAX_NNA_CHANNELS = 200;

    private final BasicModMixer mixer;
    private final int sampleRate;
    private long[] left = new long[0];
    private long[] right = new long[0];
    private long renderedFrames;

    public JavaModRenderer(Module tracker, int sampleRate) {
        this.sampleRate = sampleRate;
        this.mixer = tracker.getModMixer(sampleRate, ModConstants.INTERPOLATION_WINDOWSFIR,
                ModConstants.AMIGAEMULATION_NONE, ModConstants.PLAYER_LOOP_FADEOUT, MAX_NNA_CHANNELS);
        mixer.setFireUpdates(false);
    }

    @Override
    public int render(short[] interleavedStereo) {
        final int frames = interleavedStereo.length / OUTPUT_CHANNELS;
        ensureBuffers(frames);
        final int mixed = mixer.mixIntoBuffer(left, right, frames);
        if (mixed <= 0) {
            return 0;
        }
        for (int i = 0; i < mixed; i++) {
            interleavedStereo[i * OUTPUT_CHANNELS] = toPcm16(left[i]);
            interleavedStereo[i * OUTPUT_CHANNELS + 1] = toPcm16(right[i]);
            left[i] = 0;
            right[i] = 0;
        }
        renderedFrames += mixed;
        return mixed;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis(renderedFrames * 1000 / sampleRate);
    }

    /**
     * JavaMod replays the song from the start without mixing and reports how far it actually got, which is the
     * first tick at or past the target.
     */
    @Override
    public void seek(Duration target) {
        renderedFrames = mixer.seek(target.toMillis());
    }

    static short toPcm16(long sample) {
        return (short) Math.clamp((sample + ROUNDING) >> BITS_TO_DROP, Short.MIN_VALUE, Short.MAX_VALUE);
    }

    private void ensureBuffers(int frames) {
        if (left.length < frames) {
            left = new long[frames];
            right = new long[frames];
        }
    }
}

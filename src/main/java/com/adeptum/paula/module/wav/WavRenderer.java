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

package com.adeptum.paula.module.wav;

import com.adeptum.paula.module.pcm.PcmRenderer;
import java.time.Duration;
import java.util.Optional;

/**
 * Plays one wave file. Every frame is the same width and sits at a known offset, so there is nothing to
 * decode beyond widening each sample to the sixteen bits the engine mixes at, and a seek is arithmetic.
 */
public final class WavRenderer extends PcmRenderer {

    private static final int BLOCK_FRAMES = 4096;

    private final byte[] file;
    private final WavAudio audio;

    private int frame;

    public WavRenderer(byte[] file, WavAudio audio, int outputRate) {
        super(audio.rate(), audio.channels(), outputRate);
        this.file = file;
        this.audio = audio;
    }

    @Override
    public Optional<Duration> length() {
        return Optional.of(audio.length());
    }

    @Override
    protected void rewind(Duration target) {
        frame = audio.frameAt(target);
    }

    @Override
    protected short[] decode() {
        final int frames = Math.min(BLOCK_FRAMES, audio.frames() - frame);
        if (frames <= 0) {
            return null;
        }
        final short[] block = new short[frames * audio.channels()];
        final int first = frame * audio.channels();
        for (int sample = 0; sample < block.length; sample++) {
            block[sample] = audio.sampleAt(file, first + sample);
        }
        frame += frames;
        return block;
    }
}

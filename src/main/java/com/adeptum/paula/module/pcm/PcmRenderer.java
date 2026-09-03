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

package com.adeptum.paula.module.pcm;

import com.adeptum.paula.playback.Renderer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * What every format that decodes to plain samples has in common: the decoded frames are handed on at the rate
 * the engine mixes at, interpolating between the two source frames the fractional position falls between when
 * the two rates differ, and a file already at the output rate passes through untouched. A subclass has only to
 * say how long the song is, hand over the next block of samples, and start the source again for a seek.
 */
public abstract class PcmRenderer implements Renderer {

    protected static final int STEREO = 2;

    private static final int MILLIS = 1000;

    private final int outputRate;
    private final int sourceChannels;
    private final double step;

    private short[] source = new short[0];
    private int sourceFrames;
    private int at;
    private double fraction;
    private boolean ended;
    private long renderedFrames;

    protected PcmRenderer(int sourceRate, int sourceChannels, int outputRate) {
        this.outputRate = outputRate;
        this.sourceChannels = sourceChannels;
        this.step = (double) sourceRate / outputRate;
    }

    /**
     * The next block of samples from the source, interleaved by the channel count the renderer was built with,
     * or nothing once the song has run out.
     */
    protected abstract short[] decode();

    /**
     * Puts the source back to the given moment, as near as its own framing allows, before any of it is decoded
     * again.
     */
    protected abstract void rewind(Duration target);

    @Override
    public abstract Optional<Duration> length();

    @Override
    public int render(short[] interleavedStereo) {
        final int frames = interleavedStereo.length / STEREO;
        int produced = 0;
        while (produced < frames && hasPair()) {
            final int base = at * STEREO;
            interleavedStereo[produced * STEREO] = between(source[base], source[base + STEREO]);
            interleavedStereo[produced * STEREO + 1] = between(source[base + 1], source[base + STEREO + 1]);
            advance();
            produced++;
        }
        renderedFrames += produced;
        return produced;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis(renderedFrames * MILLIS / outputRate);
    }

    @Override
    public void seek(Duration target) {
        final Duration clamped = target.isNegative() ? Duration.ZERO : target;
        final long millis = length().map(Duration::toMillis).map(end -> Math.min(clamped.toMillis(), end))
                .orElse(clamped.toMillis());
        restart();
        rewind(clamped);
        renderedFrames = millis * outputRate / MILLIS;
    }

    /**
     * Drops what was decoded and read so far; a subclass calls this when it opens the source for the first time.
     */
    protected final void restart() {
        sourceFrames = 0;
        at = 0;
        fraction = 0;
        ended = false;
        renderedFrames = 0;
    }

    private boolean hasPair() {
        while (at + 1 >= sourceFrames) {
            if (ended) {
                return false;
            }
            final short[] decoded = decode();
            if (decoded == null) {
                ended = true;
                return false;
            }
            compact();
            append(decoded);
        }
        return true;
    }

    private void advance() {
        fraction += step;
        final int whole = (int) fraction;
        at += whole;
        fraction -= whole;
    }

    private short between(short lower, short upper) {
        return (short) Math.round(lower + (upper - lower) * fraction);
    }

    /**
     * What has already been played is dropped before the next block is added, so the buffer stays the length of
     * a block or two however long the song is.
     */
    private void compact() {
        final int dropped = Math.min(at, sourceFrames);
        final int kept = sourceFrames - dropped;
        System.arraycopy(source, dropped * STEREO, source, 0, kept * STEREO);
        sourceFrames = kept;
        at -= dropped;
    }

    private void append(short[] decoded) {
        final int frames = decoded.length / sourceChannels;
        if (source.length < (sourceFrames + frames) * STEREO) {
            source = Arrays.copyOf(source, (sourceFrames + frames) * STEREO);
        }
        for (int frame = 0; frame < frames; frame++) {
            final short left = decoded[frame * sourceChannels];
            source[(sourceFrames + frame) * STEREO] = left;
            source[(sourceFrames + frame) * STEREO + 1] =
                    sourceChannels == 1 ? left : decoded[frame * sourceChannels + 1];
        }
        sourceFrames += frames;
    }
}

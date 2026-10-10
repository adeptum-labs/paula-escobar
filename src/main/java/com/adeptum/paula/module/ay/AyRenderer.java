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

package com.adeptum.paula.module.ay;

import com.adeptum.paula.playback.Renderer;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Plays an AY tune: the source hands over the registers once a frame, the chip is carried forward between
 * frames, and what comes out is written as stereo. The machine's interrupt is what divides the time, so the
 * frame rate is the tune's own rather than anything to do with the output rate.
 */
public final class AyRenderer implements Renderer {

    private static final int OUTPUT_CHANNELS = 2;
    private static final int MILLIS = 1000;

    private final AySource source;
    private final AyChip chip;
    private final int sampleRate;
    private final int framesPerSecond;
    private final int tickFrames;
    private final int tickRemainder;
    private final AyRegisters registers = new AyRegisters();

    private long rendered;
    private int untilTick;
    private int carried;

    public AyRenderer(AySource source, AyChip.Voicing voicing, double clockRate, int framesPerSecond,
            int sampleRate) {
        this.source = source;
        this.sampleRate = sampleRate;
        this.framesPerSecond = framesPerSecond;
        this.tickFrames = sampleRate / framesPerSecond;
        this.tickRemainder = sampleRate % framesPerSecond;
        this.chip = AyRegisters.chip(voicing, clockRate, sampleRate);
    }

    @Override
    public int render(short[] interleavedStereo) {
        final int wanted = interleavedStereo.length / OUTPUT_CHANNELS;
        int written = 0;
        while (written < wanted) {
            if (untilTick == 0 && !beginTick()) {
                break;
            }
            final int chunk = Math.min(wanted - written, untilTick);
            sound(interleavedStereo, written, chunk);
            written += chunk;
            untilTick -= chunk;
        }
        rendered += written;
        return written;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis(rendered * MILLIS / sampleRate);
    }

    @Override
    public Optional<Duration> length() {
        final OptionalLong frames = source.frames();
        return frames.isPresent()
                ? Optional.of(Duration.ofMillis(frames.getAsLong() * MILLIS / framesPerSecond))
                : Optional.empty();
    }

    /**
     * Every frame holds the whole state of the chip, so there is nothing to catch up on: the tune is picked
     * up at the frame the target falls in and sounds from there.
     */
    @Override
    public void seek(Duration target) {
        final long millis = Math.max(0, target.toMillis());
        source.rewindTo(millis * framesPerSecond / MILLIS);
        rendered = millis * sampleRate / MILLIS;
        untilTick = 0;
        carried = 0;
    }

    /**
     * The interrupt does not always fall on a whole number of output frames, so what is left over is carried
     * and spent as soon as it comes to one.
     */
    private boolean beginTick() {
        if (!source.nextFrame(registers.values())) {
            return false;
        }
        registers.applyTo(chip);
        untilTick = tickFrames;
        carried += tickRemainder;
        if (carried >= framesPerSecond) {
            carried -= framesPerSecond;
            untilTick++;
        }
        return true;
    }

    private void sound(short[] interleavedStereo, int from, int frames) {
        for (int at = 0; at < frames; at++) {
            chip.next();
            interleavedStereo[(from + at) * OUTPUT_CHANNELS] = pcm(chip.left());
            interleavedStereo[(from + at) * OUTPUT_CHANNELS + 1] = pcm(chip.right());
        }
    }

    private static short pcm(double sample) {
        return (short) Math.clamp(Math.round(sample * AyRegisters.HEADROOM * Short.MAX_VALUE),
                Short.MIN_VALUE, Short.MAX_VALUE);
    }
}

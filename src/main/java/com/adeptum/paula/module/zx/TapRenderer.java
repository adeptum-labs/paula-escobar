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

import com.adeptum.paula.module.ay.AyChip;
import com.adeptum.paula.module.ay.AyRegisters;
import com.adeptum.paula.module.ay.RegisterFrames;
import com.adeptum.paula.playback.Renderer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;

/**
 * Plays a tape by running its program a little ahead of the sample being written: the speaker's edges go to the
 * band-limited synthesiser, the AY's register writes are held until the sample they fall in and applied to the
 * chip there. A seek forwards, or backwards after a restart, runs the program on without audio in slices from the
 * audio thread, playing silence meanwhile, rather than stalling whoever asked for it; the speaker and the AY pick
 * up from the state the run ended in.
 */
final class TapRenderer implements Renderer, MachineOutput {

    private static final int CHANNELS = 2;
    private static final int MILLIS = 1000;
    private static final long CATCH_UP_SAMPLES_PER_BUFFER = 131072;
    private static final double BEEPER_GAIN = 0.8;

    private record AyWrite(long tstate, int register, int value) {
    }

    private final TapProgram program;
    private final TapeRun run;
    private final int sampleRate;
    private final long lengthFrames;
    private final Deque<AyWrite> ayWrites = new ArrayDeque<>();
    private Spectrum machine;
    private BeeperSynth synth;
    private AyChip chip;
    private AyRegisters registers;
    private double beeperLevel;
    private boolean ayUsed;
    private boolean catchingUp;
    private long sample;
    private long pending;

    TapRenderer(TapProgram program, TapeRun run, int sampleRate) {
        this.program = program;
        this.run = run;
        this.sampleRate = sampleRate;
        this.lengthFrames = run.length().toMillis() * sampleRate / MILLIS;
        start();
    }

    @Override
    public int render(short[] interleavedStereo) {
        catchUp();
        final int frames = (int) Math.min(interleavedStereo.length / CHANNELS, lengthFrames - sample - pending);
        if (frames <= 0) {
            return 0;
        }
        if (pending > 0) {
            Arrays.fill(interleavedStereo, 0, frames * CHANNELS, (short) 0);
            return frames;
        }
        for (int frame = 0; frame < frames; frame++) {
            machine.runUntil(tstateOf(sample + BeeperSynth.LOOKAHEAD));
            mix(interleavedStereo, frame);
            sample++;
        }
        return frames;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis((sample + pending) * MILLIS / sampleRate);
    }

    @Override
    public Optional<Duration> length() {
        return Optional.of(run.length());
    }

    @Override
    public void seek(Duration target) {
        final long targetFrames = Math.clamp(target.toMillis() * sampleRate / MILLIS, 0, lengthFrames);
        if (targetFrames < sample) {
            start();
        }
        pending = targetFrames - sample;
        if (pending > 0) {
            ayWrites.forEach(write -> registers.write(write.register(), write.value()));
            ayWrites.clear();
            catchingUp = true;
        } else if (catchingUp) {
            finishCatchUp();
        }
    }

    @Override
    public void beeper(long tstate, double level) {
        beeperLevel = level;
        if (!catchingUp) {
            synth.level(tstate, level);
        }
    }

    @Override
    public void ayRegister(long tstate, int register, int value) {
        ayUsed = true;
        if (catchingUp) {
            registers.write(register, value);
        } else {
            ayWrites.add(new AyWrite(tstate, register, value));
        }
    }

    private void start() {
        machine = new Spectrum(run.model(), program, this);
        synth = new BeeperSynth(sampleRate, run.model().clock(), 0, 0);
        registers = new AyRegisters();
        chip = AyRegisters.chip(AyChip.Voicing.AY, RegisterFrames.SPECTRUM_CLOCK, sampleRate);
        ayWrites.clear();
        beeperLevel = 0;
        ayUsed = false;
        catchingUp = false;
        sample = 0;
        pending = 0;
    }

    private void catchUp() {
        if (pending == 0) {
            return;
        }
        final long slice = Math.min(pending, CATCH_UP_SAMPLES_PER_BUFFER);
        sample += slice;
        pending -= slice;
        machine.runUntil(tstateOf(sample));
        if (pending == 0) {
            finishCatchUp();
        }
    }

    private void finishCatchUp() {
        catchingUp = false;
        synth = new BeeperSynth(sampleRate, run.model().clock(), sample, beeperLevel);
        registers.applyTo(chip);
    }

    private void mix(short[] out, int frame) {
        final long now = tstateOf(sample);
        double left = synth.next() * BEEPER_GAIN;
        double right = left;
        if (ayUsed) {
            while (!ayWrites.isEmpty() && ayWrites.peek().tstate() <= now) {
                final AyWrite write = ayWrites.poll();
                registers.write(write.register(), write.value());
                registers.applyTo(chip);
            }
            chip.next();
            left += chip.left() * AyRegisters.HEADROOM;
            right += chip.right() * AyRegisters.HEADROOM;
        }
        out[frame * CHANNELS] = pcm(left);
        out[frame * CHANNELS + 1] = pcm(right);
    }

    private long tstateOf(long sampleIndex) {
        return sampleIndex * run.model().clock() / sampleRate;
    }

    private static short pcm(double sample) {
        return (short) Math.clamp(Math.round(sample * Short.MAX_VALUE), Short.MIN_VALUE, Short.MAX_VALUE);
    }
}

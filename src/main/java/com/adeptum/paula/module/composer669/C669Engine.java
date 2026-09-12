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
 *
 * The frequencies, slides and vibrato follow what OpenMPT's player, released
 * under the BSD licence, found the original Composer 669 to do.
 */

package com.adeptum.paula.module.composer669;

import java.util.Arrays;
import java.util.OptionalLong;

/**
 * Plays a Composer 669 module: a sequencer walking the order list row by row and tick by tick at the fixed
 * pace of the tracker, and a mixdown of the eight channels into 16-bit stereo. Composer 669 thinks in hertz
 * rather than Amiga periods, so every slide moves a frequency by a whole number of hertz and bites harder on
 * the low notes than on the high ones. The song is played once through.
 */
final class C669Engine {

    static final int VOICES = C669Pattern.CHANNELS;
    static final int LOWEST_FREQUENCY = 112;

    private static final int STEREO = 2;
    private static final int GAIN_BITS = 14;
    private static final int GAIN_UNIT = 1 << GAIN_BITS;
    private static final int CLIP_HIGH = 0x7FFF;
    private static final int CLIP_LOW = -0x8000;
    private static final int TEMPO = 78;
    private static final int SECONDS_PER_TICK_NUMERATOR = 5;
    private static final int SECONDS_PER_TICK_DENOMINATOR = TEMPO * 2;
    private static final int C5_FREQUENCY = 8363;
    private static final int NOTES_PER_OCTAVE = 12;
    private static final int OCTAVES_BELOW_C5 = 2;
    private static final int SEMITONE_BITS = 16;
    private static final int SLIDE_HERTZ = 80;
    private static final int PORTAMENTO_HERTZ = 40;
    private static final int VIBRATO_HERTZ = 668;
    private static final int PAN_STEP = 16;
    private static final int LEFT = 0x30;
    private static final int RIGHT = 0xD0;
    private static final int VOLUME_STEPS = 15;
    private static final int VOLUME_ROUNDING = 8;
    private static final int PORTAMENTO_UP = 0;
    private static final int PORTAMENTO_DOWN = 1;
    private static final int TONE_PORTAMENTO = 2;
    private static final int FINETUNE = 3;
    private static final int VIBRATO = 4;
    private static final int SPEED = 5;
    private static final int PAN_SLIDE = 6;
    private static final int RETRIGGER = 7;
    private static final int PAN_LEFT = 0;
    private static final int PAN_RIGHT = 1;

    /**
     * Each semitone above C as a multiple of 65536, the table OpenMPT's linear slides step through.
     */
    private static final int[] SEMITONE = new int[NOTES_PER_OCTAVE];

    static {
        for (int semitone = 0; semitone < NOTES_PER_OCTAVE; semitone++) {
            SEMITONE[semitone] = (int) Math.round((1 << SEMITONE_BITS) * Math.pow(2, semitone / (double) NOTES_PER_OCTAVE));
        }
    }

    private final C669File file;
    private final int sampleRate;
    private final long tickFramesNumerator;
    private final C669Voice[] voices = new C669Voice[VOICES];
    private int order;
    private int row;
    private int tick;
    private int speed;
    private int tickFrames;
    private int tickFramesLeft;
    private long tickRemainder;
    private int[] accumulator = new int[0];
    private boolean ended;

    C669Engine(C669File file, int sampleRate) {
        this.file = file;
        this.sampleRate = sampleRate;
        this.tickFramesNumerator = (long) sampleRate * SECONDS_PER_TICK_NUMERATOR;
        for (int number = 0; number < VOICES; number++) {
            voices[number] = new C669Voice();
            voices[number].panning = number % 2 == 0 ? LEFT : RIGHT;
        }
        final C669Pattern first = file.patternAt(0);
        speed = first == null ? 1 : first.speed();
    }

    /**
     * The frequency a note sounds at: C-5, note 24, at the 8363 Hz every sample is taken to be recorded at,
     * the rest by equal temperament in the integer arithmetic OpenMPT uses, so the two agree to the hertz.
     */
    static int frequencyOf(int note) {
        final long scaled = (long) C5_FREQUENCY * SEMITONE[note % NOTES_PER_OCTAVE] << (note / NOTES_PER_OCTAVE);
        return (int) (scaled >> (SEMITONE_BITS + OCTAVES_BELOW_C5));
    }

    C669Voice voice(int number) {
        return voices[number];
    }

    boolean hasEnded() {
        return ended;
    }

    int order() {
        return order;
    }

    int row() {
        return row;
    }

    int speed() {
        return speed;
    }

    int tickFrames() {
        return tickFrames;
    }

    /**
     * Mixes the next frames and says how many of them the song still had music for; a shorter answer than
     * asked means the song ended inside the buffer.
     */
    int mix(short[] out, int frames) {
        room(frames);
        Arrays.fill(accumulator, 0, frames * STEREO, 0);
        int mixed = 0;
        int left = frames;
        while (!ended && left > 0) {
            if (tickFramesLeft == 0) {
                nextTick();
                tickFramesLeft = tickFrames;
                if (ended) {
                    break;
                }
            }
            final int chunk = Math.min(tickFramesLeft, left);
            for (final C669Voice voice : voices) {
                mixIn(voice, mixed * STEREO, chunk);
            }
            left -= chunk;
            tickFramesLeft -= chunk;
            mixed += chunk;
        }
        flush(out, frames);
        return mixed;
    }

    /**
     * How many frames the song lasts, played through without mixing anything, or nothing at all where it runs
     * past the length a caller is willing to wait for.
     */
    static OptionalLong songFrames(C669File file, int sampleRate, long limit) {
        final C669Engine engine = new C669Engine(file, sampleRate);
        long frames = 0;
        while (frames < limit) {
            engine.nextTick();
            if (engine.ended) {
                return OptionalLong.of(frames);
            }
            frames += engine.tickFrames;
        }
        return OptionalLong.empty();
    }

    /**
     * Plays one tick: the row is read at its first tick, then every channel is moved on by whatever it carries.
     * The tracker keeps 78 beats a minute whatever the song, so a tick is a fixed fraction of a second and the
     * remainder is carried from tick to tick rather than lost.
     */
    void nextTick() {
        if (ended) {
            return;
        }
        if (tick == 0 && !startRow()) {
            ended = true;
            return;
        }
        perTick();
        tickRemainder += tickFramesNumerator;
        tickFrames = (int) (tickRemainder / SECONDS_PER_TICK_DENOMINATOR);
        tickRemainder %= SECONDS_PER_TICK_DENOMINATOR;
        if (++tick >= speed) {
            tick = 0;
            advanceRow();
        }
    }

    int soundingFrequency(C669Voice voice) {
        return Math.max(LOWEST_FREQUENCY, voice.frequency + voice.finetune + voice.vibrato);
    }

    /**
     * Reads the row every channel stands on, at the pattern's own speed when it is the first, or says the
     * order list has run out.
     */
    private boolean startRow() {
        final C669Pattern pattern = file.patternAt(order);
        if (pattern == null) {
            return false;
        }
        if (row == 0) {
            speed = pattern.speed();
        }
        playRow(pattern);
        return true;
    }

    private void playRow(C669Pattern pattern) {
        for (int channel = 0; channel < VOICES; channel++) {
            final C669Voice voice = voices[channel];
            final C669Cell cell = pattern.cell(row, channel);
            voice.rowStarts();
            if (cell.hasNote()) {
                noteOn(voice, cell);
            }
            if (cell.hasVolume()) {
                voice.volume = (cell.volume() * C669Voice.FULL_VOLUME + VOLUME_ROUNDING) / VOLUME_STEPS;
            }
            if (cell.hasCommand()) {
                command(voice, cell);
            }
            if (voice.carried == PAN_SLIDE) {
                final int step = voice.carriedParameter == PAN_LEFT ? -PAN_STEP : PAN_STEP;
                voice.panning = Math.clamp(voice.panning + step, 0, C669Voice.HARD_RIGHT);
            }
        }
    }

    /**
     * A note with a tone portamento on a sounding channel only sets where the slide is heading; any other
     * note starts its sample afresh.
     */
    private void noteOn(C669Voice voice, C669Cell cell) {
        final int frequency = frequencyOf(cell.note());
        if (cell.command() == TONE_PORTAMENTO && voice.sounding) {
            voice.targetFrequency = frequency;
            return;
        }
        final C669Sample sample = file.sample(cell.instrument());
        if (sample == null) {
            voice.silence();
        } else {
            voice.start(sample, cell.instrument(), frequency);
        }
    }

    /**
     * Any effect the tracker knows ends the one the channel was carrying before it takes hold; A, B, C and G
     * then carry on themselves, the rest last only their own row or, for D, the note. The commands past H that
     * a few files carry do nothing at all, not even that.
     */
    private void command(C669Voice voice, C669Cell cell) {
        if (cell.command() > RETRIGGER) {
            return;
        }
        final int parameter = cell.parameter();
        voice.carried = C669Cell.NONE;
        switch (cell.command()) {
            case PORTAMENTO_UP, PORTAMENTO_DOWN -> carry(voice, cell, parameter != 0);
            case TONE_PORTAMENTO -> tonePortamento(voice, parameter);
            case FINETUNE -> voice.finetune = parameter * SLIDE_HERTZ;
            case VIBRATO -> voice.vibratoDepth = parameter * VIBRATO_HERTZ;
            case SPEED -> speed = parameter > 0 ? parameter : speed;
            case PAN_SLIDE -> carry(voice, cell, parameter == PAN_LEFT || parameter == PAN_RIGHT);
            case RETRIGGER -> voice.retrigEvery = file.extended() && cell.hasNote() ? parameter : 0;
            default -> {
            }
        }
    }

    private static void carry(C669Voice voice, C669Cell cell, boolean carries) {
        if (carries) {
            voice.carried = cell.command();
            voice.carriedParameter = cell.parameter();
        }
    }

    /**
     * A speed of its own is remembered and carried on to the rows after; none at all reuses the last speed for
     * this row alone.
     */
    private static void tonePortamento(C669Voice voice, int parameter) {
        if (parameter != 0) {
            voice.portamentoStep = parameter * PORTAMENTO_HERTZ;
            voice.carried = TONE_PORTAMENTO;
        } else {
            voice.portamentoThisRow = true;
        }
    }

    private void perTick() {
        for (final C669Voice voice : voices) {
            switch (voice.carried) {
                case PORTAMENTO_UP -> voice.frequency += voice.carriedParameter * SLIDE_HERTZ;
                case PORTAMENTO_DOWN -> voice.frequency = Math.max(LOWEST_FREQUENCY,
                        voice.frequency - voice.carriedParameter * SLIDE_HERTZ);
                case TONE_PORTAMENTO -> slideToTarget(voice);
                default -> {
                }
            }
            if (voice.portamentoThisRow) {
                slideToTarget(voice);
            }
            voice.vibrato = voice.vibratoDepth > 0 && voice.vibratoPosition++ % 2 == 1 ? voice.vibratoDepth : 0;
            if (voice.retrigEvery > 0 && tick > 0 && tick % voice.retrigEvery == 0) {
                voice.retrigger();
            }
        }
    }

    private static void slideToTarget(C669Voice voice) {
        if (voice.frequency < voice.targetFrequency) {
            voice.frequency = Math.min(voice.targetFrequency, voice.frequency + voice.portamentoStep);
        } else if (voice.frequency > voice.targetFrequency) {
            voice.frequency = Math.max(voice.targetFrequency, voice.frequency - voice.portamentoStep);
        }
    }

    /**
     * The row after the pattern's break row is never reached: the next pattern in the order list starts
     * instead.
     */
    private void advanceRow() {
        if (++row > file.patternAt(order).breakRow()) {
            row = 0;
            order++;
        }
    }

    private void room(int frames) {
        if (accumulator.length < frames * STEREO) {
            accumulator = new int[frames * STEREO];
        }
    }

    /**
     * One voice into the running total, stepped through its sample at the frequency it sounds, at the gain its
     * volume and its side of the stereo field ask for. The loudest frame of the chunk is kept for the scope.
     */
    private void mixIn(C669Voice voice, int at, int frames) {
        if (!voice.sounding || voice.sample == null) {
            voice.peak = 0;
            return;
        }
        final double step = soundingFrequency(voice) / (double) sampleRate;
        final int gain = voice.volume * GAIN_UNIT / C669Voice.FULL_VOLUME;
        final int right = voice.panning;
        final int leftSide = C669Voice.HARD_RIGHT - right;
        int peak = 0;
        for (int frame = 0; frame < frames && voice.sounding; frame++) {
            final int sample = voice.frameAt();
            peak = Math.max(peak, Math.abs(sample));
            if (!voice.muted) {
                final int level = sample * gain >> GAIN_BITS;
                accumulator[at + frame * STEREO] += level * leftSide / C669Voice.HARD_RIGHT;
                accumulator[at + frame * STEREO + 1] += level * right / C669Voice.HARD_RIGHT;
            }
            voice.advance(step);
        }
        voice.peak = peak;
    }

    /**
     * Every voice can reach full scale on its own, so the sum is given room for all eight before it is handed
     * out.
     */
    private void flush(short[] out, int frames) {
        for (int slot = 0; slot < frames * STEREO; slot++) {
            final int level = accumulator[slot] / VOICES;
            out[slot] = (short) Math.max(CLIP_LOW, Math.min(CLIP_HIGH, level));
        }
    }
}

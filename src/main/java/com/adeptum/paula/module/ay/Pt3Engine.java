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

import java.util.OptionalLong;

/**
 * Plays a module of Pro Tracker 3, following the player ZXTune shares between it and Vortex Tracker. A note
 * walks its sample and ornament a line an interrupt; slides, the envelope and the noise each carry an offset
 * a sample line may add to or keep; and the version the module was saved by decides several details of how
 * it sounds, from the volume table to how a glide to a note picks up where the last one left off.
 */
final class Pt3Engine implements AySource {

    private static final int NO_TARGET = -1;
    private static final int SLIDING_START_KEPT = 6;
    private static final int STALLED_GLIDE_NUDGED = 7;
    private static final int LOUDEST = 15;
    private static final int TONE_MASK = 0xfff;
    private static final int NOISE_MASK = 0x1f;
    private static final int BY_ENVELOPE = 0x10;
    private static final int TONE_A_OFF = 1;
    private static final int NOISE_A_OFF = 8;
    private static final int NOISE_REGISTER = 6;
    private static final int MIXER_REGISTER = 7;
    private static final int FIRST_VOLUME = 8;
    private static final int ENVELOPE_PERIOD_REGISTER = 11;
    private static final int SHAPE_REGISTER = 13;

    private final Pt3File file;
    private final int[] tones;
    private final long frames;
    private final Voice[] voices = new Voice[Pt3File.CHANNELS];

    private int position;
    private int line;
    private int tick;
    private int tempo;
    private int envelopeBase;
    private final Slider envelopeSlider = new Slider();
    private int noiseBase;
    private int noiseAddon;
    private int shapeWritten;

    Pt3Engine(Pt3File file) {
        this.file = file;
        this.tones = Pt3Tables.tones(file.table(), file.version());
        this.frames = countFrames();
        restart();
    }

    @Override
    public boolean nextFrame(int[] registers) {
        if (position >= file.positions().length) {
            return false;
        }
        shapeWritten = RegisterFrames.SHAPE_UNTOUCHED;
        if (tick == 0) {
            takeTheLine();
        }
        sound(registers);
        advance();
        return true;
    }

    @Override
    public void rewindTo(long frame) {
        restart();
        final int[] thrownAway = new int[RegisterFrames.REGISTERS];
        for (long at = 0; at < frame && nextFrame(thrownAway); at++) {
            continue;
        }
    }

    @Override
    public OptionalLong frames() {
        return OptionalLong.of(frames);
    }

    /**
     * The order is walked once through at the tempos its lines set, a tempo carrying on until another is set.
     */
    private long countFrames() {
        long counted = 0;
        int speed = file.tempo();
        for (final int pattern : file.positions()) {
            for (final Pt3Line walked : file.patterns()[pattern].lines()) {
                speed = walked.tempo() == Pt3Line.NO_TEMPO ? speed : walked.tempo();
                counted += speed;
            }
        }
        return counted;
    }

    private void restart() {
        position = 0;
        line = 0;
        tick = 0;
        tempo = file.tempo();
        envelopeBase = 0;
        envelopeSlider.period = 0;
        envelopeSlider.delta = 0;
        envelopeSlider.reset();
        noiseBase = 0;
        noiseAddon = 0;
        for (int channel = 0; channel < voices.length; channel++) {
            voices[channel] = new Voice();
        }
    }

    private Pt3Pattern pattern() {
        return file.patterns()[file.positions()[position]];
    }

    private void takeTheLine() {
        final Pt3Line taken = pattern().line(line);
        if (taken.tempo() != Pt3Line.NO_TEMPO) {
            tempo = taken.tempo();
        }
        if (line == 0) {
            noiseBase = 0;
        }
        for (int channel = 0; channel < voices.length; channel++) {
            voices[channel].take(taken.cell(channel));
        }
    }

    private void advance() {
        if (++tick < tempo) {
            return;
        }
        tick = 0;
        if (++line < pattern().length()) {
            return;
        }
        line = 0;
        position++;
    }

    /**
     * The mixer is worked out afresh each interrupt and so are the volumes, the noise and the envelope period;
     * a silent channel's tone is left as it stood, and the envelope shape is written only where it was named.
     */
    private void sound(int[] registers) {
        registers[MIXER_REGISTER] = 0;
        int envelopeAddon = 0;
        for (int channel = 0; channel < voices.length; channel++) {
            envelopeAddon += voices[channel].sound(registers, channel);
            voices[channel].vibrate();
        }
        registers[NOISE_REGISTER] = (noiseBase + noiseAddon) & NOISE_MASK;
        final int envelopePeriod = envelopeAddon + envelopeSlider.value + envelopeBase;
        registers[ENVELOPE_PERIOD_REGISTER] = envelopePeriod & 0xff;
        registers[ENVELOPE_PERIOD_REGISTER + 1] = envelopePeriod >> Byte.SIZE & 0xff;
        registers[SHAPE_REGISTER] = shapeWritten;
        envelopeSlider.update();
    }

    private int toneOf(int halftones) {
        return tones[Math.clamp(halftones, 0, tones.length - 1)];
    }

    /**
     * A value moved by a step every so many interrupts, as the tone and envelope slides are.
     */
    private static final class Slider {

        private int period;
        private int value;
        private int counter;
        private int delta;

        private boolean update() {
            if (counter != 0 && --counter == 0) {
                value += delta;
                counter = period;
                return true;
            }
            return false;
        }

        private void reset() {
            counter = 0;
            value = 0;
        }
    }

    private final class Voice {

        private boolean enabled;
        private boolean envelope;
        private int note;
        private int sampleNumber = 1;
        private int inSample;
        private int ornamentNumber;
        private int inOrnament;
        private int volume = LOUDEST;
        private int volumeSlide;
        private final Slider toneSlider = new Slider();
        private int target = NO_TARGET;
        private int targetDistance;
        private int toneAccumulator;
        private int envelopeSliding;
        private int noiseSliding;
        private int vibrateCounter;
        private int vibrateOn;
        private int vibrateOff;

        /**
         * A note or a rest starts the channel over; the commands are then applied in the order they were
         * read, which matters where one of them resets what another set.
         */
        private void take(Pt3Cell cell) {
            final int previousSlide = toneSlider.value;
            if (cell.enabled() != Pt3Cell.KEEP) {
                inSample = 0;
                inOrnament = 0;
                volumeSlide = 0;
                envelopeSliding = 0;
                noiseSliding = 0;
                toneSlider.reset();
                toneAccumulator = 0;
                vibrateCounter = 0;
                enabled = cell.enabled() == Pt3Cell.ON;
            }
            if (cell.note() != Pt3Cell.KEEP) {
                note = cell.note();
            }
            if (cell.sample() != Pt3Cell.KEEP) {
                sampleNumber = cell.sample();
            }
            if (cell.ornament() != Pt3Cell.KEEP) {
                ornamentNumber = cell.ornament();
                inOrnament = 0;
            }
            if (cell.volume() != Pt3Cell.KEEP) {
                volume = cell.volume();
            }
            for (final Pt3Command command : cell.commands()) {
                apply(command, previousSlide);
            }
        }

        private void apply(Pt3Command command, int previousSlide) {
            switch (command.kind()) {
                case GLISS -> {
                    toneSlider.period = command.first();
                    toneSlider.counter = command.first();
                    toneSlider.delta = command.second();
                    target = NO_TARGET;
                    vibrateCounter = 0;
                    if (toneSlider.counter == 0 && file.version() >= STALLED_GLIDE_NUDGED) {
                        toneSlider.counter++;
                    }
                }
                case GLISS_NOTE -> {
                    vibrateCounter = 0;
                    toneSlider.period = command.first();
                    toneSlider.counter = command.first();
                    toneSlider.delta = Math.abs(command.second());
                    target = command.third();
                    targetDistance = toneOf(target) - toneOf(note);
                    if (file.version() >= SLIDING_START_KEPT) {
                        toneSlider.value = previousSlide;
                    }
                    if (targetDistance - toneSlider.value < 0) {
                        toneSlider.delta = -toneSlider.delta;
                    }
                }
                case SAMPLE_OFFSET -> inSample = command.first();
                case ORNAMENT_OFFSET -> inOrnament = command.first();
                case VIBRATE -> {
                    vibrateCounter = command.first();
                    vibrateOn = command.first();
                    vibrateOff = command.second();
                    toneSlider.value = 0;
                    toneSlider.counter = 0;
                }
                case ENVELOPE_SLIDE -> {
                    envelopeSlider.period = command.first();
                    envelopeSlider.counter = command.first();
                    envelopeSlider.delta = command.second();
                }
                case ENVELOPE -> {
                    shapeWritten = command.first();
                    envelopeBase = command.second();
                    envelope = true;
                    envelopeSlider.reset();
                    inOrnament = 0;
                }
                case NO_ENVELOPE -> {
                    envelope = false;
                    inOrnament = 0;
                }
                case NOISE_BASE -> noiseBase = command.first();
            }
        }

        /**
         * Writes this channel's share of the registers and answers what it adds to the envelope period.
         */
        private int sound(int[] registers, int channel) {
            if (!enabled) {
                registers[FIRST_VOLUME + channel] = 0;
                return 0;
            }
            final Pt3Sample sample = sampleOf(sampleNumber);
            final Pt3SampleLine sounding = sample.line(inSample);
            final Pt3Ornament ornament = ornamentOf(ornamentNumber);
            final int toneAddon = sounding.toneOffset() + toneAccumulator;
            if (sounding.keepToneOffset()) {
                toneAccumulator = toneAddon;
            }
            final int tone = (toneOf(note + ornament.offset(inOrnament)) + toneSlider.value + toneAddon) & TONE_MASK;
            registers[channel * 2] = tone & 0xff;
            registers[channel * 2 + 1] = tone >> Byte.SIZE;
            if (sounding.toneOff()) {
                registers[MIXER_REGISTER] |= TONE_A_OFF << channel;
            }
            volumeSlide = Math.clamp(volumeSlide + sounding.volumeSlide(), -LOUDEST, LOUDEST);
            registers[FIRST_VOLUME + channel] =
                    Pt3Tables.level(file.version(), volume, Math.clamp(volumeSlide + sounding.level(), 0, LOUDEST))
                            | (envelope && !sounding.envelopeOff() ? BY_ENVELOPE : 0);
            int envelopeAddon = 0;
            if (sounding.noiseOff()) {
                envelopeAddon = sounding.noiseOrEnvelopeOffset() + envelopeSliding;
                if (sounding.keepNoiseOrEnvelopeOffset()) {
                    envelopeSliding = envelopeAddon;
                }
                registers[MIXER_REGISTER] |= NOISE_A_OFF << channel;
            } else {
                noiseAddon = sounding.noiseOrEnvelopeOffset() + noiseSliding;
                if (sounding.keepNoiseOrEnvelopeOffset()) {
                    noiseSliding = noiseAddon;
                }
            }
            glide();
            if (++inSample >= sample.lines().length) {
                inSample = sample.loop();
            }
            if (++inOrnament >= ornament.offsets().length) {
                inOrnament = ornament.loop();
            }
            return envelopeAddon;
        }

        /**
         * A glide to a note stops on it once the slide has come as far as the distance between the two notes.
         */
        private void glide() {
            if (!toneSlider.update() || target == NO_TARGET) {
                return;
            }
            if ((toneSlider.delta < 0 && toneSlider.value <= targetDistance)
                    || (toneSlider.delta >= 0 && toneSlider.value >= targetDistance)) {
                note = target;
                toneSlider.value = 0;
                toneSlider.counter = 0;
            }
        }

        /**
         * A vibrato here switches the whole channel on and off, holding each state for its own count.
         */
        private void vibrate() {
            if (vibrateCounter > 0 && --vibrateCounter == 0) {
                enabled = !enabled;
                vibrateCounter = enabled ? vibrateOn : vibrateOff;
            }
        }

        private Pt3Sample sampleOf(int number) {
            final Pt3Sample found = number < file.samples().length ? file.samples()[number] : null;
            return found == null ? Pt3Sample.EMPTY : found;
        }

        private Pt3Ornament ornamentOf(int number) {
            final Pt3Ornament found = number < file.ornaments().length ? file.ornaments()[number] : null;
            return found == null ? Pt3Ornament.EMPTY : found;
        }
    }
}

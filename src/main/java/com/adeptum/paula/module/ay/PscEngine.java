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
 * Plays a module of Pro Sound Creator, working out what the interrupt would have written to the chip, as
 * ZXTune's player does. A note walks its sample and ornament a line an interrupt; each sample line adds to the
 * tone of the ones before it and nudges the volume, and each ornament line moves the note on from where the
 * last left it.
 */
final class PscEngine implements AySource {

    /**
     * The periods of the ASM table Pro Sound Creator looked its notes up in, as far as its notes reach.
     */
    static final int[] TONES = {
            0xedc, 0xe07, 0xd3e, 0xc80, 0xbcc, 0xb22, 0xa82, 0x9ec, 0x95c, 0x8d6, 0x858, 0x7e0,
            0x76e, 0x704, 0x69f, 0x640, 0x5e6, 0x591, 0x541, 0x4f6, 0x4ae, 0x46b, 0x42c, 0x3f0,
            0x3b7, 0x382, 0x34f, 0x320, 0x2f3, 0x2c8, 0x2a1, 0x27b, 0x257, 0x236, 0x216, 0x1f8,
            0x1dc, 0x1c1, 0x1a8, 0x190, 0x179, 0x164, 0x150, 0x13d, 0x12c, 0x11b, 0x10b, 0x0fc,
            0x0ee, 0x0e0, 0x0d4, 0x0c8, 0x0bd, 0x0b2, 0x0a8, 0x09f, 0x096, 0x08d, 0x085, 0x07e,
            0x077, 0x070, 0x06a, 0x064, 0x05e, 0x059, 0x054, 0x050, 0x04b, 0x047, 0x043, 0x03f,
            0x03c, 0x038, 0x035, 0x032, 0x02f, 0x02d, 0x02a, 0x028, 0x026, 0x024, 0x022, 0x020,
            0x01e, 0x01c
    };

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

    private final PscFile file;
    private final long frames;
    private final Voice[] voices = new Voice[PscFile.CHANNELS];

    private int position;
    private int line;
    private int tick;
    private int tempo;
    private int envelopeTone;
    private int noiseBase;

    PscEngine(PscFile file) {
        this.file = file;
        this.frames = countFrames();
        restart();
    }

    @Override
    public boolean nextFrame(int[] registers) {
        if (position >= file.positions().length) {
            return false;
        }
        registers[SHAPE_REGISTER] = RegisterFrames.SHAPE_UNTOUCHED;
        if (tick == 0) {
            takeTheLine(registers);
        }
        registers[MIXER_REGISTER] = 0;
        for (int channel = 0; channel < voices.length; channel++) {
            voices[channel].sound(registers, channel);
        }
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
     * The order is walked once through at the tempos its lines set, a tempo carrying on until another is set;
     * a line always lasts at least the one interrupt.
     */
    private long countFrames() {
        long counted = 0;
        int speed = file.tempo();
        for (final int pattern : file.positions()) {
            for (final PscLine walked : file.patterns()[pattern].lines()) {
                speed = walked.tempo() == PscLine.NO_TEMPO ? speed : walked.tempo();
                counted += Math.max(speed, 1);
            }
        }
        return counted;
    }

    private void restart() {
        position = 0;
        line = 0;
        tick = 0;
        tempo = file.tempo();
        envelopeTone = 0;
        noiseBase = 0;
        for (int channel = 0; channel < voices.length; channel++) {
            voices[channel] = new Voice();
        }
    }

    private PscPattern pattern() {
        return file.patterns()[file.positions()[position]];
    }

    /**
     * After the channels have taken their cells, each channel's noise moves on by the noise base.
     */
    private void takeTheLine(int[] registers) {
        final PscLine taken = pattern().line(line);
        if (taken.tempo() != PscLine.NO_TEMPO) {
            tempo = taken.tempo();
        }
        for (int channel = 0; channel < voices.length; channel++) {
            voices[channel].take(taken.cell(channel), registers);
        }
        for (final Voice voice : voices) {
            voice.noise += noiseBase;
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

    private static void writeEnvelopeTone(int[] registers, int tone) {
        registers[ENVELOPE_PERIOD_REGISTER] = tone & 0xff;
        registers[ENVELOPE_PERIOD_REGISTER + 1] = tone >> Byte.SIZE & 0xff;
    }

    /**
     * A walk through the lines of a sample or an ornament. A loop is taken back to where it was last seen to
     * begin, unless the walk was told to break out of it, and the walk stops once past the last line. Only a
     * note starts it again: naming another sample or ornament carries on from where the walk stands.
     */
    private static final class Walk<T extends PscLoopingLine> {

        private T[] lines;
        private boolean walking;
        private int at;
        private int loop;
        private boolean breakLoop;

        private Walk(T[] lines) {
            this.lines = lines;
        }

        private void restart() {
            walking = true;
            at = 0;
        }

        private T line() {
            return walking && at < lines.length ? lines[at] : null;
        }

        private void next() {
            final T current = lines[at];
            if (current.loopBegin()) {
                loop = at;
            }
            if (current.loopEnd() && !breakLoop) {
                at = loop;
                return;
            }
            breakLoop &= !current.loopEnd();
            walking = ++at < lines.length;
        }
    }

    private final class Voice {

        private final Walk<PscSampleLine> sample = new Walk<>(file.samples()[0].lines());
        private final Walk<PscOrnamentLine> ornament = new Walk<>(file.ornaments()[0].lines());
        private boolean envelope;
        private int note;
        private short toneSum;
        private int tone;
        private int volume;
        private int attenuation;
        private int noise;
        private boolean sliding;
        private boolean towardNote;
        private short slid;
        private short slideStep;
        private int volumeCounter;
        private int volumePeriod;
        private int volumeStep;

        /**
         * A note starts the sample and ornament over and clears what the last note built up, a rest stops them;
         * the commands are then applied in the order they were read, except that a note or a rest leaves no
         * loop to break out of.
         */
        private void take(PscCell cell, int[] registers) {
            if (cell.enabled() == PscCell.ON) {
                sample.restart();
                ornament.restart();
                volumeCounter = 0;
                slid = 0;
                toneSum = 0;
                noise = 0;
                attenuation = 0;
            } else if (cell.enabled() == PscCell.OFF) {
                sample.walking = false;
                ornament.walking = false;
            }
            if (cell.enabled() != PscCell.KEEP) {
                sliding = false;
            }
            if (cell.note() != PscCell.KEEP) {
                note = cell.note();
            }
            if (cell.sample() != PscCell.KEEP) {
                sample.lines = file.samples()[cell.sample()].lines();
            }
            if (cell.ornament() != PscCell.KEEP) {
                ornament.lines = file.ornaments()[cell.ornament()].lines();
            }
            if (cell.volume() != PscCell.KEEP) {
                volume = cell.volume();
                attenuation = 0;
            }
            for (final PscCommand command : cell.commands()) {
                apply(command, registers);
            }
            if (cell.enabled() != PscCell.KEEP) {
                sample.breakLoop = false;
                ornament.breakLoop = false;
            }
        }

        private void apply(PscCommand command, int[] registers) {
            switch (command.kind()) {
                case BREAK_SAMPLE -> sample.breakLoop = true;
                case BREAK_ORNAMENT -> ornament.breakLoop = true;
                case NO_ORNAMENT -> ornament.walking = false;
                case ENVELOPE -> {
                    registers[SHAPE_REGISTER] = command.first();
                    envelopeTone = command.second();
                    writeEnvelopeTone(registers, envelopeTone);
                }
                case ENVELOPE_ON -> envelope = true;
                case NO_ENVELOPE -> envelope = false;
                case NOISE_BASE -> noiseBase = command.first();
                case GLISS -> {
                    slid = (short) (tone - TONES[note]);
                    slideStep = (short) (slid >= 0 ? -command.first() : command.first());
                    sliding = true;
                    towardNote = true;
                }
                case SLIDE -> {
                    slideStep = (short) command.first();
                    sliding = true;
                    towardNote = false;
                }
                case VOLUME_SLIDE -> {
                    volumePeriod = command.first();
                    volumeCounter = volumePeriod;
                    volumeStep = command.second();
                }
            }
        }

        /**
         * A channel whose sample has run out is silent and leaves the rest of the chip alone. Otherwise the
         * envelope, where the sample line lets it sound and masks the noise, takes what the line adds in place
         * of the noise.
         */
        private void sound(int[] registers, int channel) {
            final PscSampleLine sounding = sample.line();
            if (sounding == null) {
                registers[FIRST_VOLUME + channel] = 0;
                return;
            }
            sample.next();
            final PscOrnamentLine moving = ornament.line();
            if (moving != null) {
                noise += moving.noise();
                note = wrapped(note + moving.halftones());
                ornament.next();
            }
            toneSum += (short) sounding.tone();
            tone = (TONES[note] + toneSum + slide()) & TONE_MASK;
            registers[channel * 2] = tone & 0xff;
            registers[channel * 2 + 1] = tone >> Byte.SIZE;
            if (sounding.toneOff()) {
                registers[MIXER_REGISTER] |= TONE_A_OFF << channel;
            }
            attenuation += sounding.volumeStep() + volumeSlide();
            final int level = Math.clamp(attenuation + volume, 0, LOUDEST);
            attenuation = level - volume;
            final boolean enveloped = envelope && sounding.envelope();
            registers[FIRST_VOLUME + channel] = (level + 1) * sounding.level() >> 4 | (enveloped ? BY_ENVELOPE : 0);
            if (enveloped && sounding.noiseOff()) {
                envelopeTone += sounding.adding();
                writeEnvelopeTone(registers, envelopeTone);
            } else {
                noise += sounding.adding();
                if (!sounding.noiseOff()) {
                    registers[NOISE_REGISTER] = noise & NOISE_MASK;
                }
            }
            if (sounding.noiseOff()) {
                registers[MIXER_REGISTER] |= NOISE_A_OFF << channel;
            }
        }

        /**
         * A note moved below the lowest or above the highest comes round from the other end, once.
         */
        private static int wrapped(int moved) {
            final int notes = TONES.length;
            final int round = moved < 0 ? moved + notes : moved >= notes ? moved - notes : moved;
            return Math.clamp(round, 0, notes - 1);
        }

        /**
         * A glissando starts from the tone last sounded and stops once its slide has come round to the note,
         * sounding the step that got it there; a slide carries on until a note or a rest.
         */
        private int slide() {
            if (!sliding) {
                return 0;
            }
            slid += slideStep;
            sliding = !(towardNote && (slid < 0 ? slideStep <= 0 : slideStep >= 0));
            return slid;
        }

        private int volumeSlide() {
            if (volumeCounter != 0 && --volumeCounter == 0) {
                volumeCounter = volumePeriod;
                return volumeStep;
            }
            return 0;
        }
    }
}

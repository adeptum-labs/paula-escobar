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
 * Plays a module of Pro Tracker 2, working out what the interrupt would have written to the chip, as ZXTune's
 * player does. A note walks its sample and ornament a line an interrupt, the sample bending the tone and
 * setting the level and noise, while a glissando slides the tone and may stop on a note it was sent to.
 */
final class Pt2Engine implements AySource {

    /**
     * The periods Pro Tracker 2 looked its notes up in, eight octaves of them.
     */
    static final int[] TONES = {
            0xef8, 0xe10, 0xd60, 0xc80, 0xbd8, 0xb28, 0xa88, 0x9f0, 0x960, 0x8e0, 0x858, 0x7e0,
            0x77c, 0x708, 0x6b0, 0x640, 0x5ec, 0x594, 0x544, 0x4f8, 0x4b0, 0x470, 0x42c, 0x3fd,
            0x3be, 0x384, 0x358, 0x320, 0x2f6, 0x2ca, 0x2a2, 0x27c, 0x258, 0x238, 0x216, 0x1f8,
            0x1df, 0x1c2, 0x1ac, 0x190, 0x17b, 0x165, 0x151, 0x13e, 0x12c, 0x11c, 0x10a, 0x0fc,
            0x0ef, 0x0e1, 0x0d6, 0x0c8, 0x0bd, 0x0b2, 0x0a8, 0x09f, 0x096, 0x08e, 0x085, 0x07e,
            0x077, 0x070, 0x06b, 0x064, 0x05e, 0x059, 0x054, 0x04f, 0x04b, 0x047, 0x042, 0x03f,
            0x03b, 0x038, 0x035, 0x032, 0x02f, 0x02c, 0x02a, 0x027, 0x025, 0x023, 0x021, 0x01f,
            0x01d, 0x01c, 0x01a, 0x019, 0x017, 0x016, 0x015, 0x013, 0x012, 0x011, 0x010, 0x00f
    };

    private static final int NO_TARGET = -1;
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
    private static final int DEFAULT_SAMPLE = 1;

    private final Pt2File file;
    private final long frames;
    private final Voice[] voices = new Voice[Pt2File.CHANNELS];

    private int position;
    private int line;
    private int tick;
    private int tempo;

    Pt2Engine(Pt2File file) {
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
     * The order is walked once through at the tempos its lines set, a tempo carrying on until another is set;
     * a line always lasts at least the one interrupt.
     */
    private long countFrames() {
        long counted = 0;
        int speed = file.tempo();
        for (final int pattern : file.positions()) {
            for (final Pt2Line walked : file.patterns()[pattern].lines()) {
                speed = walked.tempo() == Pt2Line.NO_TEMPO ? speed : walked.tempo();
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
        for (int channel = 0; channel < voices.length; channel++) {
            voices[channel] = new Voice();
        }
    }

    private Pt2Pattern pattern() {
        return file.patterns()[file.positions()[position]];
    }

    private void takeTheLine(int[] registers) {
        final Pt2Line taken = pattern().line(line);
        if (taken.tempo() != Pt2Line.NO_TEMPO) {
            tempo = taken.tempo();
        }
        for (int channel = 0; channel < voices.length; channel++) {
            voices[channel].take(taken.cell(channel), registers);
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
     * The mixer and the volumes are worked out afresh each interrupt; a silent channel's tone is left as it
     * stood, and the noise is written only by a channel whose sample line sounds it.
     */
    private void sound(int[] registers) {
        registers[MIXER_REGISTER] = 0;
        for (int channel = 0; channel < voices.length; channel++) {
            voices[channel].sound(registers, channel);
        }
    }

    private static int toneOf(int halftones) {
        return TONES[Math.clamp(halftones, 0, TONES.length - 1)];
    }

    private final class Voice {

        private boolean enabled;
        private boolean envelope;
        private int note;
        private int sampleNumber = DEFAULT_SAMPLE;
        private int inSample;
        private int ornamentNumber;
        private int inOrnament;
        private int volume = LOUDEST;
        private int noiseAddon;
        private int sliding;
        private int target = NO_TARGET;
        private int glissade;

        /**
         * A note or a rest starts the channel's sample and ornament over; the commands are then applied in the
         * order they were read.
         */
        private void take(Pt2Cell cell, int[] registers) {
            if (cell.enabled() != Pt2Cell.KEEP) {
                enabled = cell.enabled() == Pt2Cell.ON;
                if (!enabled) {
                    stopGliding();
                }
                inSample = 0;
                inOrnament = 0;
            }
            if (cell.note() != Pt2Cell.KEEP) {
                note = cell.note();
                stopGliding();
            }
            if (cell.sample() != Pt2Cell.KEEP) {
                sampleNumber = cell.sample();
                inSample = 0;
            }
            if (cell.ornament() != Pt2Cell.KEEP) {
                ornamentNumber = cell.ornament();
                inOrnament = 0;
            }
            if (cell.volume() != Pt2Cell.KEEP) {
                volume = cell.volume();
            }
            for (final Pt2Command command : cell.commands()) {
                apply(command, registers);
            }
        }

        private void stopGliding() {
            sliding = 0;
            glissade = 0;
            target = NO_TARGET;
        }

        private void apply(Pt2Command command, int[] registers) {
            switch (command.kind()) {
                case ENVELOPE -> {
                    registers[SHAPE_REGISTER] = command.first();
                    registers[ENVELOPE_PERIOD_REGISTER] = command.second() & 0xff;
                    registers[ENVELOPE_PERIOD_REGISTER + 1] = command.second() >> Byte.SIZE & 0xff;
                    envelope = true;
                }
                case NO_ENVELOPE -> envelope = false;
                case NOISE_ADD -> noiseAddon = command.first();
                case GLISS_NOTE -> {
                    sliding = 0;
                    glissade = command.first();
                    target = command.second();
                }
                case GLISS -> {
                    glissade = command.first();
                    target = NO_TARGET;
                }
                case NO_GLISS -> glissade = 0;
            }
        }

        private void sound(int[] registers, int channel) {
            if (!enabled) {
                registers[FIRST_VOLUME + channel] = 0;
                return;
            }
            final Pt2Sample sample = file.samples()[sampleNumber];
            final Pt2SampleLine sounding = sample.line(inSample);
            final Pt2Ornament ornament = file.ornaments()[ornamentNumber];
            final int tone = (toneOf(note + ornament.offset(inOrnament)) + sliding + sounding.vibrato()) & TONE_MASK;
            registers[channel * 2] = tone & 0xff;
            registers[channel * 2 + 1] = tone >> Byte.SIZE;
            if (sounding.toneOff()) {
                registers[MIXER_REGISTER] |= TONE_A_OFF << channel;
            }
            registers[FIRST_VOLUME + channel] =
                    Math.clamp(level(volume, sounding.level()), 0, LOUDEST) | (envelope ? BY_ENVELOPE : 0);
            if (sounding.noiseOff()) {
                registers[MIXER_REGISTER] |= NOISE_A_OFF << channel;
            } else {
                registers[NOISE_REGISTER] = (sounding.noise() + noiseAddon) & NOISE_MASK;
            }
            glide();
            if (++inSample >= sample.lines().length) {
                inSample = sample.loop();
            }
            if (++inOrnament >= ornament.offsets().length) {
                inOrnament = ornament.loop();
            }
        }

        /**
         * A glissando to a note stops on it once the next step would carry the slide past the distance between
         * the two notes.
         */
        private void glide() {
            if (target != NO_TARGET) {
                final int remaining = toneOf(target) - toneOf(note) - (sliding + glissade);
                if ((glissade > 0 && remaining <= 0) || (glissade < 0 && remaining >= 0)) {
                    note = target;
                    stopGliding();
                }
            }
            sliding += glissade;
        }

        /**
         * The player's own sum for scaling a sample level by the channel volume.
         */
        private static int level(int volume, int level) {
            return (volume * 17 + (volume > 7 ? 1 : 0)) * level / 256;
        }
    }
}

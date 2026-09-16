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
 * Plays a module of ST Song Compiler, working out what the interrupt would have written to the chip. A note
 * is not a pitch held until the next one: it starts a walk through its sample, a line of which says how loud
 * the channel is, which generators are heard and how far the note is bent, while the ornament shifts it by
 * whole semitones.
 */
final class StcEngine implements AySource {

    /**
     * The periods SoundTracker looked its notes up in, eight octaves of them.
     */
    static final int[] TONES = {
            0xef8, 0xe10, 0xd60, 0xc80, 0xbd8, 0xb28, 0xa88, 0x9f0, 0x960, 0x8e0, 0x858, 0x7e0,
            0x77c, 0x708, 0x6b0, 0x640, 0x5ec, 0x594, 0x544, 0x4f8, 0x4b0, 0x470, 0x42c, 0x3f0,
            0x3be, 0x384, 0x358, 0x320, 0x2f6, 0x2ca, 0x2a2, 0x27c, 0x258, 0x238, 0x216, 0x1f8,
            0x1df, 0x1c2, 0x1ac, 0x190, 0x17b, 0x165, 0x151, 0x13e, 0x12c, 0x11c, 0x10b, 0x0fc,
            0x0ef, 0x0e1, 0x0d6, 0x0c8, 0x0bd, 0x0b2, 0x0a8, 0x09f, 0x096, 0x08e, 0x085, 0x07e,
            0x077, 0x070, 0x06b, 0x064, 0x05e, 0x059, 0x054, 0x04f, 0x04b, 0x047, 0x042, 0x03f,
            0x03b, 0x038, 0x035, 0x032, 0x02f, 0x02c, 0x02a, 0x027, 0x025, 0x023, 0x021, 0x01f,
            0x01d, 0x01c, 0x01a, 0x019, 0x017, 0x016, 0x015, 0x013, 0x012, 0x011, 0x010, 0x00f
    };

    private static final int SAMPLE_STEPS = 32;
    private static final int TONE_MASK = 0xfff;
    private static final int LOUDEST = 15;
    private static final int BY_ENVELOPE = 0x10;
    private static final int TONE_A_OFF = 1;
    private static final int NOISE_A_OFF = 8;
    private static final int NOISE_MASK = 0x1f;
    private static final int SHAPE_REGISTER = 13;
    private static final int NOISE_REGISTER = 6;
    private static final int MIXER_REGISTER = 7;
    private static final int FIRST_VOLUME = 8;
    private static final int ENVELOPE_PERIOD_REGISTER = 11;

    private static final StcSample SILENT = silentSample();
    private static final int[] FLAT = new int[StcFile.ORNAMENT_LENGTH];

    private final StcFile file;
    private final Voice[] voices = {new Voice(), new Voice(), new Voice()};

    private int position;
    private int line;
    private int tick;
    private int envelopeType;
    private int envelopeTone;
    private long played;

    StcEngine(StcFile file) {
        this.file = file;
    }

    @Override
    public boolean nextFrame(int[] registers) {
        if (position >= file.positions().length) {
            return false;
        }
        if (tick == 0) {
            takeTheLine();
        }
        sound(registers);
        advance();
        played++;
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

    /**
     * The order is walked once through; nothing in the file says where it would loop back to.
     */
    @Override
    public OptionalLong frames() {
        long lines = 0;
        for (final StcPosition step : file.positions()) {
            lines += lengthOf(step);
        }
        return OptionalLong.of(lines * file.tempo());
    }

    private void restart() {
        position = 0;
        line = 0;
        tick = 0;
        envelopeType = 0;
        envelopeTone = 0;
        played = 0;
        for (final Voice voice : voices) {
            voice.reset();
        }
    }

    private int lengthOf(StcPosition step) {
        final StcPattern pattern = file.patterns()[step.pattern()];
        return pattern == null ? 0 : pattern.length();
    }

    private void takeTheLine() {
        final StcPattern pattern = file.patterns()[file.positions()[position].pattern()];
        if (pattern == null || line >= pattern.length()) {
            return;
        }
        for (int channel = 0; channel < StcFile.CHANNELS; channel++) {
            voices[channel].take(pattern.cell(line, channel));
        }
    }

    private void advance() {
        for (final Voice voice : voices) {
            voice.iterate();
        }
        if (++tick < file.tempo()) {
            return;
        }
        tick = 0;
        if (++line < lengthOf(file.positions()[position])) {
            return;
        }
        line = 0;
        position++;
    }

    /**
     * Every register is worked out afresh each interrupt, the mixer starting with everything heard and each
     * channel turning off what its sample line does not ask for.
     */
    private void sound(int[] registers) {
        registers[SHAPE_REGISTER] = RegisterFrames.SHAPE_UNTOUCHED;
        registers[MIXER_REGISTER] = 0;
        final int transposition = file.positions()[position].transposition();
        for (int channel = 0; channel < StcFile.CHANNELS; channel++) {
            voices[channel].sound(registers, channel, transposition);
        }
        if (envelopeType != 0) {
            registers[SHAPE_REGISTER] = envelopeType;
            registers[ENVELOPE_PERIOD_REGISTER] = envelopeTone & 0xff;
            registers[ENVELOPE_PERIOD_REGISTER + 1] = envelopeTone >> Byte.SIZE & 0xff;
        }
    }

    private static StcSample silentSample() {
        final StcSampleLine[] lines = new StcSampleLine[StcFile.SAMPLE_LENGTH];
        java.util.Arrays.fill(lines, new StcSampleLine(0, 0, true, true, 0));
        return new StcSample(lines, 0, 0);
    }

    /**
     * One channel's walk through its sample. The count runs down from the whole length of a sample and the
     * walk ends where it reaches nothing to loop back to, which is how a note dies away on its own.
     */
    private final class Voice {

        private int note;
        private int countDown = -1;
        private int at;
        private StcSample sample = SILENT;
        private int[] ornament = FLAT;
        private int envelope;

        private void reset() {
            note = 0;
            countDown = -1;
            at = 0;
            sample = SILENT;
            ornament = FLAT;
            envelope = 0;
        }

        private void take(StcCell cell) {
            if (cell.note() != StcCell.NONE) {
                countDown = cell.note() == StcCell.REST ? -1 : SAMPLE_STEPS;
            }
            if (cell.note() >= 0) {
                note = cell.note();
                at = 0;
            }
            if (cell.sample() != StcCell.NONE) {
                sample = sampleOf(cell.sample());
            }
            if (cell.ornament() != StcCell.NONE) {
                ornament = ornamentOf(cell.ornament());
            }
            if (cell.envelope() == StcCell.NO_ENVELOPE) {
                envelope = 0;
            } else if (cell.envelope() != StcCell.NONE) {
                envelopeType = cell.envelope();
                envelopeTone = cell.envelopePeriod();
                envelope = 1;
            }
        }

        /**
         * The walk is stepped on a copy to see whether the note has anywhere left to go: where it has not,
         * the channel falls silent, and where it has, the line it lands on is the one that sounds.
         */
        private void sound(int[] registers, int channel, int transposition) {
            final int steps = countDown;
            final int position = at;
            step();
            final boolean alive = countDown >= 0;
            final int sounding = (at - 1) & (SAMPLE_STEPS - 1);
            countDown = steps;
            at = position;
            if (!alive) {
                registers[FIRST_VOLUME + channel] = 0;
                return;
            }
            final StcSampleLine sounded = sample.lines()[sounding];
            registers[FIRST_VOLUME + channel] = Math.clamp(sounded.level(), 0, LOUDEST)
                    | (envelope != 0 ? BY_ENVELOPE : 0);
            final int halftones = Math.clamp(note + ornament[sounding] + transposition, 0, TONES.length - 1);
            final int tone = (TONES[halftones] + sounded.effect()) & TONE_MASK;
            registers[channel * 2] = tone & 0xff;
            registers[channel * 2 + 1] = tone >> Byte.SIZE;
            if (sounded.toneOff()) {
                registers[MIXER_REGISTER] |= TONE_A_OFF << channel;
            }
            if (sounded.noiseOff()) {
                registers[MIXER_REGISTER] |= NOISE_A_OFF << channel;
            } else {
                registers[NOISE_REGISTER] = sounded.noise() & NOISE_MASK;
            }
        }

        private void iterate() {
            step();
            if (countDown < 0) {
                return;
            }
            if (envelope == 2) {
                envelopeType = 0;
            } else if (envelope == 1) {
                envelope = 2;
                envelopeType = 0;
            }
        }

        private void step() {
            if (countDown < 0) {
                return;
            }
            countDown--;
            at = (at + 1) & (SAMPLE_STEPS - 1);
            if (countDown != 0) {
                return;
            }
            if (sample.loop() != 0) {
                at = sample.loop() & (SAMPLE_STEPS - 1);
                countDown = sample.loopLimit() - sample.loop() + 1;
            } else {
                countDown = -1;
            }
        }

        private StcSample sampleOf(int number) {
            final StcSample found = file.samples()[number & 0xf];
            return found == null ? SILENT : found;
        }

        private int[] ornamentOf(int number) {
            final int[] found = file.ornaments()[number & 0xf];
            return found == null ? FLAT : found;
        }
    }
}

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
 * The commands and how they are meant follow Load_mo3.cpp of OpenMPT,
 * Copyright © 2004-2026 the OpenMPT project developers and Copyright ©
 * 1997-2003 Olivier Lapicque, licensed under the three-clause BSD licence
 * and used here under the GNU General Public License.
 */

package com.adeptum.paula.module.mo3;

/**
 * Turns the commands of an MO3 back into the effects the tracker that wrote the module used.
 *
 * <p>An MO3 numbers its commands its own way, one numbering covering all five trackers, so a command says both
 * what to do and which trackers ever ask for it. Going back means picking the effect that tracker wrote: the
 * letters A to Z for Impulse Tracker and Scream Tracker, the effect numbers for ProTracker, Fast Tracker and
 * MultiTracker. A handful belong in the volume column rather than the effect column, and a handful mean
 * something different in the two families, which is all this holds beyond the two tables.</p>
 */
final class Mo3Commands {

    static final int NOTE = 0x01;
    static final int INSTRUMENT = 0x02;

    private static final int TONE_PORTAMENTO = 0x06;
    private static final int VIBRATO = 0x07;
    private static final int PANNING = 0x0B;
    private static final int VOLUME = 0x0F;
    private static final int PATTERN_BREAK = 0x10;
    private static final int VOLUME_SLIDE_COARSE = 0x14;
    private static final int VOLUME_SLIDE_FINE = 0x15;
    private static final int PANNING_SLIDE_COLUMN = 0x1B;
    private static final int EXTRA_FINE_PORTAMENTO_UP = 0x1D;
    private static final int EXTRA_FINE_PORTAMENTO_DOWN = 0x1E;
    private static final int VIBRATO_SPEED_COLUMN = 0x1F;
    private static final int VIBRATO_DEPTH_COLUMN = 0x20;
    private static final int VOLUME_SLIDE = 0x22;
    private static final int VOLUME_SLIDE_COLUMN = 0x30;
    private static final int PITCH_DOWN_COLUMN = 0x31;
    private static final int PITCH_UP_COLUMN = 0x32;
    private static final int OTHER_VOLUME_COLUMN = 0x34;

    private static final int COMMANDS = 0x3A;

    /**
     * The five effects OpenMPT added past the letters Impulse Tracker used, which its own modules may carry.
     */
    private static final int PARAMETER_EXTENSION = 0x1B;
    private static final int SMOOTH_MIDI = 0x1C;
    private static final int DELAY_CUT = 0x1D;
    private static final int FINE_TUNE = 0x1E;
    private static final int FINE_TUNE_SMOOTH = 0x1F;

    /**
     * The effects Fast Tracker gave letters to carry on from the sixteen it numbered, and two more sit past
     * the end of the alphabet.
     */
    private static final int XM_FIRST_LETTER = 0x10;
    private static final int XM_SMOOTH_MIDI = 0x24;
    private static final int XM_PARAMETER_EXTENSION = 0x26;

    /**
     * The ten volume-column portamento values Impulse Tracker allows, which an MO3 writes as the value itself
     * rather than as the place it holds in this table.
     */
    private static final int[] IT_PORTAMENTO_VALUES = {0x00, 0x01, 0x04, 0x08, 0x10, 0x20, 0x40, 0x60, 0x80, 0xFF};

    private static final int NOTES = 120;
    private static final int KEY_OFF = 0xFF;
    private static final int NOTE_CUT = 0xFE;

    private static final int LOUDEST = 64;
    private static final int PANNING_RIGHT = 0xFF;
    private static final int HIGH_NIBBLE = 0xF0;
    private static final int LOW_NIBBLE = 0x0F;
    private static final int NIBBLE = 4;

    /**
     * The volume column holds ten steps of each of the slides and portamentos it can carry.
     */
    private static final int COLUMN_STEPS = 10;

    private static final int TENS = 10;
    private static final int IT_PANNING_STEPS = 4;
    private static final int FIRST_COLUMN_OFFSET = 223;
    private static final int LAST_COLUMN_OFFSET = 232;
    private static final int EXTRA_FINE_UP = 0x10;
    private static final int EXTRA_FINE_DOWN = 0x20;

    private static final int[] SCREAM_TRACKER_EFFECTS = screamTrackerEffects();
    private static final int[] PROTRACKER_EFFECTS = protrackerEffects();

    private Mo3Commands() {
    }

    static void apply(Mo3Event event, int command, int value, Mo3Kind kind) {
        final boolean screamTracker = kind.isScreamTrackerFamily();
        switch (command) {
            case NOTE -> event.note(note(value, kind));
            case INSTRUMENT -> event.instrument(value + 1);
            case TONE_PORTAMENTO -> tonePortamento(event, value, kind);
            case VIBRATO -> vibrato(event, value, kind);
            case PANNING -> panning(event, value, kind);
            case VOLUME -> volume(event, value, kind);
            case PATTERN_BREAK -> patternBreak(event, value, kind);
            case VOLUME_SLIDE_COARSE, VOLUME_SLIDE_FINE -> columnVolumeSlide(event, command, value);
            case PANNING_SLIDE_COLUMN -> columnPanningSlide(event, value);
            case EXTRA_FINE_PORTAMENTO_UP -> event.effect(effect(command, screamTracker), EXTRA_FINE_UP | value);
            case EXTRA_FINE_PORTAMENTO_DOWN -> event.effect(effect(command, screamTracker), EXTRA_FINE_DOWN | value);
            case VIBRATO_SPEED_COLUMN -> event.volumeEffect(Mo3Event.VOLUME_COLUMN_VIBRATO_SPEED, value);
            case VIBRATO_DEPTH_COLUMN -> event.volumeEffect(Mo3Event.VOLUME_COLUMN_VIBRATO_DEPTH, value);
            case VOLUME_SLIDE -> volumeSlide(event, value);
            case VOLUME_SLIDE_COLUMN -> columnVolumeSlideByTens(event, value);
            case PITCH_DOWN_COLUMN -> event.volumeEffect(Mo3Event.VOLUME_COLUMN_PITCH_DOWN, value);
            case PITCH_UP_COLUMN -> event.volumeEffect(Mo3Event.VOLUME_COLUMN_PITCH_UP, value);
            case OTHER_VOLUME_COLUMN -> columnOffset(event, value);
            default -> event.effect(effect(command, screamTracker), value);
        }
    }

    /**
     * The note itself, counted from the first the format holds. Impulse Tracker and the ProTracker family
     * happen to land on the same note for the same value; MultiTracker sits a semitone above them.
     */
    private static int note(int value, Mo3Kind kind) {
        if (value < NOTES) {
            return value + (kind == Mo3Kind.MULTITRACKER ? 2 : 1);
        }
        return switch (value) {
            case KEY_OFF -> Mo3Event.KEY_OFF;
            case NOTE_CUT -> Mo3Event.NOTE_CUT;
            default -> Mo3Event.NOTE_FADE;
        };
    }

    /**
     * Impulse Tracker and Fast Tracker both hold a tone portamento in the volume column when it is coarse
     * enough to fit there, which is what an MO3 counts on rather than writing the column command itself.
     */
    private static void tonePortamento(Mo3Event event, int value, Mo3Kind kind) {
        if (!event.hasVolumeEffect() && kind == Mo3Kind.FAST_TRACKER && (value & LOW_NIBBLE) == 0) {
            event.volumeEffect(Mo3Event.VOLUME_COLUMN_TONE_PORTAMENTO, value >> NIBBLE);
            return;
        }
        if (!event.hasVolumeEffect() && kind == Mo3Kind.IMPULSE_TRACKER) {
            for (int step = 0; step < IT_PORTAMENTO_VALUES.length; step++) {
                if (IT_PORTAMENTO_VALUES[step] == value) {
                    event.volumeEffect(Mo3Event.VOLUME_COLUMN_TONE_PORTAMENTO, step);
                    return;
                }
            }
        }
        event.effect(effect(TONE_PORTAMENTO, kind.isScreamTrackerFamily()), value);
    }

    private static void vibrato(Mo3Event event, int value, Mo3Kind kind) {
        if (!event.hasVolumeEffect() && value < COLUMN_STEPS && kind == Mo3Kind.IMPULSE_TRACKER) {
            event.volumeEffect(Mo3Event.VOLUME_COLUMN_VIBRATO_DEPTH, value);
        } else {
            event.effect(effect(VIBRATO, kind.isScreamTrackerFamily()), value);
        }
    }

    private static void panning(Mo3Event event, int value, Mo3Kind kind) {
        if (!event.hasVolumeEffect()) {
            if (kind == Mo3Kind.IMPULSE_TRACKER && value == PANNING_RIGHT) {
                event.volumeEffect(Mo3Event.VOLUME_COLUMN_PANNING, LOUDEST);
                return;
            }
            if (kind == Mo3Kind.IMPULSE_TRACKER && value % IT_PANNING_STEPS == 0) {
                event.volumeEffect(Mo3Event.VOLUME_COLUMN_PANNING, value / IT_PANNING_STEPS);
                return;
            }
            if (kind == Mo3Kind.FAST_TRACKER && (value & LOW_NIBBLE) == 0) {
                event.volumeEffect(Mo3Event.VOLUME_COLUMN_PANNING, value >> NIBBLE);
                return;
            }
        }
        event.effect(effect(PANNING, kind.isScreamTrackerFamily()), value);
    }

    private static void volume(Mo3Event event, int value, Mo3Kind kind) {
        if (kind != Mo3Kind.PROTRACKER && !event.hasVolumeEffect() && value <= LOUDEST) {
            event.volumeEffect(Mo3Event.VOLUME_COLUMN_VOLUME, value);
        } else {
            event.effect(effect(VOLUME, kind.isScreamTrackerFamily()), value);
        }
    }

    /**
     * Impulse Tracker breaks to the row the value names; the rest name it in decimal digits packed two to the
     * byte, which is what the tracker wrote and what its player reads back.
     */
    private static void patternBreak(Mo3Event event, int value, Mo3Kind kind) {
        event.effect(effect(PATTERN_BREAK, kind.isScreamTrackerFamily()),
                kind == Mo3Kind.IMPULSE_TRACKER ? value : (value >> NIBBLE) * TENS + (value & LOW_NIBBLE));
    }

    private static void columnVolumeSlide(Mo3Event event, int command, int value) {
        final boolean coarse = command == VOLUME_SLIDE_COARSE;
        if ((value & HIGH_NIBBLE) != 0) {
            event.volumeEffect(coarse ? Mo3Event.VOLUME_COLUMN_SLIDE_UP : Mo3Event.VOLUME_COLUMN_FINE_UP,
                    value >> NIBBLE);
        } else {
            event.volumeEffect(coarse ? Mo3Event.VOLUME_COLUMN_SLIDE_DOWN : Mo3Event.VOLUME_COLUMN_FINE_DOWN,
                    value & LOW_NIBBLE);
        }
    }

    private static void columnPanningSlide(Mo3Event event, int value) {
        if ((value & HIGH_NIBBLE) != 0) {
            event.volumeEffect(Mo3Event.VOLUME_COLUMN_PAN_RIGHT, value >> NIBBLE);
        } else {
            event.volumeEffect(Mo3Event.VOLUME_COLUMN_PAN_LEFT, value & LOW_NIBBLE);
        }
    }

    /**
     * A volume slide that follows a tone portamento or a vibrato is the one command that does both, which is
     * how an MO3 writes those two.
     */
    private static void volumeSlide(Mo3Event event, int value) {
        final int following = event.effect();
        if (following == letter('G')) {
            event.effect(letter('L'), value);
        } else if (following == letter('H')) {
            event.effect(letter('K'), value);
        } else {
            event.effect(letter('D'), value);
        }
    }

    /**
     * Impulse Tracker counts the four volume-column slides off in tens, the tens saying which slide and the
     * units how far.
     */
    private static void columnVolumeSlideByTens(Mo3Event event, int value) {
        final int by = value % COLUMN_STEPS;
        switch (value / COLUMN_STEPS) {
            case 0 -> event.volumeEffect(Mo3Event.VOLUME_COLUMN_FINE_UP, by);
            case 1 -> event.volumeEffect(Mo3Event.VOLUME_COLUMN_FINE_DOWN, by);
            case 2 -> event.volumeEffect(Mo3Event.VOLUME_COLUMN_SLIDE_UP, by);
            case 3 -> event.volumeEffect(Mo3Event.VOLUME_COLUMN_SLIDE_DOWN, by);
            default -> { }
        }
    }

    private static void columnOffset(Mo3Event event, int value) {
        if (value >= FIRST_COLUMN_OFFSET && value <= LAST_COLUMN_OFFSET) {
            event.volumeEffect(Mo3Event.VOLUME_COLUMN_OFFSET, value - FIRST_COLUMN_OFFSET);
        }
    }

    private static int effect(int command, boolean screamTracker) {
        final int[] effects = screamTracker ? SCREAM_TRACKER_EFFECTS : PROTRACKER_EFFECTS;
        return command < effects.length ? effects[command] : Mo3Event.NO_EFFECT;
    }

    private static int letter(char effect) {
        return effect - 'A' + 1;
    }

    private static int xmLetter(char effect) {
        return XM_FIRST_LETTER + effect - 'G';
    }

    /**
     * What Impulse Tracker and Scream Tracker call each command. Where the table says nothing, the command
     * belongs to the other family or lands in the volume column instead.
     */
    private static int[] screamTrackerEffects() {
        final int[] effects = new int[COMMANDS];
        effects[0x03] = letter('J');
        effects[0x06] = letter('G');
        effects[0x07] = letter('H');
        effects[0x0A] = letter('R');
        effects[0x0B] = letter('X');
        effects[0x0C] = letter('O');
        effects[0x0E] = letter('B');
        effects[0x10] = letter('C');
        effects[0x16] = letter('V');
        effects[0x21] = letter('A');
        effects[0x22] = letter('D');
        effects[0x23] = letter('E');
        effects[0x24] = letter('F');
        effects[0x25] = letter('I');
        effects[0x26] = letter('Q');
        effects[0x27] = letter('U');
        effects[0x28] = letter('M');
        effects[0x29] = letter('N');
        effects[0x2A] = letter('P');
        effects[0x2B] = letter('S');
        effects[0x2C] = letter('T');
        effects[0x2D] = letter('W');
        effects[0x2E] = letter('Y');
        effects[0x2F] = letter('Z');
        effects[0x35] = PARAMETER_EXTENSION;
        effects[0x36] = SMOOTH_MIDI;
        effects[0x37] = DELAY_CUT;
        effects[0x38] = FINE_TUNE;
        effects[0x39] = FINE_TUNE_SMOOTH;
        return effects;
    }

    /**
     * What ProTracker, Fast Tracker and MultiTracker call each command: the sixteen they numbered, then the
     * ones Fast Tracker gave letters to.
     */
    private static int[] protrackerEffects() {
        final int[] effects = new int[COMMANDS];
        effects[0x03] = 0x00;
        effects[0x04] = 0x01;
        effects[0x05] = 0x02;
        effects[0x06] = 0x03;
        effects[0x07] = 0x04;
        effects[0x08] = 0x05;
        effects[0x09] = 0x06;
        effects[0x0A] = 0x07;
        effects[0x0B] = 0x08;
        effects[0x0C] = 0x09;
        effects[0x0D] = 0x0A;
        effects[0x0E] = 0x0B;
        effects[0x0F] = 0x0C;
        effects[0x10] = 0x0D;
        effects[0x11] = 0x0E;
        effects[0x12] = 0x0F;
        effects[0x13] = xmLetter('T');
        effects[0x16] = xmLetter('G');
        effects[0x17] = xmLetter('H');
        effects[0x18] = xmLetter('K');
        effects[0x19] = xmLetter('L');
        effects[0x1A] = xmLetter('P');
        effects[0x1C] = xmLetter('R');
        effects[0x1D] = xmLetter('X');
        effects[0x1E] = xmLetter('X');
        effects[0x2E] = xmLetter('Y');
        effects[0x2F] = xmLetter('Z');
        effects[0x33] = xmLetter('W');
        effects[0x35] = XM_PARAMETER_EXTENSION;
        effects[0x36] = XM_SMOOTH_MIDI;
        return effects;
    }
}

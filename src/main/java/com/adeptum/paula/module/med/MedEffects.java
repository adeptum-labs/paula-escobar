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
 * The replay follows the MED loaders and med_extras.c of libxmp,
 * Copyright © 1996-2026 Claudio Matsuoka and Hipolito Carraro Jr,
 * licensed under the MIT licence and used here under the GNU General
 * Public License.
 */

package com.adeptum.paula.module.med;

/**
 * Reads the command written on a line for what it asks of the voice. OctaMED numbers its commands its own way,
 * spells a volume in decimal unless the song says otherwise, and hides five more commands behind the parameter
 * of one of them; a command it has no meaning for is dropped rather than guessed at.
 */
final class MedEffects {

    private static final int ARPEGGIO = 0x00;
    private static final int SLIDE_UP = 0x01;
    private static final int SLIDE_DOWN = 0x02;
    private static final int PORTAMENTO = 0x03;
    private static final int DEEP_VIBRATO = 0x04;
    private static final int PORTAMENTO_AND_VOLUME_SLIDE = 0x05;
    private static final int VIBRATO_AND_VOLUME_SLIDE = 0x06;
    private static final int TREMOLO = 0x07;
    private static final int HOLD_AND_DECAY = 0x08;
    private static final int SET_SPEED = 0x09;
    private static final int VOLUME_SLIDE = 0x0A;
    private static final int POSITION_JUMP = 0x0B;
    private static final int SET_VOLUME = 0x0C;
    private static final int VOLUME_SLIDE_AGAIN = 0x0D;
    private static final int SYNTH_JUMP = 0x0E;
    private static final int TEMPO = 0x0F;
    private static final int FINE_SLIDE_UP = 0x11;
    private static final int FINE_SLIDE_DOWN = 0x12;
    private static final int VIBRATO = 0x14;
    private static final int FINETUNE = 0x15;
    private static final int LOOP = 0x16;
    private static final int NOTE_CUT = 0x18;
    private static final int SAMPLE_OFFSET = 0x19;
    private static final int FINE_VOLUME_UP = 0x1A;
    private static final int FINE_VOLUME_DOWN = 0x1B;
    private static final int BREAK = 0x1D;
    private static final int PATTERN_DELAY = 0x1E;
    private static final int RETRIGGER = 0x1F;
    private static final int REVERSE = 0x20;
    private static final int SET_PAN = 0x2E;

    private static final int SLOWEST_SPEED = 0x01;
    private static final int FASTEST_SPEED = 0x20;
    private static final int NEXT_BLOCK = 0x00;
    private static final int HIGHEST_TEMPO = 0xF0;
    private static final int PLAY_TWICE = 0xF1;
    private static final int DELAY_NOTE = 0xF2;
    private static final int PLAY_THREE_TIMES = 0xF3;
    private static final int NOTE_OFF = 0xFF;

    private static final int TWICE = 3;
    private static final int THRICE = 2;
    private static final int TENS = 10;
    private static final int NIBBLE = 4;
    private static final int HIGHEST_DECIMAL = 0x99;
    private static final int FINETUNE_BIAS = 8;
    private static final int LOOP_ROWS = 0x0F;
    private static final int PAN_LEFT = 0xF0;
    private static final int PAN_RIGHT = 0x10;
    private static final int PAN_CENTRE = 16;
    private static final int PAN_SCALE = 3;
    private static final int PAN_FULL = 0x100;

    private MedEffects() {
    }

    static MedCommand translate(MedEntry entry, MedSong song) {
        final int parameter = entry.parameter();
        return switch (entry.command()) {
            case ARPEGGIO -> command(entry, MedEffect.ARPEGGIO, parameter);
            case SLIDE_UP -> sliding(entry, MedEffect.SLIDE_UP, parameter);
            case SLIDE_DOWN -> sliding(entry, MedEffect.SLIDE_DOWN, parameter);
            case PORTAMENTO -> command(entry, MedEffect.PORTAMENTO, parameter);
            case DEEP_VIBRATO -> command(entry, MedEffect.DEEP_VIBRATO, parameter);
            case PORTAMENTO_AND_VOLUME_SLIDE ->
                    command(entry, MedEffect.PORTAMENTO_AND_VOLUME_SLIDE, parameter);
            case VIBRATO_AND_VOLUME_SLIDE -> command(entry, MedEffect.VIBRATO_AND_VOLUME_SLIDE, parameter);
            case TREMOLO -> command(entry, MedEffect.TREMOLO, parameter);
            case HOLD_AND_DECAY -> command(entry, MedEffect.HOLD_AND_DECAY, parameter);
            case SET_SPEED -> speed(entry, parameter);
            case VOLUME_SLIDE, VOLUME_SLIDE_AGAIN -> command(entry, MedEffect.VOLUME_SLIDE, parameter);
            case POSITION_JUMP -> command(entry, MedEffect.POSITION_JUMP, parameter);
            case SET_VOLUME -> command(entry, MedEffect.SET_VOLUME, volume(parameter, song));
            case TEMPO -> tempo(entry, parameter, song);
            case FINE_SLIDE_UP -> command(entry, MedEffect.FINE_SLIDE_UP, parameter);
            case FINE_SLIDE_DOWN -> command(entry, MedEffect.FINE_SLIDE_DOWN, parameter);
            case VIBRATO -> command(entry, MedEffect.VIBRATO, parameter);
            case FINETUNE -> command(entry, MedEffect.FINETUNE, parameter + FINETUNE_BIAS);
            case LOOP -> command(entry, MedEffect.LOOP, Math.min(parameter, LOOP_ROWS));
            case NOTE_CUT -> command(entry, MedEffect.NOTE_CUT, Math.min(parameter, LOOP_ROWS));
            case SAMPLE_OFFSET -> command(entry, MedEffect.SAMPLE_OFFSET, parameter);
            case FINE_VOLUME_UP -> sliding(entry, MedEffect.FINE_VOLUME_UP, parameter);
            case FINE_VOLUME_DOWN -> sliding(entry, MedEffect.FINE_VOLUME_DOWN, parameter);
            case BREAK -> command(entry, MedEffect.BREAK, parameter);
            case PATTERN_DELAY -> command(entry, MedEffect.PATTERN_DELAY, parameter);
            case RETRIGGER -> retrigger(entry, parameter);
            case REVERSE -> reverse(entry, parameter);
            case SET_PAN -> pan(entry, parameter);
            case SYNTH_JUMP -> dropped(entry);
            default -> dropped(entry);
        };
    }

    /**
     * A slide of nothing at all is written where a tracker would leave the line empty, and means as much.
     */
    private static MedCommand sliding(MedEntry entry, MedEffect effect, int parameter) {
        return parameter == 0 ? dropped(entry) : command(entry, effect, parameter);
    }

    private static MedCommand speed(MedEntry entry, int parameter) {
        return parameter >= SLOWEST_SPEED && parameter <= FASTEST_SPEED
                ? command(entry, MedEffect.SET_SPEED, parameter) : dropped(entry);
    }

    /**
     * Unless the song says its volumes are written in hex, the two digits of the parameter are read as the
     * decimal number they are written to look like, so a stored forty is forty and not sixty-four.
     *
     * <p>libxmp shifts a whole byte where it means a nibble and so reads these unchanged. The modules say
     * otherwise: across nearly two thousand of these commands in songs that count in decimal, no digit is
     * ever above nine and the largest parameter is 0x64, which is the highest volume there is once read as
     * digits and nonsense read as a number. A parameter too large to be two decimal digits is left alone.
     */
    private static int volume(int parameter, MedSong song) {
        return song.volumesAreHex() || parameter >= HIGHEST_DECIMAL
                ? parameter : (parameter >> NIBBLE) * TENS + (parameter & LOOP_ROWS);
    }

    /**
     * The tempo command carries five commands of its own above the tempos, and a parameter of nothing at all
     * means the block ends here.
     */
    private static MedCommand tempo(MedEntry entry, int parameter, MedSong song) {
        if (parameter == NEXT_BLOCK) {
            return command(entry, MedEffect.BREAK, 0);
        }
        if (parameter <= HIGHEST_TEMPO) {
            return command(entry, MedEffect.SET_TEMPO, convertTempo(parameter, song));
        }
        return switch (parameter) {
            case PLAY_TWICE -> command(entry, MedEffect.RETRIGGER, TWICE);
            case DELAY_NOTE -> command(entry, MedEffect.NOTE_DELAY, TWICE);
            case PLAY_THREE_TIMES -> command(entry, MedEffect.RETRIGGER, THRICE);
            case NOTE_OFF -> new MedCommand(0, entry.instrument(), MedEffect.NOTE_CUT, 0, false);
            default -> dropped(entry);
        };
    }

    /**
     * In five to eight channel mode the tempo drives the mixing buffer, and below that the ten lowest tempos
     * stand for Soundtracker speeds rather than for themselves.
     */
    static int convertTempo(int tempo, MedSong song) {
        if (tempo < MedTables.LOWEST_TEMPO) {
            return tempo;
        }
        if (song.isEightChannel()) {
            return MedTables.eightChannelTempo(tempo);
        }
        return tempo <= MedTables.HIGHEST_COMPATIBILITY_TEMPO && !song.isBpm()
                ? MedTables.compatibilityTempo(tempo) : tempo;
    }

    private static MedCommand retrigger(MedEntry entry, int parameter) {
        return parameter != 0 && entry.hasNote()
                ? command(entry, MedEffect.RETRIGGER, parameter) : dropped(entry);
    }

    private static MedCommand reverse(MedEntry entry, int parameter) {
        return parameter == 0 && entry.hasNote() ? command(entry, MedEffect.REVERSE, 1) : dropped(entry);
    }

    /**
     * Panning is written as a signed step either side of the middle, and only the sixteen steps each way mean
     * anything; the rightmost lands one short of the wall so that it stays a position rather than an overflow.
     */
    private static MedCommand pan(MedEntry entry, int parameter) {
        if (parameter < PAN_LEFT && parameter > PAN_RIGHT) {
            return dropped(entry);
        }
        final int placed = ((byte) parameter + PAN_CENTRE) << PAN_SCALE;
        return command(entry, MedEffect.SET_PAN, placed == PAN_FULL ? placed - 1 : placed);
    }

    private static MedCommand command(MedEntry entry, MedEffect effect, int parameter) {
        return new MedCommand(entry.note(), entry.instrument(), effect, parameter, entry.isHoldSymbol());
    }

    private static MedCommand dropped(MedEntry entry) {
        return command(entry, MedEffect.NONE, 0);
    }
}

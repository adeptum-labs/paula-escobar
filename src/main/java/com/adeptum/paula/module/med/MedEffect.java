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
 * What a line can ask of the voice it is on, once the command written in the module has been read for what it
 * means. OctaMED writes a wider set than a tracker and spells several of them differently, so the sequencer
 * works in these rather than in the numbers on the line.
 */
enum MedEffect {
    NONE,
    ARPEGGIO,
    SLIDE_UP,
    SLIDE_DOWN,
    PORTAMENTO,
    DEEP_VIBRATO,
    PORTAMENTO_AND_VOLUME_SLIDE,
    VIBRATO_AND_VOLUME_SLIDE,
    TREMOLO,
    HOLD_AND_DECAY,
    VOLUME_SLIDE,
    POSITION_JUMP,
    SET_VOLUME,
    SET_SPEED,
    SET_TEMPO,
    BREAK,
    FINE_SLIDE_UP,
    FINE_SLIDE_DOWN,
    VIBRATO,
    FINETUNE,
    LOOP,
    NOTE_CUT,
    SAMPLE_OFFSET,
    FINE_VOLUME_UP,
    FINE_VOLUME_DOWN,
    PATTERN_DELAY,
    RETRIGGER,
    NOTE_DELAY,
    REVERSE,
    SET_PAN
}

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

package com.adeptum.paula.module.mo3;

/**
 * One row of one track: a note, the instrument to sound it with, an effect and an effect in the volume column.
 *
 * <p>An MO3 writes each of those as a command of its own, several to a row, so the row is filled in as they
 * are read rather than made in one go. The effect is left in the numbering the tracker that wrote the module
 * used, since that is what its player is waiting for: one to twenty-six for A to Z in the Impulse Tracker and
 * Scream Tracker family, and the effect number itself in the ProTracker one. The volume column happens to be
 * counted the same way in both.</p>
 */
final class Mo3Event {

    static final int NO_NOTE = 0;
    static final int KEY_OFF = -1;
    static final int NOTE_CUT = -2;
    static final int NOTE_FADE = -3;

    static final int NO_EFFECT = 0;

    static final int VOLUME_COLUMN_NONE = 0;
    static final int VOLUME_COLUMN_VOLUME = 0x01;
    static final int VOLUME_COLUMN_SLIDE_DOWN = 0x02;
    static final int VOLUME_COLUMN_SLIDE_UP = 0x03;
    static final int VOLUME_COLUMN_FINE_DOWN = 0x04;
    static final int VOLUME_COLUMN_FINE_UP = 0x05;
    static final int VOLUME_COLUMN_VIBRATO_SPEED = 0x06;
    static final int VOLUME_COLUMN_VIBRATO_DEPTH = 0x07;
    static final int VOLUME_COLUMN_PANNING = 0x08;
    static final int VOLUME_COLUMN_PAN_LEFT = 0x09;
    static final int VOLUME_COLUMN_PAN_RIGHT = 0x0A;
    static final int VOLUME_COLUMN_TONE_PORTAMENTO = 0x0B;
    static final int VOLUME_COLUMN_PITCH_DOWN = 0x0C;
    static final int VOLUME_COLUMN_PITCH_UP = 0x0D;
    static final int VOLUME_COLUMN_OFFSET = 0x0E;

    private int note = NO_NOTE;
    private int instrument;
    private int effect = NO_EFFECT;
    private int effectOp;
    private int volumeEffect = VOLUME_COLUMN_NONE;
    private int volumeEffectOp;

    int note() {
        return note;
    }

    void note(int note) {
        this.note = note;
    }

    int instrument() {
        return instrument;
    }

    void instrument(int instrument) {
        this.instrument = instrument;
    }

    int effect() {
        return effect;
    }

    int effectOp() {
        return effectOp;
    }

    void effect(int effect, int effectOp) {
        this.effect = effect;
        this.effectOp = effectOp;
    }

    void effectOp(int effectOp) {
        this.effectOp = effectOp;
    }

    int volumeEffect() {
        return volumeEffect;
    }

    int volumeEffectOp() {
        return volumeEffectOp;
    }

    void volumeEffect(int volumeEffect, int volumeEffectOp) {
        this.volumeEffect = volumeEffect;
        this.volumeEffectOp = volumeEffectOp;
    }

    boolean hasVolumeEffect() {
        return volumeEffect != VOLUME_COLUMN_NONE;
    }

    boolean isEmpty() {
        return note == NO_NOTE && instrument == 0 && effect == NO_EFFECT && effectOp == 0
                && volumeEffect == VOLUME_COLUMN_NONE;
    }
}

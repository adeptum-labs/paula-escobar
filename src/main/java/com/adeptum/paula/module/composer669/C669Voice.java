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

package com.adeptum.paula.module.composer669;

/**
 * One channel of the song while it plays: the sample it sounds and where it stands in it, the frequency in
 * hertz it sounds at, and the effect that carries on from row to row until a note or another effect ends it.
 */
final class C669Voice {

    static final int FULL_VOLUME = 64;
    static final int HARD_RIGHT = 255;
    static final int MIDDLE = 128;

    short[] sample;
    int loopStart;
    int loopEnd;
    double position;
    int instrument;
    int frequency;
    int targetFrequency;
    int finetune;
    int vibrato;
    int volume;
    int panning = MIDDLE;
    boolean sounding;
    boolean muted;
    int carried = C669Cell.NONE;
    int carriedParameter;
    int portamentoStep;
    boolean portamentoThisRow;
    int vibratoDepth;
    int vibratoPosition;
    int retrigEvery;
    int peak;

    /**
     * Starts a sample at its head, at the frequency of the note struck, dropping whatever effect the channel
     * was carrying and any finetune the last note was given.
     */
    void start(C669Sample from, int number, int atFrequency) {
        sample = from.data();
        loopStart = from.loopStart();
        loopEnd = from.loopEnd();
        instrument = number + 1;
        frequency = atFrequency;
        targetFrequency = atFrequency;
        finetune = 0;
        vibratoPosition = 0;
        carried = C669Cell.NONE;
        position = 0;
        sounding = sample.length > 0;
    }

    /**
     * What a new row leaves behind: the effects that last only for their own row.
     */
    void rowStarts() {
        portamentoThisRow = false;
        vibratoDepth = 0;
        vibrato = 0;
        retrigEvery = 0;
    }

    void retrigger() {
        position = 0;
    }

    void silence() {
        sounding = false;
        sample = null;
        position = 0;
        peak = 0;
    }

    /**
     * Where in the sample the next frame comes from, round the loop once its end is passed; nothing is left to
     * sound when a sample without a loop runs out.
     */
    boolean advance(double step) {
        position += step;
        if (loopEnd > loopStart) {
            while (position >= loopEnd) {
                position -= loopEnd - loopStart;
            }
            return true;
        }
        if (position >= sample.length) {
            silence();
            return false;
        }
        return true;
    }

    short frameAt() {
        final int index = (int) position;
        return index >= 0 && index < sample.length ? sample[index] : 0;
    }
}

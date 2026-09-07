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
 * One track of the song while it is playing: what it is sounding, where in the sample it stands, and the state
 * every effect that runs on past its own line leaves behind.
 */
final class MedVoice {

    private static final int FULL_VOLUME = 64;
    static final int INACTIVE = -1;
    static final int MIDDLE_PANNING = 128;
    static final int HARD_RIGHT = 255;

    short[] sample;
    int loopStart;
    int loopLength;

    int note;
    int instrument;
    int period;
    int targetPeriod;
    int portamentoSpeed;
    int finetune;
    int volume;
    int panning = MIDDLE_PANNING;
    int trackVolume = FULL_VOLUME;

    double position;
    boolean sounding;
    boolean muted;

    int vibratoSpeed;
    int vibratoDepth;
    int vibratoStep;
    int tremoloSpeed;
    int tremoloDepth;
    int tremoloStep;
    int arpeggio;

    int holdCount = INACTIVE;
    int decayValue = INACTIVE;
    boolean holdActive;
    boolean holdSustained;

    int retriggerEvery;
    int delayTicks;
    MedCommand delayed;

    int peak;

    void silence() {
        sounding = false;
        sample = null;
        position = 0;
        peak = 0;
    }

    /**
     * Starts one layer of an instrument at the head of its sample, keeping the effects a new note does not
     * clear.
     */
    void start(MedLayer layer, int startPeriod, int startVolume) {
        sample = layer.sample();
        loopStart = layer.loops() ? layer.loopStart() : 0;
        loopLength = layer.loops() ? layer.loopLength() : 0;
        period = startPeriod;
        targetPeriod = startPeriod;
        volume = startVolume;
        position = 0;
        vibratoStep = 0;
        tremoloStep = 0;
        sounding = sample != null && sample.length > 0;
    }

    /**
     * Where in the sample the next frame comes from, following the loop round once the end is passed; nothing
     * is left to sound when a sample without a loop runs out.
     */
    boolean advance(double step) {
        position += step;
        if (loopLength > 1) {
            final int end = Math.min(sample.length, loopStart + loopLength);
            while (position >= end) {
                position -= loopLength;
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

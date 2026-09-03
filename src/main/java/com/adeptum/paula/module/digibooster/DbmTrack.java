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
 * The track state is that of player.c in libdigibooster3, Copyright © 2014
 * Grzegorz Kraszewski, licensed under the two-clause BSD licence and used
 * here under the GNU General Public License.
 */

package com.adeptum.paula.module.digibooster;

import java.util.Arrays;

/**
 * One track of the module while it plays: the instrument it holds with the chain that sounds it, the volume,
 * panning and pitch the effects keep moving, and the echo the track is sent through.
 */
final class DbmTrack {

    static final int NEVER = 0x7FFFFFFF;
    static final int MIDDLE_C = 576;
    static final int ARPEGGIO_STEPS = 3;

    /** The effects that play their last parameter again when given none. */
    static final int VOLUME_SLIDE = 0;
    static final int PANNING_SLIDE = 1;
    static final int PORTA_UP = 2;
    static final int PORTA_DOWN = 3;
    static final int PORTA_SPEED = 4;
    static final int PORTA_VOLUME_SLIDE = 5;
    static final int VIBRATO = 6;
    static final int VIBRATO_VOLUME_SLIDE = 7;
    static final int REMEMBERED = 8;

    private final int[] shifts;

    int instrument;
    boolean playing;
    boolean muted;

    DbmWavetable wavetable;
    DbmResampler resampler;
    DbmPanoramizer panoramizer;
    DbmEchoUnit echo;

    short gainLeft;
    short gainRight;
    int volume;
    int panning;
    int pitch;
    short volumeDelta;
    short panningDelta;
    short pitchDelta;
    short portaDelta;
    short portaTarget = MIDDLE_C;
    final short[] arpeggio = new short[ARPEGGIO_STEPS];
    short vibratoSpeed;
    short vibratoDepth;
    short vibratoCounter;

    final DbmEnvelopeState volumeEnvelope = new DbmEnvelopeState();
    final DbmEnvelopeState panningEnvelope = new DbmEnvelopeState();
    short volumeEnvelopeValue;
    short panningEnvelopeValue;

    int triggerCounter = NEVER;
    int cutCounter = NEVER;
    int retrigger;
    int triggerOffset;
    boolean backwards;

    final int[] remembered = new int[REMEMBERED];

    int echoDelay;
    int echoFeedback;
    int echoMix;
    int echoCross;

    DbmTrack(int[] shifts) {
        this.shifts = shifts;
    }

    void sound(short[] sample, int loopStart, int loopLength, int loopType) {
        wavetable = new DbmWavetable(sample, loopStart, loopLength, loopType);
        resampler = new DbmResampler(wavetable);
        panoramizer = new DbmPanoramizer(resampler, shifts);
    }

    void silence() {
        wavetable = null;
        resampler = null;
        panoramizer = null;
        instrument = 0;
        playing = false;
    }

    boolean sounding() {
        return panoramizer != null;
    }

    /**
     * What the track sends on: its instrument, or the echo it is played through once one is switched on. A new
     * echo starts from the tracker's own defaults; the module's own settings only reach it once an effect asks
     * for them, the way the reference replayer does it.
     */
    boolean pull(short[] into, int at, int frames) {
        return echo == null ? pullInstrument(into, at, frames) : echo.pull(into, at, frames);
    }

    private boolean pullInstrument(short[] into, int at, int frames) {
        if (panoramizer == null) {
            Arrays.fill(into, at, at + frames * 2, (short) 0);
            return false;
        }
        return panoramizer.pull(into, at, frames);
    }

    void echoOn(int type, int sampleRate) {
        if (echo == null) {
            echo = new DbmEchoUnit(type, sampleRate, this::pullInstrument);
        }
    }

    void echoOff(int type) {
        if (echo != null && echo.type() == type) {
            echo = null;
        }
    }

    int echoType() {
        return echo == null ? 0 : echo.type();
    }

    void echoParameters() {
        if (echo != null) {
            echo.delay(echoDelay);
            echo.levels(echoMix, echoCross, echoFeedback);
        }
    }
}

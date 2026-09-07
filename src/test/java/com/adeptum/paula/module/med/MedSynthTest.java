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

package com.adeptum.paula.module.med;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class MedSynthTest {

    private static final int END = 0xFF;
    private static final int JUMP = 0xFE;
    private static final int WAIT = 0xF1;
    private static final int SET_SPEED = 0xF0;
    private static final int CHANGE_DOWN = 0xF2;
    private static final int PERIOD = 428;
    private static final int LOUD = 60;
    private static final int QUIET = 10;

    private final short[][] waveforms = {new short[32], new short[64]};

    private MedSynthInstrument synth(int[] volume, int[] waveform, int volumeSpeed, int waveformSpeed) {
        return new MedSynthInstrument(volumeSpeed, waveformSpeed, 0, bytes(volume), bytes(waveform), waveforms);
    }

    private static byte[] bytes(int... values) {
        final byte[] table = new byte[values.length];
        for (int at = 0; at < values.length; at++) {
            table[at] = (byte) values[at];
        }
        return table;
    }

    private MedVoice started(MedSynthInstrument synth) {
        final MedVoice voice = new MedVoice();
        voice.period = PERIOD;
        MedSynth.start(voice, synth, PERIOD);
        return voice;
    }

    @Test
    void stepsTheVolumeSequenceOneEntryATick() {
        final MedVoice voice = started(synth(new int[]{LOUD, QUIET, END}, new int[]{END}, 1, 0));

        MedSynth.tick(voice);
        assertEquals(LOUD, voice.synthVolume);
        MedSynth.tick(voice);
        assertEquals(QUIET, voice.synthVolume);
        MedSynth.tick(voice);
        assertEquals(QUIET, voice.synthVolume, "the end holds where it stopped");
    }

    @Test
    void waitsWhereTheSequenceAsksItTo() {
        final MedVoice voice = started(synth(new int[]{LOUD, WAIT, 2, QUIET, END}, new int[]{END}, 1, 0));

        MedSynth.tick(voice);
        MedSynth.tick(voice);
        MedSynth.tick(voice);
        MedSynth.tick(voice);
        assertEquals(LOUD, voice.synthVolume, "two ticks of waiting are two ticks of the same volume");
        MedSynth.tick(voice);
        assertEquals(QUIET, voice.synthVolume);
    }

    @Test
    void takesTheSpeedTheSequenceSets() {
        final MedVoice voice = started(synth(new int[]{SET_SPEED, 3, LOUD, QUIET, END}, new int[]{END}, 1, 0));

        MedSynth.tick(voice);
        MedSynth.tick(voice);
        assertEquals(LOUD, voice.synthVolume, "the speed itself is read on the first tick");
        MedSynth.tick(voice);
        MedSynth.tick(voice);
        assertEquals(LOUD, voice.synthVolume, "and three ticks pass before the next entry");
        MedSynth.tick(voice);
        assertEquals(QUIET, voice.synthVolume);
    }

    @Test
    void jumpsBackWithoutRunningForEver() {
        final MedVoice voice = started(synth(new int[]{LOUD, JUMP, 0, END}, new int[]{END}, 1, 0));

        MedSynth.tick(voice);
        MedSynth.tick(voice);

        assertEquals(LOUD, voice.synthVolume, "a jump reads on rather than looping in one tick");
    }

    @Test
    void swapsTheWaveformUnderTheNote() {
        final MedVoice voice = started(synth(new int[]{END}, new int[]{1, 0, END}, 0, 1));

        MedSynth.tick(voice);
        assertSame(waveforms[1], voice.sample, "the sequence names which waveform sounds");
        MedSynth.tick(voice);
        assertSame(waveforms[0], voice.sample);
    }

    @Test
    void slidesThePitchWhereTheSequenceAsks() {
        final MedVoice voice = started(synth(new int[]{END}, new int[]{CHANGE_DOWN, 4, END}, 0, 1));

        MedSynth.tick(voice);
        MedSynth.tick(voice);

        assertEquals(PERIOD + 8, voice.period, "two ticks of sliding four each");
    }

    @Test
    void doesNothingForAnInstrumentThatIsNotSynthetic() {
        final MedVoice voice = new MedVoice();

        MedSynth.tick(voice);

        assertEquals(0, voice.period);
        assertNotNull(voice);
    }
}

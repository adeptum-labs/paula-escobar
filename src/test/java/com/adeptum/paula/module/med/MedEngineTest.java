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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MedEngineTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int LONGER_THAN_THE_SONG = 1000;
    private static final int FRAMES = 4096;
    private static final int AUDIBLE = 1000;

    private MedEngine engine() throws IOException {
        return new MedEngine(MedReader.read(TestModules.medMmd0()), SAMPLE_RATE);
    }

    @Test
    void hasAVoiceForEveryTrackOfTheWidestBlock() throws IOException {
        assertEquals(TestModules.MED_TRACKS, engine().tracks());
    }

    @Test
    void soundsTheNoteWrittenOnTheFirstLine() throws IOException {
        final MedEngine engine = engine();

        engine.nextTick();

        assertTrue(engine.voice(0).sounding, "the note on the first track is heard");
        assertNotNull(engine.voice(0).sample);
        assertFalse(engine.voice(1).sounding, "and the second track was left empty");
    }

    @Test
    void soundsItAtThePeriodOfTheNoteWritten() throws IOException {
        final MedEngine engine = engine();

        engine.nextTick();

        assertEquals(MedTables.period(TestModules.MED_NOTE), engine.soundingPeriod(engine.voice(0)));
        assertEquals(TestModules.MED_VOLUME, engine.soundingVolume(engine.voice(0)));
    }

    @Test
    void endsTheSongWhenThePlaySequenceRunsOut() throws IOException {
        final MedEngine engine = engine();

        for (int tick = 0; tick < LONGER_THAN_THE_SONG && !engine.hasEnded(); tick++) {
            engine.nextTick();
        }

        assertTrue(engine.hasEnded(), "one block of four lines does not play for ever");
    }

    /**
     * The tempo counts beats a minute and the tracker divides each into twenty-four ticks, so a tick of the
     * default tempo is a known number of frames rather than whatever falls out.
     */
    @Test
    void measuresATickFromTheTempo() throws IOException {
        assertEquals(SAMPLE_RATE * 60 / (TestModules.MED_TEMPO * 24), engine().tickFrames());
    }

    @Test
    void startsWhereItIsPutForASeek() throws IOException {
        final MedEngine engine = engine();

        engine.position(0, 2);
        engine.nextTick();

        assertFalse(engine.voice(0).sounding, "the third line carries no note");
    }

    @Test
    void mixesTheNoteIntoBothSides() throws IOException {
        final MedEngine engine = engine();
        final short[] out = new short[FRAMES * 2];

        assertEquals(FRAMES, engine.mix(out, FRAMES), "the song is still playing");
        assertTrue(loudest(out) > AUDIBLE, "the square wave is heard, peak was " + loudest(out));
    }

    @Test
    void mixesNothingOnceTheSongHasEnded() throws IOException {
        final MedEngine engine = engine();
        final short[] out = new short[FRAMES * 2];

        while (engine.mix(out, FRAMES) == FRAMES) {
            continue;
        }

        assertEquals(0, engine.mix(out, FRAMES), "nothing is left to play");
    }

    @Test
    void silencesAMutedTrack() throws IOException {
        final MedEngine engine = engine();
        final short[] out = new short[FRAMES * 2];
        engine.mix(out, FRAMES);
        final int heard = loudest(out);

        engine.position(0, 0);
        engine.voice(0).muted = true;
        engine.mix(out, FRAMES);

        assertTrue(heard > AUDIBLE, "it was heard before it was silenced");
        assertEquals(0, loudest(out), "and nothing after");
    }

    @Test
    void knowsHowLongTheSongIs() throws IOException {
        final MedFile module = MedReader.read(TestModules.medMmd0());

        final long frames = MedEngine.songFrames(module, SAMPLE_RATE, SAMPLE_RATE * 60L).orElseThrow();

        assertTrue(frames > 0 && frames < SAMPLE_RATE * 10L, "four lines are seconds, not minutes, was " + frames);
    }

    private static int loudest(short[] out) {
        int peak = 0;
        for (final short sample : out) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}

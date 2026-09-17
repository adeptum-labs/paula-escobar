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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestPscs;
import java.io.IOException;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class PscEngineTest {

    private static final int TONE_A = 0;
    private static final int TONE_C = 2;
    private static final int NOISE = 6;
    private static final int MIXER = 7;
    private static final int VOLUME_A = 8;
    private static final int VOLUME_B = 9;
    private static final int VOLUME_C = 10;
    private static final int ENVELOPE_PERIOD = 11;
    private static final int SHAPE = 13;
    private static final int BY_ENVELOPE = 0x10;
    private static final int TONE_AND_NOISE_A_OFF = 0x09;
    private static final int LOUDEST = 15;

    private static final int THIRD_LINE = 2 * TestPscs.LINE_TEMPO;
    private static final int FOURTH_LINE = 3 * TestPscs.LINE_TEMPO;
    private static final int SECOND_PATTERN = TestPscs.FIRST_PATTERN_LINES * TestPscs.LINE_TEMPO;
    private static final int FRAMES = (TestPscs.FIRST_PATTERN_LINES + TestPscs.SECOND_PATTERN_LINES)
            * TestPscs.LINE_TEMPO;

    /**
     * Each sample line adds its tone to the ones before it, and each ornament line moves the note on from where
     * the last one left it.
     */
    @Test
    void addsUpTheSampleTonesAndTheOrnamentSteps() throws IOException {
        assertEquals(PscEngine.TONES[TestPscs.NOTE] + TestPscs.TONE_STEP, tone(frame(0), TONE_A));
        assertEquals(PscEngine.TONES[TestPscs.NOTE + TestPscs.ORNAMENT_STEP] + 2 * TestPscs.TONE_STEP,
                tone(frame(1), TONE_A));
        assertEquals(PscEngine.TONES[TestPscs.NOTE + TestPscs.ORNAMENT_STEP] + 3 * TestPscs.TONE_STEP,
                tone(frame(2), TONE_A));
    }

    /**
     * The level is the sample's scaled by the channel volume, which the sample lines step up and down.
     */
    @Test
    void scalesTheSampleLevelByTheSteppedVolume() throws IOException {
        assertEquals((TestPscs.VOLUME + 1) * TestPscs.LEVEL >> 4, frame(0)[VOLUME_A]);
        assertEquals(TestPscs.VOLUME * TestPscs.SECOND_LEVEL >> 4, frame(1)[VOLUME_A]);
        assertEquals(TestPscs.VOLUME * TestPscs.LEVEL >> 4, frame(2)[VOLUME_A]);
        assertEquals(0, frame(0)[VOLUME_C], "the third channel rests");
    }

    @Test
    void masksWhatTheSampleLineMasks() throws IOException {
        assertEquals(0, frame(0)[MIXER] & TONE_AND_NOISE_A_OFF);
        assertEquals(TONE_AND_NOISE_A_OFF, frame(1)[MIXER] & TONE_AND_NOISE_A_OFF);
    }

    /**
     * Every channel's noise moves on by the noise base each line and by what the sample line adds each
     * interrupt; the last channel sounding noise writes it.
     */
    @Test
    void soundsTheNoiseTheBaseAndTheSampleAddUpTo() throws IOException {
        assertEquals(TestPscs.NOISE_BASE + TestPscs.NOISE_ADDING, frame(0)[NOISE]);
    }

    @Test
    void writesTheEnvelopeOnlyOnTheFrameThatNamesIt() throws IOException {
        assertEquals(TestPscs.ENVELOPE_TYPE, frame(0)[SHAPE]);
        assertEquals(TestPscs.ENVELOPE_TONE, frame(0)[ENVELOPE_PERIOD] | frame(0)[ENVELOPE_PERIOD + 1] << 8);
        assertEquals(RegisterFrames.SHAPE_UNTOUCHED, frame(1)[SHAPE]);
    }

    /**
     * The envelope sounds on a channel told to use it only where the sample line lets it.
     */
    @Test
    void soundsTheEnvelopeWhereTheSampleLineLetsIt() throws IOException {
        assertEquals((LOUDEST + 1) * TestPscs.LEVEL >> 4 | BY_ENVELOPE, frame(0)[VOLUME_B]);
        assertEquals(0, frame(1)[VOLUME_B] & BY_ENVELOPE);
        assertEquals(0, frame(0)[VOLUME_A] & BY_ENVELOPE, "a plain volume turns the envelope off");
    }

    /**
     * A glissando starts from the tone last sounded and moves toward the note by its step.
     */
    @Test
    void glidesFromTheToneLastSounded() throws IOException {
        assertEquals(tone(frame(THIRD_LINE - 1), TONE_A) + TestPscs.TONE_STEP + TestPscs.GLISS_STEP,
                tone(frame(THIRD_LINE), TONE_A));
    }

    @Test
    void silencesARestingChannel() throws IOException {
        assertEquals(0, frame(FOURTH_LINE)[VOLUME_A]);
        assertEquals(0, frame(SECOND_PATTERN)[VOLUME_B], "a rest before a note");
    }

    /**
     * A slide of the volume up every other interrupt makes up for the sample line stepping it down.
     */
    @Test
    void slidesTheVolume() throws IOException {
        assertEquals((TestPscs.VOLUME + 1) * TestPscs.LEVEL >> 4, frame(SECOND_PATTERN + 2)[VOLUME_A]);
    }

    /**
     * A note clears a break out of the loop named alongside it, so its looping sample keeps sounding.
     */
    @Test
    void aNoteKeepsTheLoopABreakBesideItWouldEnd() throws IOException {
        assertEquals(PscEngine.TONES[TestPscs.SECOND_NOTE] + 3 * TestPscs.TONE_STEP,
                tone(frame(SECOND_PATTERN + 2), TONE_C));
        assertTrue(frame(SECOND_PATTERN + 2)[VOLUME_C] > 0);
    }

    @Test
    void runsForAsLongAsTheOrderTakesAtTheTemposItSets() throws IOException {
        assertEquals(OptionalLong.of(FRAMES), engine().frames());
    }

    @Test
    void endsWhenTheOrderHasRunOut() throws IOException {
        final PscEngine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (long at = 0; at < FRAMES; at++) {
            assertTrue(engine.nextFrame(registers), "frame " + at + " should sound");
        }

        assertFalse(engine.nextFrame(registers));
    }

    @Test
    void goesBackToAFrameAndPlaysOnFromThere() throws IOException {
        final PscEngine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (int at = 0; at < FRAMES; at++) {
            engine.nextFrame(registers);
        }

        engine.rewindTo(THIRD_LINE);
        engine.nextFrame(registers);

        assertEquals(tone(frame(THIRD_LINE), TONE_A), tone(registers, TONE_A));
    }

    private static int tone(int[] registers, int channel) {
        return registers[channel * 2] | registers[channel * 2 + 1] << 8;
    }

    private static int[] frame(int at) throws IOException {
        final PscEngine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (int frame = 0; frame <= at; frame++) {
            engine.nextFrame(registers);
        }
        return registers;
    }

    private static PscEngine engine() throws IOException {
        return new PscEngine(PscReader.read(TestPscs.psc()));
    }
}

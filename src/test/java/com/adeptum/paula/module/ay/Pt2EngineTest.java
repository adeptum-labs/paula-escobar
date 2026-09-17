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

import com.adeptum.paula.testing.TestPt2s;
import java.io.IOException;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class Pt2EngineTest {

    private static final int TONE_A = 0;
    private static final int TONE_B = 1;
    private static final int NOISE = 6;
    private static final int MIXER = 7;
    private static final int VOLUME_A = 8;
    private static final int VOLUME_B = 9;
    private static final int VOLUME_C = 10;
    private static final int ENVELOPE_PERIOD = 11;
    private static final int SHAPE = 13;
    private static final int BY_ENVELOPE = 0x10;
    private static final int TONE_AND_NOISE_A_OFF = 0x09;

    /**
     * The first line sets the tempo to four; the first channel acts every other line.
     */
    private static final int THIRD_LINE = 2 * TestPt2s.LINE_TEMPO;
    private static final int FIFTH_LINE = 4 * TestPt2s.LINE_TEMPO;
    private static final int FRAMES = (TestPt2s.FIRST_PATTERN_LINES + TestPt2s.SECOND_PATTERN_LINES)
            * TestPt2s.LINE_TEMPO;

    @Test
    void soundsTheNoteBentByTheSampleVibrato() throws IOException {
        assertEquals(Pt2Engine.TONES[TestPt2s.NOTE] + TestPt2s.VIBRATO, tone(frame(0), TONE_A));
    }

    /**
     * A channel given no ornament plays the unset one, which shifts its notes by the file's first byte.
     */
    @Test
    void shiftsTheNoteByTheOrnament() throws IOException {
        assertEquals(Pt2Engine.TONES[TestPt2s.NOTE + TestPt2s.ORNAMENT_STEP], tone(frame(1), TONE_A));
        assertEquals(Pt2Engine.TONES[TestPt2s.SECOND_NOTE + TestPt2s.TEMPO] + TestPt2s.VIBRATO,
                tone(frame(0), TONE_B));
    }

    /**
     * The level is the sample's scaled by the channel volume as the player's own sum does it.
     */
    @Test
    void scalesTheSampleLevelByTheChannelVolume() throws IOException {
        assertEquals((TestPt2s.VOLUME * 17 + 1) * TestPt2s.LEVEL / 256, frame(0)[VOLUME_A]);
        assertEquals((TestPt2s.VOLUME * 17 + 1) * TestPt2s.SECOND_LEVEL / 256, frame(1)[VOLUME_A]);
        assertEquals(0, frame(0)[VOLUME_C], "the third channel rests");
    }

    @Test
    void masksWhatTheSampleLineMasksAndSoundsItsNoise() throws IOException {
        assertEquals(0, frame(0)[MIXER] & TONE_AND_NOISE_A_OFF);
        assertEquals(TestPt2s.NOISE, frame(0)[NOISE]);
        assertEquals(TONE_AND_NOISE_A_OFF, frame(1)[MIXER] & TONE_AND_NOISE_A_OFF);
    }

    /**
     * Only the frame that names an envelope writes its shape; the channel sounds by it until told otherwise.
     */
    @Test
    void writesTheEnvelopeOnlyOnTheFrameThatNamesIt() throws IOException {
        assertEquals(TestPt2s.ENVELOPE_TYPE, frame(0)[SHAPE]);
        assertEquals(TestPt2s.ENVELOPE_TONE, frame(0)[ENVELOPE_PERIOD] | frame(0)[ENVELOPE_PERIOD + 1] << 8);
        assertEquals(RegisterFrames.SHAPE_UNTOUCHED, frame(1)[SHAPE]);
        assertEquals(BY_ENVELOPE, frame(1)[VOLUME_B] & BY_ENVELOPE);
        assertEquals(0, frame(TestPt2s.LINE_TEMPO)[VOLUME_B] & BY_ENVELOPE, "the second line turns it off");
    }

    @Test
    void addsTheChannelsNoiseAddon() throws IOException {
        assertEquals(TestPt2s.NOISE + TestPt2s.NOISE_ADD, frame(THIRD_LINE)[NOISE]);
    }

    /**
     * A glissando to a note starts from the note as it stood and moves by its step each interrupt.
     */
    @Test
    void glidesTowardTheTargetByItsStep() throws IOException {
        assertEquals(Pt2Engine.TONES[TestPt2s.NOTE] + TestPt2s.VIBRATO, tone(frame(THIRD_LINE), TONE_A));
        assertEquals(Pt2Engine.TONES[TestPt2s.NOTE + TestPt2s.ORNAMENT_STEP] + TestPt2s.GLISS_STEP,
                tone(frame(THIRD_LINE + 1), TONE_A));
    }

    @Test
    void silencesARestingChannel() throws IOException {
        assertEquals(0, frame(FIFTH_LINE)[VOLUME_A]);
    }

    @Test
    void runsForAsLongAsTheOrderTakesAtTheTemposItSets() throws IOException {
        assertEquals(OptionalLong.of(FRAMES), engine().frames());
    }

    @Test
    void endsWhenTheOrderHasRunOut() throws IOException {
        final Pt2Engine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (long at = 0; at < FRAMES; at++) {
            assertTrue(engine.nextFrame(registers), "frame " + at + " should sound");
        }

        assertFalse(engine.nextFrame(registers));
    }

    @Test
    void goesBackToAFrameAndPlaysOnFromThere() throws IOException {
        final Pt2Engine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (int at = 0; at < FRAMES; at++) {
            engine.nextFrame(registers);
        }

        engine.rewindTo(THIRD_LINE + 1);
        engine.nextFrame(registers);

        assertEquals(tone(frame(THIRD_LINE + 1), TONE_A), tone(registers, TONE_A));
    }

    private static int tone(int[] registers, int channel) {
        return registers[channel * 2] | registers[channel * 2 + 1] << 8;
    }

    private static int[] frame(int at) throws IOException {
        final Pt2Engine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (int frame = 0; frame <= at; frame++) {
            engine.nextFrame(registers);
        }
        return registers;
    }

    private static Pt2Engine engine() throws IOException {
        return new Pt2Engine(Pt2Reader.read(TestPt2s.pt2()));
    }
}

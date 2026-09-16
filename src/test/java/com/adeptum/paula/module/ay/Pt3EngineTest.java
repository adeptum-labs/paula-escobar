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

import com.adeptum.paula.testing.TestPt3s;
import java.io.IOException;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class Pt3EngineTest {

    private static final int TONE_A = 0;
    private static final int NOISE = 6;
    private static final int VOLUME_A = 8;
    private static final int VOLUME_B = 9;
    private static final int SHAPE = 13;
    private static final int BY_ENVELOPE = 0x10;

    /**
     * The first line sets the tempo to four, the rest on the second line comes four frames in, and the pattern
     * runs five lines before the one-line second pattern.
     */
    private static final int SECOND_LINE = TestPt3s.LINE_TEMPO;
    private static final int FOURTH_LINE = 3 * TestPt3s.LINE_TEMPO;
    private static final int SECOND_PATTERN = TestPt3s.FIRST_PATTERN_LINES * TestPt3s.LINE_TEMPO;

    /**
     * The note is looked up in the table the module names, as its version tuned it, and bent by the sample
     * line's tone offset.
     */
    @Test
    void soundsTheNoteFromTheNamedTableBentByTheSample() throws IOException {
        final int[] tones = Pt3Tables.tones(TestPt3s.TABLE, TestPt3s.VERSION);

        assertEquals((tones[TestPt3s.NOTE] + TestPt3s.TONE_OFFSET) & 0xfff, tone(frame(0), TONE_A));
    }

    /**
     * The level is the sample's, raised by the slide the line carries, and scaled by the channel's volume
     * through the table of the version that saved it.
     */
    @Test
    void scalesTheSampleLevelByTheChannelVolume() throws IOException {
        final int level = Pt3Tables.level(TestPt3s.VERSION, TestPt3s.VOLUME, TestPt3s.LEVEL + 1);

        assertEquals(level, frame(0)[VOLUME_A] & 0xf);
    }

    @Test
    void holdsALineForTheTempoItSetsAndThenRests() throws IOException {
        assertTrue((frame(SECOND_LINE - 1)[VOLUME_A] & 0xf) > 0, "the note still sounds on the fourth frame");
        assertEquals(0, frame(SECOND_LINE)[VOLUME_A] & 0xf, "the rest comes in on the fifth");
    }

    /**
     * Only the frame that names an envelope writes its shape; a sample line that does not mask it lets the
     * channel sound by it.
     */
    @Test
    void writesTheEnvelopeShapeOnlyOnTheFrameThatNamesIt() throws IOException {
        assertEquals(TestPt3s.ENVELOPE_TYPE, frame(0)[SHAPE]);
        assertEquals(RegisterFrames.SHAPE_UNTOUCHED, frame(1)[SHAPE]);
        assertEquals(0, frame(0)[VOLUME_B] & BY_ENVELOPE, "the first sample line masks the envelope");
        assertEquals(BY_ENVELOPE, frame(1)[VOLUME_B] & BY_ENVELOPE, "the second does not");
    }

    /**
     * The noise base goes back to nothing at the start of every pattern.
     */
    @Test
    void startsTheNoiseBaseOverWithEachPattern() throws IOException {
        assertEquals(TestPt3s.NOISE_BASE, frame(0)[NOISE]);
        assertEquals(0, frame(SECOND_PATTERN)[NOISE]);
    }

    /**
     * A glissando to a note moves the tone by its step each period, starting from the note as it stood.
     */
    @Test
    void slidesTowardTheTargetNoteByItsStep() throws IOException {
        final int[] tones = Pt3Tables.tones(TestPt3s.TABLE, TestPt3s.VERSION);
        final int held = (tones[TestPt3s.NOTE] + TestPt3s.TONE_OFFSET) & 0xfff;

        assertEquals(held, tone(frame(FOURTH_LINE), TONE_A));
        assertEquals((held - TestPt3s.SLIDE_STEP) & 0xfff, tone(frame(FOURTH_LINE + 1), TONE_A),
                "the target is higher, so the period falls");
    }

    /**
     * The tempo a line sets carries on into the next pattern.
     */
    @Test
    void runsForAsLongAsTheOrderTakesAtTheTemposItSets() throws IOException {
        final long frames = SECOND_PATTERN + TestPt3s.LINE_TEMPO;

        assertEquals(OptionalLong.of(frames), engine().frames());
    }

    @Test
    void endsWhenTheOrderHasRunOut() throws IOException {
        final Pt3Engine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        final long frames = engine.frames().orElseThrow();
        for (long at = 0; at < frames; at++) {
            assertTrue(engine.nextFrame(registers), "frame " + at + " should sound");
        }

        assertFalse(engine.nextFrame(registers));
    }

    @Test
    void goesBackToAFrameAndPlaysOnFromThere() throws IOException {
        final Pt3Engine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (int at = 0; at < SECOND_PATTERN; at++) {
            engine.nextFrame(registers);
        }

        engine.rewindTo(FOURTH_LINE + 1);
        engine.nextFrame(registers);

        assertEquals(tone(frame(FOURTH_LINE + 1), TONE_A), tone(registers, TONE_A));
    }

    private static int tone(int[] registers, int channel) {
        return registers[channel * 2] | registers[channel * 2 + 1] << 8;
    }

    private static int[] frame(int at) throws IOException {
        final Pt3Engine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (int frame = 0; frame <= at; frame++) {
            engine.nextFrame(registers);
        }
        return registers;
    }

    private static Pt3Engine engine() throws IOException {
        return new Pt3Engine(Pt3Reader.read(TestPt3s.pt3()));
    }
}

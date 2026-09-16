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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestStcs;
import java.io.IOException;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class StcEngineTest {

    private static final int TONE_A = 0;
    private static final int NOISE = 6;
    private static final int MIXER = 7;
    private static final int VOLUME_A = 8;
    private static final int VOLUME_B = 9;
    private static final int ENVELOPE_PERIOD = 11;
    private static final int SHAPE = 13;

    private static final int TONE_A_OFF = 1;
    private static final int NOISE_A_OFF = 8;
    private static final int BY_ENVELOPE = 0x10;

    /**
     * The note is looked up in SoundTracker's own table and bent by what the sample line asks for.
     */
    @Test
    void soundsTheNoteAtTheTableToneBentBySample() throws IOException {
        final int[] registers = frame(0);

        assertEquals((StcEngine.TONES[TestStcs.NOTE] - TestStcs.EFFECT) & 0xfff,
                registers[TONE_A] | registers[TONE_A + 1] << 8);
    }

    @Test
    void takesTheLevelFromTheSampleLine() throws IOException {
        assertEquals(TestStcs.LEVEL, frame(0)[VOLUME_A] & 0xf);
    }

    /**
     * The sample line keeps the noise and gives up the tone, which is what the enveloped lines do.
     */
    @Test
    void letsTheSampleSayWhichGeneratorsAreHeard() throws IOException {
        final int[] registers = frame(0);

        assertEquals(TONE_A_OFF, registers[MIXER] & TONE_A_OFF, "the sample line gives up its tone");
        assertEquals(0, registers[MIXER] & NOISE_A_OFF, "and keeps its noise");
        assertEquals(TestStcs.NOISE, registers[NOISE]);
    }

    @Test
    void silencesAChannelThatIsToldToRest() throws IOException {
        final StcEngine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (int at = 0; at <= TestStcs.TEMPO * 2; at++) {
            engine.nextFrame(registers);
        }

        assertEquals(0, registers[VOLUME_A] & 0xf, "the rest on the third line should have silenced it");
    }

    /**
     * An envelope command writes the shape once; the frames after it leave the register alone, or the
     * envelope would be started over before it had moved.
     */
    @Test
    void writesTheEnvelopeShapeOnlyOnTheFrameThatAsksForIt() throws IOException {
        final StcEngine engine = engine();
        final int[] first = new int[RegisterFrames.REGISTERS];
        engine.nextFrame(first);
        final int[] second = new int[RegisterFrames.REGISTERS];
        engine.nextFrame(second);

        assertEquals(TestStcs.ENVELOPE, first[SHAPE]);
        assertEquals(TestStcs.ENVELOPE_PERIOD, first[ENVELOPE_PERIOD]);
        assertEquals(BY_ENVELOPE, first[VOLUME_B] & BY_ENVELOPE, "the channel sounds by the envelope");
        assertEquals(RegisterFrames.SHAPE_UNTOUCHED, second[SHAPE]);
    }

    /**
     * The second position transposes what the pattern holds, so the same note comes out at a different pitch.
     */
    @Test
    void shiftsEveryNoteByWhatThePositionTransposes() throws IOException {
        final StcEngine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        final int[] first = frame(0);
        for (int at = 0; at < TestStcs.TEMPO * StcReader.read(TestStcs.stc()).patterns()[0].length(); at++) {
            engine.nextFrame(registers);
        }
        engine.nextFrame(registers);

        assertNotEquals(first[TONE_A] | first[TONE_A + 1] << 8, registers[TONE_A] | registers[TONE_A + 1] << 8);
    }

    @Test
    void runsForAsLongAsTheOrderTakes() throws IOException {
        final StcFile file = StcReader.read(TestStcs.stc());
        final long lines = (long) file.patterns()[0].length() * file.positions().length;

        assertEquals(OptionalLong.of(lines * file.tempo()), engine().frames());
    }

    @Test
    void endsWhenTheOrderHasRunOut() throws IOException {
        final StcEngine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        final long frames = engine.frames().orElseThrow();
        for (long at = 0; at < frames; at++) {
            assertTrue(engine.nextFrame(registers), "frame " + at + " of " + frames + " should sound");
        }

        assertEquals(false, engine.nextFrame(registers));
    }

    private static int[] frame(int at) throws IOException {
        final StcEngine engine = engine();
        final int[] registers = new int[RegisterFrames.REGISTERS];
        for (int frame = 0; frame <= at; frame++) {
            engine.nextFrame(registers);
        }
        return registers;
    }

    private static StcEngine engine() throws IOException {
        return new StcEngine(StcReader.read(TestStcs.stc()));
    }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.adeptum.paula.testing.TestModules;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * The waveform of the fixture is the square wave {@code TestModules.proTracker()} writes: half a period at a
 * hundred and half at minus a hundred. The generated module keeps it as it stands and the packed one has it
 * delta compressed, so reading either back has to give the same square.
 *
 * <p>The waveform is kept past a run of samples the mixer reads backwards off the start, so it begins at the
 * look-ahead rather than at nothing.</p>
 */
class Mo3WaveformsTest {

    private static final String FIXTURE = "/mo3/paula-test.mo3";
    private static final String NAME = "test.mo3";
    private static final int SAMPLE_LENGTH = 64;
    private static final int LOUD = 100;
    private static final int EIGHT_BIT_SHIFT = 24;

    @Test
    void readsAWaveformThatIsNotPackedAtAll() throws IOException {
        assertSquareWave(sampleOf(Mo3Module.of(NAME, TestModules.mo3())));
    }

    @Test
    void unpacksTheWaveformTheCompressorDeltaPacked() throws IOException {
        assertSquareWave(sampleOf(Mo3Module.of(NAME, fixture())));
    }

    @Test
    void leavesRoomForTheMixerToReadPastTheEnd() throws IOException {
        final Sample sample = sampleOf(Mo3Module.of(NAME, fixture()));

        assertEquals(SAMPLE_LENGTH, sample.sampleLength);
        assertEquals(SAMPLE_LENGTH + 10 * Sample.INTERPOLATION_LOOK_AHEAD, sample.sampleL.length);
    }

    @Test
    void soundsNothingForASampleWithNoWaveform() throws IOException {
        assertNull(sampleOf(Mo3Module.of(NAME, fixture()), 1).sampleL,
                "an empty slot is left without one rather than filled with silence");
    }

    private static void assertSquareWave(Sample sample) {
        assertNotNull(sample.sampleL);
        assertNull(sample.sampleR, "the fixture is one channel");
        for (int at = 0; at < SAMPLE_LENGTH; at++) {
            final long expected = (long) (at < SAMPLE_LENGTH / 2 ? LOUD : -LOUD) << EIGHT_BIT_SHIFT;
            assertEquals(expected, sample.sampleL[Sample.INTERPOLATION_LOOK_AHEAD + at], "sample " + at);
        }
    }

    private static Sample sampleOf(Mo3Module module) {
        return sampleOf(module, 0);
    }

    private static Sample sampleOf(Mo3Module module, int index) {
        return module.getInstrumentContainer().getSample(index);
    }

    private static byte[] fixture() throws IOException {
        try (InputStream fixture = Mo3WaveformsTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(fixture, FIXTURE);
            return fixture.readAllBytes();
        }
    }
}

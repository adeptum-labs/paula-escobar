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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.instrument.Envelope;
import de.quippy.javamod.multimedia.mod.loader.instrument.Instrument;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * The instruments and samples as the tracker that wrote the module kept them, read out of the generated
 * fixture and out of the one the compressor packed.
 */
class Mo3InstrumentsTest {

    private static final String FIXTURE = "/mo3/paula-test.mo3";
    private static final String NAME = "test.mo3";
    private static final int SAMPLE_LENGTH = 64;
    private static final int MIDDLE_KEY = 60;
    private static final int SHALLOWEST_FREQUENCY = 256;

    @Test
    void readsTheSampleTheModuleSounds() throws IOException {
        final Sample sample = sampleOf(generated(), 0);

        assertEquals(TestModules.SAMPLE_NAME, sample.name);
        assertEquals(SAMPLE_LENGTH, sample.sampleLength);
        assertEquals(0, sample.loopStart);
        assertEquals(SAMPLE_LENGTH, sample.loopStop);
        assertEquals(SAMPLE_LENGTH, sample.loopLength);
        assertEquals(TestModules.MO3_LOUDEST, sample.volume);
        assertFalse(sample.isStereo);
    }

    @Test
    void readsTheLoopASampleHas() throws IOException {
        assertEquals(ModConstants.LOOP_ON, sampleOf(generated(), 0).loopType);
    }

    @Test
    void tunesAProTrackerSampleInSixteenthsOfASemitone() throws IOException {
        final Sample sample = sampleOf(generated(), 0);

        assertEquals(0, sample.fineTune, "the fixture is tuned to the middle");
        assertEquals(ModConstants.IT_fineTuneTable[8], sample.baseFrequency);
        assertEquals(0, sample.transpose);
    }

    @Test
    void tunesAnImpulseTrackerSampleInHertz() throws IOException {
        final Sample sample = sampleOf(ofKind(TestModules.MO3_IS_IMPULSE_TRACKER), 0);

        assertEquals(SHALLOWEST_FREQUENCY, sample.baseFrequency,
                "the fixture is tuned lower than a sample may be played at, and is lifted to it");
        assertEquals(0, sample.fineTune, "Impulse Tracker names the frequency rather than tuning to it");
        assertEquals(0, sample.transpose);
    }

    @Test
    void readsTheInstrumentThatSoundsTheSample() throws IOException {
        final Instrument instrument = instrumentOf(generated(), 0);

        assertEquals(TestModules.INSTRUMENT_NAME, instrument.name);
        assertEquals(TestModules.MO3_FADE_OUT, instrument.volumeFadeOut);
        assertFalse(instrument.setPanning, "the fixture leaves the panning to the channel");
    }

    @Test
    void namesTheSampleEveryKeyPlays() throws IOException {
        final Instrument instrument = instrumentOf(generated(), 0);

        assertEquals(1, instrument.sampleIndex[MIDDLE_KEY], "the samples are counted from one");
        assertEquals(MIDDLE_KEY, instrument.noteIndex[MIDDLE_KEY], "and the notes from nothing");
    }

    @Test
    void readsTheVolumeEnvelopeOfAnInstrument() throws IOException {
        final Envelope envelope = instrumentOf(generated(), 0).volumeEnvelope;

        assertNotNull(envelope);
        assertTrue(envelope.on);
        assertEquals(2, envelope.nPoints);
        assertEquals(0, envelope.positions[0]);
        assertEquals(TestModules.MO3_LOUDEST, envelope.value[0]);
        assertEquals(TestModules.MO3_ENVELOPE_END, envelope.positions[1]);
        assertEquals(0, envelope.value[1], "falling to nothing");
    }

    @Test
    void leavesTheEnvelopesTheInstrumentDoesNotUseOff() throws IOException {
        assertFalse(instrumentOf(generated(), 0).panningEnvelope.on);
    }

    @Test
    void givesAFastTrackerInstrumentNoPitchEnvelope() throws IOException {
        final Instrument instrument = instrumentOf(ofKind(TestModules.MO3_IS_FAST_TRACKER), 0);

        assertNull(instrument.pitchEnvelope, "Fast Tracker has none of its own");
        assertEquals(Mo3Instrument.FAST_TRACKER_KEYS, instrument.sampleIndex.length);
    }

    @Test
    void readsTheSampleTheCompressorPacked() throws IOException {
        final Sample sample = sampleOf(Mo3Module.of(NAME, fixture()), 0);

        assertEquals(TestModules.SAMPLE_NAME, sample.name);
        assertEquals(SAMPLE_LENGTH, sample.sampleLength);
        assertEquals(ModConstants.LOOP_ON, sample.loopType);
        assertEquals(TestModules.MO3_LOUDEST, sample.volume);
    }

    private static Sample sampleOf(Mo3Module module, int index) {
        return module.getInstrumentContainer().getSample(index);
    }

    private static Instrument instrumentOf(Mo3Module module, int index) {
        return module.getInstrumentContainer().getInstrument(index);
    }

    private static Mo3Module generated() throws IOException {
        return Mo3Module.of(NAME, TestModules.mo3());
    }

    private static Mo3Module ofKind(int flag) throws IOException {
        final byte[] music = TestModules.mo3Music();
        final int flags = flag | TestModules.MO3_ALWAYS_SET | TestModules.MO3_INSTRUMENT_MODE_FLAG;
        for (int at = 0; at < Integer.BYTES; at++) {
            music[TestModules.MO3_FLAGS_AT + at] = (byte) (flags >> (at * Byte.SIZE));
        }
        return Mo3Module.of(NAME, TestModules.mo3(music));
    }

    private static byte[] fixture() throws IOException {
        try (InputStream fixture = Mo3InstrumentsTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(fixture, FIXTURE);
            return fixture.readAllBytes();
        }
    }
}

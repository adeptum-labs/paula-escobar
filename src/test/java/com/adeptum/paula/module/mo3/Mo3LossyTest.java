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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * The two fixtures are the module {@code TestModules.proTrackerChirp()} writes, packed by MO3ENC 2.4.2.2 of
 * Un4seen Developments at its smallest setting with the Ogg Vorbis and the LAME encoder respectively, which is
 * what makes it keep the one sample lossily rather than whole.
 *
 * <p>Neither can come back as it went in, so what is asked of them is the length, the loudness and the shape:
 * a sweep read back has to still be that sweep, and a decoder that lost its place or its tuning would not
 * follow it.</p>
 */
class Mo3LossyTest {

    private static final String OGG = "/mo3/ogg-test.mo3";
    private static final String MPEG = "/mo3/mpeg-test.mo3";
    private static final String NAME = "test.mo3";

    /**
     * A lossy encoder rounds every sample, so the shape is what survives; anything below this would be a
     * waveform that has lost its place rather than its precision.
     */
    private static final double CLOSE_ENOUGH = 0.95;

    private static final int SIXTEEN_BIT_SHIFT = 16;
    private static final int EIGHT_BIT_PEAK = 128;

    @Test
    void packsTheFixturesTheWayTheTestNeeds() throws IOException {
        assertEquals(Mo3Sample.OGG, sampleHeader(OGG).compression());
        assertEquals(Mo3Sample.MPEG, sampleHeader(MPEG).compression());
    }

    @Test
    void soundsTheSweepKeptAsOggVorbis() throws IOException {
        assertFollowsTheSweep(waveform(OGG));
    }

    @Test
    void soundsTheSweepKeptAsMpegAudio() throws IOException {
        assertFollowsTheSweep(waveform(MPEG));
    }

    @Test
    void keepsTheLengthTheSampleHeaderStates() throws IOException {
        assertEquals(TestModules.CHIRP_LENGTH, waveform(OGG).sampleLength);
        assertEquals(TestModules.CHIRP_LENGTH, waveform(MPEG).sampleLength);
    }

    private static void assertFollowsTheSweep(Sample sample) {
        final byte[] wanted = TestModules.chirpSample();
        assertNotNull(sample.sampleL);

        double together = 0;
        double read = 0;
        double written = 0;
        for (int at = 0; at < wanted.length; at++) {
            final double heard = sample.sampleL[Sample.INTERPOLATION_LOOK_AHEAD + at] >> SIXTEEN_BIT_SHIFT;
            final double meant = (double) wanted[at] * EIGHT_BIT_PEAK;
            together += heard * meant;
            read += heard * heard;
            written += meant * meant;
        }
        final double follows = together / Math.sqrt(read * written);

        assertTrue(follows > CLOSE_ENOUGH, "the waveform follows the sweep it was made from, at " + follows);
    }

    private static Sample waveform(String fixture) throws IOException {
        return Mo3Module.of(NAME, read(fixture)).getInstrumentContainer().getSample(0);
    }

    private static Mo3Sample sampleHeader(String fixture) throws IOException {
        return Mo3Reader.read(read(fixture)).samples().getFirst();
    }

    private static byte[] read(String fixture) throws IOException {
        try (InputStream bytes = Mo3LossyTest.class.getResourceAsStream(fixture)) {
            assertNotNull(bytes, fixture);
            return bytes.readAllBytes();
        }
    }
}

/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

package com.adeptum.paula.module.digibooster;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DbmReaderTest {

    private static final int C4 = 4;

    private static DbmFile module() throws IOException {
        return DbmReader.read(TestModules.digiBooster());
    }

    @Test
    void readsWhatTheModuleSaysAboutItself() throws IOException {
        final DbmFile module = module();

        assertEquals(TestModules.TITLE, module.name());
        assertEquals("DigiBooster Pro 2.21", module.creator());
        assertEquals(TestModules.DBM_TRACKS, module.tracks());
        assertEquals(1, module.patterns().size());
    }

    @Test
    void readsTheSongAndItsPlaylist() throws IOException {
        final DbmSong song = module().songs().getFirst();

        assertEquals(TestModules.SONG_NAME, song.name());
        assertArrayEquals(new int[TestModules.DBM_ORDERS], song.playList());
    }

    @Test
    void readsInstrumentsWithTheirSampleAndLoop() throws IOException {
        final DbmInstrument instrument = module().instruments().getFirst();

        assertEquals(TestModules.SAMPLE_NAME, instrument.name());
        assertEquals(0, instrument.sample(), "samples are numbered from one in the file and from zero here");
        assertEquals(TestModules.C3_FREQUENCY, instrument.c3Frequency());
        assertEquals(DbmInstrument.FORWARD_LOOP, instrument.loopType());
    }

    @Test
    void keepsEightBitSamplesAsSixteenBitFrames() throws IOException {
        final short[] sample = module().samples().getFirst();

        assertEquals(100 << 8, sample[0]);
        assertEquals(-100 << 8, sample[sample.length - 1]);
    }

    @Test
    void unpacksPatternEntriesOntoTheirTrackAndRow() throws IOException {
        final DbmFile module = module();
        final DbmPattern pattern = module.patterns().getFirst();

        final DbmEntry entry = pattern.entry(0, 1, module.tracks());
        assertEquals(C4, entry.octave());
        assertEquals(0, entry.note(), "a C");
        assertEquals(1, entry.instrument());
        assertEquals(DbmEntry.EMPTY, pattern.entry(0, 0, module.tracks()));
        assertEquals(TestModules.DBM_ROWS, pattern.rows());
    }

    @Test
    void givesEachEnvelopeToTheInstrumentItNames() throws IOException {
        final DbmFile module = module();
        final DbmEnvelope envelope = module.volumeEnvelopes().getFirst();

        assertEquals(0, module.instruments().getFirst().volumeEnvelope());
        assertEquals(DbmEnvelope.NONE, module.instruments().getFirst().panningEnvelope());
        assertEquals(2, envelope.sections());
        assertEquals(0, envelope.loopFirst());
        assertEquals(2, envelope.loopLast());
        assertEquals(64, envelope.values()[0]);
        assertEquals(20, envelope.positions()[2]);
    }

    @Test
    void readsTheEchoAndTheTracksItPlaysOn() throws IOException {
        final DbmEcho echo = module().echo();

        assertEquals(TestModules.ECHO_DELAY, echo.delay());
        assertEquals(TestModules.ECHO_FEEDBACK, echo.feedback());
        assertEquals(TestModules.ECHO_MIX, echo.mix());
        assertEquals(TestModules.ECHO_CROSS, echo.cross());
        assertTrue(echo.tracks()[0], "a clear mask byte switches the echo on");
        assertFalse(echo.tracks()[1]);
    }

    @Test
    void modulesWithoutAnEchoChunkPlayDry() throws IOException {
        final byte[] file = TestModules.digiBooster();
        final int dspe = indexOf(file, "DSPE");

        final DbmEcho echo = DbmReader.read(Arrays.copyOf(file, dspe)).echo();

        assertEquals(DbmEcho.DEFAULT_DELAY, echo.delay());
        assertFalse(echo.tracks()[0]);
    }

    @Test
    void refusesFilesThatAreNotDigiBoosterModules() {
        assertFalse(DbmReader.isDbm(TestModules.proTracker()));
        assertThrows(IOException.class, () -> DbmReader.read(TestModules.proTracker()));
    }

    @Test
    void refusesVersionsItDoesNotKnow() {
        final byte[] file = TestModules.digiBooster();
        file[4] = 4;

        assertThrows(IOException.class, () -> DbmReader.read(file));
    }

    @Test
    void refusesChunksThatRunPastTheEndOfTheFile() throws IOException {
        final byte[] file = TestModules.digiBooster();

        assertThrows(IOException.class, () -> DbmReader.read(Arrays.copyOf(file, indexOf(file, "SMPL") + 12)));
    }

    @Test
    void refusesContentsTheModuleDidNotAnnounce() {
        final byte[] file = TestModules.digiBooster();
        file[indexOf(file, "INFO") + 12] = 3;

        assertThrows(IOException.class, () -> DbmReader.read(file), "three patterns are announced but one is there");
    }

    private static int indexOf(byte[] file, String chunk) {
        final byte[] id = chunk.getBytes(StandardCharsets.US_ASCII);
        for (int at = 0; at + id.length <= file.length; at++) {
            if (Arrays.equals(file, at, at + id.length, id, 0, id.length)) {
                return at;
            }
        }
        throw new IllegalArgumentException("No " + chunk + " chunk");
    }
}

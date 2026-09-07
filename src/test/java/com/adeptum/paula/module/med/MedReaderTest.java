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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MedReaderTest {

    private static final int TRACKS_AT = 0;
    private static final int SAMPLE_LENGTH = 64;
    private static final int TOO_MANY = 0xFF;

    @Test
    void readsWhatTheSongSaysAboutItself() throws IOException {
        final MedFile file = MedReader.read(TestModules.medMmd0());

        assertEquals(TestModules.SONG_NAME, file.name());
        assertEquals(TestModules.MED_TEMPO, file.song().tempo());
        assertEquals(TestModules.MED_SPEED, file.song().tempo2());
        assertEquals(1, file.song().length(), "one position in the play sequence");
        assertEquals(TestModules.MED_TRACKS, file.tracks());
    }

    @Test
    void readsTheLinesOfAThreeByteBlock() throws IOException {
        final MedBlock block = MedReader.read(TestModules.medMmd0()).block(0);

        assertEquals(TestModules.MED_TRACKS, block.tracks());
        assertEquals(TestModules.MED_LINES, block.lines());
        assertEquals(TestModules.MED_NOTE, block.entry(0, TRACKS_AT).note());
        assertEquals(1, block.entry(0, TRACKS_AT).instrument());
        assertEquals(0, block.entry(1, TRACKS_AT).note(), "and nothing on the lines after it");
    }

    /**
     * The two forms carry the same song, so what comes out of them has to be the same song too.
     */
    @Test
    void readsTheFourByteFormTheSameWay() throws IOException {
        final MedBlock wide = MedReader.read(TestModules.medMmd1()).block(0);
        final MedBlock narrow = MedReader.read(TestModules.medMmd0()).block(0);

        assertEquals(narrow.tracks(), wide.tracks());
        assertEquals(narrow.lines(), wide.lines());
        assertEquals(narrow.entry(0, TRACKS_AT), wide.entry(0, TRACKS_AT));
    }

    @Test
    void unpacksTheCountedRunsOfACompressedBlock() throws IOException {
        final MedBlock packed = MedReader.read(TestModules.medCompressed()).block(0);
        final MedBlock plain = MedReader.read(TestModules.medMmd0()).block(0);

        assertEquals(plain.lines(), packed.lines());
        assertEquals(plain.entry(0, TRACKS_AT), packed.entry(0, TRACKS_AT));
        assertEquals(plain.entry(3, 1), packed.entry(3, 1), "and the silence the count stood for");
    }

    @Test
    void readsTheInstrumentAndItsLoop() throws IOException {
        final MedInstrument instrument = MedReader.read(TestModules.medMmd0()).instrument(1);

        assertNotNull(instrument);
        assertEquals(TestModules.MED_VOLUME, instrument.volume());
        assertEquals(SAMPLE_LENGTH, instrument.sample().length, "a frame for every byte the file held");
        assertTrue(instrument.loops(), "the settings give it a loop");
        assertFalse(instrument.isSilent());
    }

    @Test
    void refusesAFileThatIsNotAModule() {
        assertThrows(IOException.class, () -> MedReader.read("not a module at all".getBytes()));
        assertFalse(MedReader.looksLikeMed(new byte[]{'M', 'M', 'D'}));
        assertTrue(MedReader.looksLikeMed(TestModules.medMmd0()));
    }

    @Test
    void refusesAModuleThatEndsInTheMiddleOfItself() {
        final byte[] file = TestModules.medMmd0();

        assertThrows(IOException.class, () -> MedReader.read(Arrays.copyOf(file, file.length / 2)));
    }

    @Test
    void refusesABlockOfMoreTracksThanTheFormatHolds() {
        final byte[] file = TestModules.medMmd0();
        final int blockAt = blockOffset(file);
        file[blockAt] = (byte) TOO_MANY;

        assertThrows(IOException.class, () -> MedReader.read(file));
    }

    private static int blockOffset(byte[] file) {
        int at = 0;
        for (int index = 16; index < 20; index++) {
            at = at << 8 | file[index] & 0xFF;
        }
        int block = 0;
        for (int index = at; index < at + 4; index++) {
            block = block << 8 | file[index] & 0xFF;
        }
        return block;
    }
}

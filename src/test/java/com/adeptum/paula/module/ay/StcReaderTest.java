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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestStcs;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class StcReaderTest {

    @Test
    void readsWhatTheHeaderNames() throws IOException {
        final StcFile file = StcReader.read(TestStcs.stc());

        assertEquals(TestStcs.TEMPO, file.tempo());
        assertEquals(TestStcs.TITLE, file.title());
    }

    @Test
    void readsThePositionsAndWhatTheyTranspose() throws IOException {
        final StcFile file = StcReader.read(TestStcs.stc());

        assertEquals(2, file.positions().length);
        assertEquals(0, file.positions()[0].pattern());
        assertEquals(0, file.positions()[0].transposition());
        assertEquals(0, file.positions()[1].pattern());
        assertEquals(TestStcs.TRANSPOSITION, file.positions()[1].transposition());
    }

    /**
     * A sample line packs the effect across two bytes with its sign in the masks, the level in the low nibble
     * and the noise in the low five bits.
     */
    @Test
    void unpacksASampleLine() throws IOException {
        final StcSample sample = StcReader.read(TestStcs.stc()).samples()[TestStcs.SAMPLE];

        assertEquals(TestStcs.LEVEL, sample.lines()[0].level());
        assertEquals(TestStcs.NOISE, sample.lines()[0].noise());
        assertFalse(sample.lines()[0].noiseOff());
        assertTrue(sample.lines()[0].toneOff());
        assertEquals(-TestStcs.EFFECT, sample.lines()[0].effect(), "the sign bit is clear, so it falls");
        assertEquals(TestStcs.SAMPLE_LOOP, sample.loop());
    }

    @Test
    void readsAnOrnamentAsItsOffsets() throws IOException {
        final int[] ornament = StcReader.read(TestStcs.stc()).ornaments()[TestStcs.ORNAMENT];

        assertEquals(StcFile.ORNAMENT_LENGTH, ornament.length);
        assertEquals(0, ornament[0]);
        assertEquals(TestStcs.ORNAMENT_STEP, ornament[1]);
    }

    /**
     * A channel is a stream of commands rather than a grid: notes end a line, a count above 0xa0 sets how many
     * lines to wait between them, and the other bytes change what the next note will sound with.
     */
    @Test
    void readsAChannelAsTheStreamOfCommandsItIs() throws IOException {
        final StcPattern pattern = StcReader.read(TestStcs.stc()).patterns()[0];

        assertEquals(TestStcs.NOTE, pattern.cell(0, 0).note());
        assertEquals(TestStcs.SAMPLE, pattern.cell(0, 0).sample());
        assertEquals(TestStcs.ORNAMENT, pattern.cell(0, 0).ornament());
        assertEquals(StcCell.NONE, pattern.cell(1, 0).note(), "the note waits out the count");
        assertEquals(StcCell.REST, pattern.cell(2, 0).note());
    }

    @Test
    void readsAnEnvelopeCommandAndItsPeriod() throws IOException {
        final StcPattern pattern = StcReader.read(TestStcs.stc()).patterns()[0];

        assertEquals(TestStcs.ENVELOPE, pattern.cell(0, 1).envelope());
        assertEquals(TestStcs.ENVELOPE_PERIOD, pattern.cell(0, 1).envelopePeriod());
        assertEquals(0, pattern.cell(0, 1).ornament(), "an envelope command carries ornament zero");
    }

    @Test
    void refusesWhatIsNotAnStc() {
        assertThrows(IOException.class, () -> StcReader.read(new byte[]{1, 2, 3}));
    }

    @Test
    void refusesAFileThatDoesNotEndWhereItSaysItDoes() throws IOException {
        final byte[] file = TestStcs.stc();
        file[25] = 0;
        file[26] = 0;

        assertThrows(IOException.class, () -> StcReader.read(file));
    }
}

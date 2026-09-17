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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.adeptum.paula.testing.TestPscs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PscReaderTest {

    private static final int A = 0;
    private static final int B = 1;
    private static final int C = 2;
    private static final int TEMPO_AT = 73;

    @Test
    void readsTheHeaderAndTheOrder() throws IOException {
        final PscFile file = read();

        assertEquals(TestPscs.TEMPO, file.tempo());
        assertEquals(TestPscs.LOOP, file.loop());
        assertEquals(TestPscs.TITLE, file.title());
        assertEquals(TestPscs.AUTHOR, file.author());
        assertArrayEquals(new int[]{0, 1}, file.positions());
    }

    @Test
    void readsTheSampleLinesUpToTheLastOne() throws IOException {
        final PscSampleLine[] lines = read().samples()[TestPscs.SAMPLE].lines();

        assertArrayEquals(new PscSampleLine[]{
            new PscSampleLine(TestPscs.LEVEL, TestPscs.TONE_STEP, false, false, TestPscs.NOISE_ADDING, true, 0,
                    true, false),
            new PscSampleLine(TestPscs.SECOND_LEVEL, TestPscs.TONE_STEP, true, true, 0, false, -1, false, true)},
                lines);
    }

    @Test
    void readsTheOrnamentLines() throws IOException {
        assertArrayEquals(new PscOrnamentLine[]{new PscOrnamentLine(0, 0, true, false),
            new PscOrnamentLine(TestPscs.ORNAMENT_STEP, TestPscs.ORNAMENT_NOISE, false, true)},
                read().ornaments()[TestPscs.ORNAMENT].lines());
    }

    /**
     * Before version 1.03 samples and ornaments are found from the start of the file rather than from their
     * tables.
     */
    @Test
    void findsTheSamplesOfAnOldModuleFromTheStartOfTheFile() throws IOException {
        final PscFile old = PscReader.read(TestPscs.psc(TestPscs.OLD_VERSION));

        assertArrayEquals(read().samples()[TestPscs.SAMPLE].lines(), old.samples()[TestPscs.SAMPLE].lines());
        assertArrayEquals(read().ornaments()[TestPscs.ORNAMENT].lines(),
                old.ornaments()[TestPscs.ORNAMENT].lines());
    }

    @Test
    void readsTheFirstLineOfEachChannel() throws IOException {
        final PscLine line = read().patterns()[0].line(0);

        assertEquals(TestPscs.LINE_TEMPO, line.tempo());
        assertEquals(new PscCell(PscCell.ON, TestPscs.NOTE, TestPscs.SAMPLE, TestPscs.ORNAMENT, TestPscs.VOLUME,
                List.of(new PscCommand(PscCommand.Kind.NO_ENVELOPE, 0, 0))), line.cell(A));
        assertEquals(new PscCell(PscCell.ON, TestPscs.SECOND_NOTE, TestPscs.SAMPLE, PscCell.KEEP, 0x0f, List.of(
                new PscCommand(PscCommand.Kind.ENVELOPE, TestPscs.ENVELOPE_TYPE, TestPscs.ENVELOPE_TONE),
                new PscCommand(PscCommand.Kind.NOISE_BASE, TestPscs.NOISE_BASE, 0),
                new PscCommand(PscCommand.Kind.ENVELOPE_ON, 0, 0))), line.cell(B));
        assertEquals(PscCell.OFF, line.cell(C).enabled());
    }

    @Test
    void waitsOutLinesBetweenEvents() throws IOException {
        final PscPattern pattern = read().patterns()[0];

        assertEquals(TestPscs.FIRST_PATTERN_LINES, pattern.length());
        assertEquals(PscLine.EMPTY, pattern.line(1));
        assertEquals(List.of(new PscCommand(PscCommand.Kind.GLISS, TestPscs.GLISS_STEP, 0)),
                pattern.line(2).cell(A).commands());
        assertEquals(PscCell.EMPTY, pattern.line(2).cell(B));
        assertEquals(PscCell.OFF, pattern.line(3).cell(A).enabled());
    }

    @Test
    void aRestSilencesTheChannelEvenWhereANoteFollowsIt() throws IOException {
        final PscCell cell = read().patterns()[1].line(0).cell(B);

        assertEquals(PscCell.OFF, cell.enabled());
        assertEquals(TestPscs.SECOND_NOTE, cell.note());
    }

    @Test
    void refusesWhatIsNotAProSoundCreatorModule() {
        assertThrows(IOException.class, () -> PscReader.read("not a module".getBytes(StandardCharsets.US_ASCII)));
        final byte[] slow = TestPscs.psc();
        slow[TEMPO_AT] = 0x20;
        assertThrows(IOException.class, () -> PscReader.read(slow), "a tempo the editor never wrote");
        final byte[] misplaced = TestPscs.psc();
        misplaced[76]++;
        assertThrows(IOException.class, () -> PscReader.read(misplaced), "samples not after their table");
    }

    private static PscFile read() throws IOException {
        return PscReader.read(TestPscs.psc());
    }
}

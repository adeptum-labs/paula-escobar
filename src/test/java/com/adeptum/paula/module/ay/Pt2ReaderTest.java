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

import com.adeptum.paula.testing.TestPt2s;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class Pt2ReaderTest {

    private static final int A = 0;
    private static final int B = 1;
    private static final int C = 2;

    @Test
    void readsTheHeaderAndTheOrder() throws IOException {
        final Pt2File file = read();

        assertEquals(TestPt2s.TEMPO, file.tempo());
        assertEquals(TestPt2s.LOOP, file.loop());
        assertEquals(TestPt2s.TITLE, file.title());
        assertArrayEquals(new int[]{0, 1}, file.positions());
    }

    /**
     * A line's first byte carries the noise and the masks, the vibrato's sign among them, and the other two
     * the level and the vibrato itself.
     */
    @Test
    void readsTheSampleLines() throws IOException {
        final Pt2Sample sample = read().samples()[TestPt2s.SAMPLE];

        assertEquals(TestPt2s.SAMPLE_LOOP, sample.loop());
        assertEquals(new Pt2SampleLine(TestPt2s.LEVEL, TestPt2s.NOISE, false, false, TestPt2s.VIBRATO),
                sample.line(0));
        assertEquals(new Pt2SampleLine(TestPt2s.SECOND_LEVEL, 0, true, true, 0), sample.line(1));
    }

    @Test
    void readsTheOrnament() throws IOException {
        final Pt2Ornament ornament = read().ornaments()[TestPt2s.ORNAMENT];

        assertArrayEquals(new int[]{0, TestPt2s.ORNAMENT_STEP}, ornament.offsets());
    }

    /**
     * An ornament the module leaves unset points at the start of the file, and plays its first byte as its
     * only line, as ZXTune reads it.
     */
    @Test
    void readsAnUnsetOrnamentFromTheStartOfTheFile() throws IOException {
        assertArrayEquals(new int[]{TestPt2s.TEMPO}, read().ornaments()[0].offsets());
    }

    @Test
    void readsTheFirstLineOfEachChannel() throws IOException {
        final Pt2Line line = read().patterns()[0].line(0);

        assertEquals(TestPt2s.LINE_TEMPO, line.tempo());
        assertEquals(new Pt2Cell(Pt2Cell.ON, TestPt2s.NOTE, TestPt2s.SAMPLE, TestPt2s.ORNAMENT, TestPt2s.VOLUME,
                List.of()), line.cell(A));
        assertEquals(new Pt2Cell(Pt2Cell.ON, TestPt2s.SECOND_NOTE, TestPt2s.SAMPLE, Pt2Cell.KEEP, Pt2Cell.KEEP,
                List.of(new Pt2Command(Pt2Command.Kind.ENVELOPE, TestPt2s.ENVELOPE_TYPE, TestPt2s.ENVELOPE_TONE))),
                line.cell(B));
        assertEquals(Pt2Cell.OFF, line.cell(C).enabled());
    }

    /**
     * A channel told to wait sits out that many lines; a glissando to a note takes the note as where it is
     * going, leaving the note that sounds as it was.
     */
    @Test
    void waitsOutLinesAndTakesAGlissandosNoteAsItsTarget() throws IOException {
        final Pt2Pattern pattern = read().patterns()[0];

        assertEquals(Pt2Cell.EMPTY, pattern.line(1).cell(A));
        assertEquals(new Pt2Cell(Pt2Cell.ON, Pt2Cell.KEEP, Pt2Cell.KEEP, Pt2Cell.KEEP, Pt2Cell.KEEP, List.of(
                new Pt2Command(Pt2Command.Kind.NOISE_ADD, TestPt2s.NOISE_ADD, 0),
                new Pt2Command(Pt2Command.Kind.GLISS_NOTE, TestPt2s.GLISS_STEP, TestPt2s.GLISS_TARGET))),
                pattern.line(2).cell(A));
        assertEquals(List.of(new Pt2Command(Pt2Command.Kind.NO_ENVELOPE, 0, 0)), pattern.line(1).cell(B).commands());
        assertEquals(Pt2Cell.OFF, pattern.line(4).cell(A).enabled());
    }

    /**
     * The first channel ends a pattern where a zero stands in place of its next command, and a pattern shorter
     * than five lines plays as five.
     */
    @Test
    void endsAPatternAtTheFirstChannelsZeroButNeverShorterThanFive() throws IOException {
        final Pt2File file = read();

        assertEquals(TestPt2s.FIRST_PATTERN_LINES, file.patterns()[0].length());
        assertEquals(TestPt2s.SECOND_PATTERN_LINES, file.patterns()[1].length());
    }

    @Test
    void refusesWhatIsNotAProTracker2Module() {
        assertThrows(IOException.class, () -> Pt2Reader.read("not a module".getBytes(StandardCharsets.US_ASCII)));
        final byte[] slow = TestPt2s.pt2();
        slow[0] = 1;
        assertThrows(IOException.class, () -> Pt2Reader.read(slow), "a tempo below two");
        final byte[] misplaced = TestPt2s.pt2();
        misplaced[99]++;
        assertThrows(IOException.class, () -> Pt2Reader.read(misplaced), "patterns not right after the order");
    }

    private static Pt2File read() throws IOException {
        return Pt2Reader.read(TestPt2s.pt2());
    }
}

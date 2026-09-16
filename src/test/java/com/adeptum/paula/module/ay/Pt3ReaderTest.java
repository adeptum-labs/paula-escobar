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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestPt3s;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class Pt3ReaderTest {

    private static final int A = 0;
    private static final int B = 1;

    @Test
    void readsTheHeader() throws IOException {
        final Pt3File file = read();

        assertEquals(TestPt3s.VERSION, file.version());
        assertEquals(TestPt3s.TABLE, file.table());
        assertEquals(TestPt3s.TEMPO, file.tempo());
        assertEquals(TestPt3s.LOOP, file.loop());
        assertEquals(TestPt3s.TITLE, file.title());
        assertEquals(TestPt3s.AUTHOR, file.author());
    }

    /**
     * Vortex Tracker II names itself in words rather than with a version digit, and plays as version six.
     */
    @Test
    void takesAVortexTrackerModuleForVersionSix() throws IOException {
        final byte[] bytes = TestPt3s.pt3();
        final byte[] mark = "Vortex Tracker II".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(mark, 0, bytes, 0, mark.length);

        assertEquals(6, Pt3Reader.read(bytes).version());
    }

    /**
     * Some modules were saved with the words at their head written over, so the module is known by its shape
     * and its version, having no digit to read, is taken as six.
     */
    @Test
    void readsAModuleWhoseHeaderWordsWereWrittenOver() throws IOException {
        final byte[] bytes = TestPt3s.pt3();
        java.util.Arrays.fill(bytes, 0, 30, (byte) 0x1f);

        final Pt3File file = Pt3Reader.read(bytes);

        assertEquals(6, file.version());
        assertEquals(TestPt3s.FIRST_PATTERN_LINES, file.patterns()[0].length());
    }

    @Test
    void refusesAModuleWithNoTempo() {
        final byte[] bytes = TestPt3s.pt3();
        bytes[100] = 0;

        assertThrows(IOException.class, () -> Pt3Reader.read(bytes));
    }

    @Test
    void readsThePositionsAsPatternNumbers() throws IOException {
        assertArrayEquals(new int[]{0, 1}, read().positions());
    }

    /**
     * A position counts patterns in threes, so anything else is not a position a tracker wrote.
     */
    @Test
    void refusesAPositionThatIsNotAPatternInThrees() {
        final byte[] bytes = TestPt3s.pt3();
        bytes[202] = 4;

        assertThrows(IOException.class, () -> Pt3Reader.read(bytes));
    }

    @Test
    void unpacksEveryFlagOfASampleLine() throws IOException {
        final Pt3Sample sample = read().samples()[TestPt3s.SAMPLE];
        final Pt3SampleLine first = sample.lines()[0];

        assertEquals(TestPt3s.LEVEL, first.level());
        assertEquals(1, first.volumeSlide());
        assertEquals(TestPt3s.NOISE_OFFSET, first.noiseOrEnvelopeOffset());
        assertTrue(first.envelopeOff());
        assertTrue(first.noiseOff());
        assertTrue(first.keepToneOffset());
        assertFalse(first.keepNoiseOrEnvelopeOffset());
        assertFalse(first.toneOff());
        assertEquals(TestPt3s.TONE_OFFSET, first.toneOffset());
        assertEquals(TestPt3s.SAMPLE_LOOP, sample.loop());
    }

    @Test
    void leavesAPlainLineWithNothingButItsLevel() throws IOException {
        final Pt3SampleLine second = read().samples()[TestPt3s.SAMPLE].lines()[1];

        assertEquals(TestPt3s.SECOND_LEVEL, second.level());
        assertEquals(0, second.volumeSlide());
        assertFalse(second.envelopeOff() || second.noiseOff() || second.toneOff());
    }

    @Test
    void readsAnOrnamentAndItsLoop() throws IOException {
        final Pt3Ornament ornament = read().ornaments()[0];

        assertArrayEquals(new int[]{0, TestPt3s.ORNAMENT_STEP}, ornament.offsets());
        assertEquals(TestPt3s.ORNAMENT_LOOP, ornament.loop());
    }

    @Test
    void readsWhatComesBeforeTheNote() throws IOException {
        final Pt3Cell cell = pattern().line(0).cell(A);

        assertEquals(Pt3Cell.ON, cell.enabled());
        assertEquals(TestPt3s.NOTE, cell.note());
        assertEquals(TestPt3s.SAMPLE, cell.sample());
        assertEquals(TestPt3s.ORNAMENT, cell.ornament());
        assertEquals(TestPt3s.VOLUME, cell.volume());
    }

    /**
     * Effects are named before the note and their parameters follow it, read in the reverse of the order the
     * effects were named; the tempo belongs to the line rather than to the channel.
     */
    @Test
    void readsEffectParametersAfterTheNoteInReverse() throws IOException {
        final Pt3Line line = pattern().line(0);

        assertEquals(TestPt3s.LINE_TEMPO, line.tempo());
        assertEquals(List.of(
                new Pt3Command(Pt3Command.Kind.ORNAMENT_OFFSET, TestPt3s.ORNAMENT_OFFSET, 0, 0),
                new Pt3Command(Pt3Command.Kind.GLISS, TestPt3s.GLISS_PERIOD, TestPt3s.GLISS_STEP, 0)),
                line.cell(A).commands());
    }

    @Test
    void readsARestAndTheLinesAChannelWaitsOut() throws IOException {
        final Pt3Pattern pattern = pattern();

        assertEquals(Pt3Cell.OFF, pattern.line(1).cell(A).enabled());
        assertEquals(Pt3Cell.EMPTY, pattern.line(2).cell(A));
    }

    /**
     * A glissando to a note takes the note as where it is going rather than as the note to sound.
     */
    @Test
    void takesTheNoteOfAGlissandoAsItsTarget() throws IOException {
        final Pt3Cell cell = pattern().line(3).cell(A);

        assertEquals(Pt3Cell.ON, cell.enabled());
        assertEquals(Pt3Cell.KEEP, cell.note());
        assertEquals(List.of(new Pt3Command(Pt3Command.Kind.GLISS_NOTE, TestPt3s.SLIDE_PERIOD, TestPt3s.SLIDE_STEP,
                TestPt3s.SLIDE_TARGET)), cell.commands());
    }

    /**
     * The envelope's period is written the other way round from every other number in the file.
     */
    @Test
    void readsAnEnvelopeAndANoiseBase() throws IOException {
        final Pt3Cell cell = pattern().line(0).cell(B);

        assertEquals(List.of(new Pt3Command(Pt3Command.Kind.ENVELOPE, TestPt3s.ENVELOPE_TYPE, TestPt3s.ENVELOPE_TONE, 0),
                new Pt3Command(Pt3Command.Kind.NOISE_BASE, TestPt3s.NOISE_BASE, 0, 0)), cell.commands());
        assertEquals(TestPt3s.SAMPLE, cell.sample());
        assertEquals(TestPt3s.SECOND_NOTE, cell.note());
    }

    @Test
    void endsAPatternWhereTheFirstChannelMeetsItsEnd() throws IOException {
        assertEquals(TestPt3s.FIRST_PATTERN_LINES, pattern().length());
        assertEquals(1, read().patterns()[1].length());
    }

    /**
     * A pattern whose first channel ends before it begins still takes one line, which says nothing.
     */
    @Test
    void readsAPatternThatEndsAtOnceAsOneLineOfNothing() throws IOException {
        final byte[] bytes = TestPt3s.pt3();
        final int table = (bytes[103] & 0xff) | (bytes[104] & 0xff) << 8;
        final int secondPatternFirstChannel = (bytes[table + 6] & 0xff) | (bytes[table + 7] & 0xff) << 8;
        bytes[secondPatternFirstChannel] = 0;

        final Pt3Pattern pattern = Pt3Reader.read(bytes).patterns()[1];

        assertEquals(1, pattern.length());
        assertEquals(Pt3Cell.EMPTY, pattern.line(0).cell(A));
    }

    @Test
    void refusesWhatIsNotAPt3() {
        assertThrows(IOException.class, () -> Pt3Reader.read(new byte[300]));
        assertThrows(IOException.class, () -> Pt3Reader.read("ProTracker 3.5".getBytes(StandardCharsets.US_ASCII)));
    }

    private static Pt3Pattern pattern() throws IOException {
        return read().patterns()[0];
    }

    private static Pt3File read() throws IOException {
        return Pt3Reader.read(TestPt3s.pt3());
    }
}

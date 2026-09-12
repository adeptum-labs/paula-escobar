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

package com.adeptum.paula.module.composer669;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class C669ReaderTest {

    private static final int SAMPLES_AT = 110;
    private static final int PATTERNS_AT = 111;
    private static final int SPEEDS_AT = 241;
    private static final int FIRST_SAMPLE_LENGTH_AT = 497 + 13;
    private static final int FIRST_LOOP_START_AT = FIRST_SAMPLE_LENGTH_AT + 4;
    private static final int FIRST_LOOP_END_AT = FIRST_LOOP_START_AT + 4;
    private static final int TOO_MANY = 0xFF;
    private static final int SQUARE_HIGH = (TestModules.C669_SAMPLE_HIGH - 128) << 8;
    private static final int SQUARE_LOW = (TestModules.C669_SAMPLE_LOW - 128) << 8;

    @Test
    void readsWhatTheSongSaysAboutItself() throws IOException {
        final C669File file = C669Reader.read(TestModules.composer669());

        assertFalse(file.extended());
        assertEquals(TestModules.C669_TITLE, file.title());
        assertEquals(List.of(TestModules.C669_TITLE, TestModules.C669_CREDIT, ""), file.message());
        assertArrayEquals(new int[] {0, 1}, file.orders(), "the order list ends at the first end mark");
        assertEquals(0, file.restart());
        assertEquals(TestModules.C669_PATTERNS, file.patterns().size());
        assertEquals(1, file.samples().size());
    }

    @Test
    void readsTheSpeedAndTheBreakOfEachPattern() throws IOException {
        final List<C669Pattern> patterns = C669Reader.read(TestModules.composer669()).patterns();

        for (int number = 0; number < patterns.size(); number++) {
            assertEquals(TestModules.C669_SPEEDS[number], patterns.get(number).speed());
            assertEquals(TestModules.C669_BREAKS[number], patterns.get(number).breakRow());
        }
    }

    @Test
    void readsTheCellsOfAPattern() throws IOException {
        final C669Pattern pattern = C669Reader.read(TestModules.composer669()).patterns().get(0);

        final C669Cell note = pattern.cell(0, 0);
        assertEquals(TestModules.C669_NOTE, note.note());
        assertEquals(0, note.instrument());
        assertEquals(TestModules.C669_VOLUME, note.volume());
        assertEquals(C669Cell.NONE, note.command());

        final C669Cell volume = pattern.cell(TestModules.C669_VOLUME_ROW, TestModules.C669_VOLUME_CHANNEL);
        assertEquals(C669Cell.NONE, volume.note(), "a volume on its own starts nothing");
        assertEquals(TestModules.C669_HALF_VOLUME, volume.volume());

        final C669Cell speed = pattern.cell(TestModules.C669_SPEED_ROW, 0);
        assertEquals(C669Cell.NONE, speed.note());
        assertEquals(C669Cell.NONE, speed.volume(), "and an effect on its own sets no volume");
        assertEquals(TestModules.C669_SPEED_COMMAND, speed.command());
        assertEquals(TestModules.C669_NEW_SPEED, speed.parameter());

        final C669Cell empty = pattern.cell(3, 3);
        assertEquals(C669Cell.NONE, empty.note());
        assertEquals(C669Cell.NONE, empty.volume());
        assertEquals(C669Cell.NONE, empty.command());
    }

    @Test
    void readsTheInstrumentSpreadOverTwoBytes() throws IOException {
        final byte[] module = TestModules.composer669();
        final int cell = 497 + 25;
        module[cell] = (byte) (TestModules.C669_NOTE << 2 | 0b10);
        module[cell + 1] = (byte) (0b0101 << 4 | TestModules.C669_VOLUME);

        assertEquals(0b100101, C669Reader.read(module).patterns().get(0).cell(0, 0).instrument());
    }

    @Test
    void turnsTheUnsignedSampleIntoSixteenBits() throws IOException {
        final C669Sample sample = C669Reader.read(TestModules.composer669()).samples().get(0);

        assertEquals(TestModules.SAMPLE_NAME, sample.name());
        assertEquals(TestModules.C669_SAMPLE_LENGTH, sample.data().length);
        assertEquals(SQUARE_HIGH, sample.data()[0]);
        assertEquals(SQUARE_LOW, sample.data()[TestModules.C669_SAMPLE_LENGTH / 2]);
        assertTrue(sample.loops());
        assertEquals(0, sample.loopStart());
        assertEquals(TestModules.C669_SAMPLE_LENGTH, sample.loopEnd());
    }

    @Test
    void takesALoopEndPastTheSampleFromZeroAsNoLoop() throws IOException {
        final byte[] module = TestModules.composer669();
        module[FIRST_LOOP_END_AT] = (byte) 0xFF;
        module[FIRST_LOOP_END_AT + 1] = (byte) 0xFF;
        module[FIRST_LOOP_END_AT + 2] = (byte) 0x0F;

        assertFalse(C669Reader.read(module).samples().get(0).loops());
    }

    @Test
    void keepsALoopInsideTheSample() throws IOException {
        final byte[] module = TestModules.composer669();
        module[FIRST_LOOP_START_AT] = 16;
        module[FIRST_LOOP_END_AT] = (byte) 200;

        final C669Sample sample = C669Reader.read(module).samples().get(0);
        assertTrue(sample.loops());
        assertEquals(16, sample.loopStart());
        assertEquals(TestModules.C669_SAMPLE_LENGTH, sample.loopEnd());
    }

    @Test
    void knowsTheExtendedFormOfUnis669() throws IOException {
        assertTrue(C669Reader.read(TestModules.composer669("JN")).extended());
    }

    @Test
    void refusesWhatIsNotA669Module() {
        assertThrows(IOException.class, () -> C669Reader.read(TestModules.composer669("MM")));
        assertThrows(IOException.class, () -> C669Reader.read(TestModules.proTracker()));
        assertThrows(IOException.class, () -> C669Reader.read(new byte[100]));
    }

    @Test
    void refusesCountsAndSpeedsTheTrackerCouldNotWrite() {
        assertThrows(IOException.class, () -> C669Reader.read(patched(SAMPLES_AT, TOO_MANY)));
        assertThrows(IOException.class, () -> C669Reader.read(patched(PATTERNS_AT, TOO_MANY)));
        assertThrows(IOException.class, () -> C669Reader.read(patched(SPEEDS_AT, 16)));
        assertThrows(IOException.class, () -> C669Reader.read(patched(SPEEDS_AT, 0)), "a played pattern needs a speed");
    }

    @Test
    void refusesAFileCutShort() {
        final byte[] module = TestModules.composer669();

        assertThrows(IOException.class, () -> C669Reader.read(Arrays.copyOf(module, module.length - 1)));
        assertThrows(IOException.class, () -> C669Reader.read(Arrays.copyOf(module, 497 + 25 + 100)));
    }

    private static byte[] patched(int at, int value) {
        final byte[] module = TestModules.composer669();
        module[at] = (byte) value;
        return module;
    }
}

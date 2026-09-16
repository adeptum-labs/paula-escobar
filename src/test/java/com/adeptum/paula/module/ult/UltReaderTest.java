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

package com.adeptum.paula.module.ult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.adeptum.paula.testing.TestUlts;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class UltReaderTest {

    @Test
    void readsTheHeaderAndTheMessage() throws IOException {
        final UltFile file = UltReader.read(TestUlts.ult());

        assertEquals('4', file.version());
        assertEquals(TestUlts.TITLE, file.title());
        assertEquals(TestUlts.MESSAGE, file.message());
        assertEquals(TestUlts.CHANNELS, file.channels());
    }

    @Test
    void readsASampleHeader() throws IOException {
        final UltSample sample = UltReader.read(TestUlts.ult()).samples()[0];

        assertEquals(TestUlts.SAMPLE_NAME, sample.name());
        assertEquals(TestUlts.SAMPLE_FILE, sample.fileName());
        assertEquals(TestUlts.LOOP_START, sample.loopStart());
        assertEquals(TestUlts.LOOP_END, sample.loopEnd());
        assertEquals(TestUlts.VOLUME, sample.volume());
        assertEquals(TestUlts.SPEED, sample.speed());
        assertEquals(TestUlts.FINETUNE, sample.finetune());
        assertEquals(TestUlts.LOOP, sample.flags());
    }

    /**
     * Before version four a sample has no speed of its own, and its finetune sits where the speed later went.
     */
    @Test
    void takesTheFinetuneFromWhereTheOlderVersionsKeptIt() throws IOException {
        final UltSample sample = UltReader.read(TestUlts.ult('3')).samples()[0];

        assertEquals(8363, sample.speed());
        assertEquals(TestUlts.FINETUNE, sample.finetune());
    }

    @Test
    void readsTheOrderUpToItsEnd() throws IOException {
        assertArrayEquals(new int[]{0}, UltReader.read(TestUlts.ult()).orders());
    }

    /**
     * From version three each channel names its place between the speakers; before it they simply alternate.
     */
    @Test
    void readsTheChannelsPanningOrAlternatesItOnTheOlderVersions() throws IOException {
        assertArrayEquals(new int[]{TestUlts.FIRST_CHANNEL_PANNING << 4 | 8, TestUlts.SECOND_CHANNEL_PANNING << 4 | 8},
                UltReader.read(TestUlts.ult()).panning());
        assertArrayEquals(new int[]{64, 192}, UltReader.read(TestUlts.ult('2')).panning());
    }

    @Test
    void readsAnEventWithItsTwoEffects() throws IOException {
        final UltEvent event = UltReader.read(TestUlts.ult()).event(0, 0, 0);

        assertEquals(TestUlts.NOTE, event.note());
        assertEquals(TestUlts.INSTRUMENT, event.instrument());
        assertEquals(TestUlts.FIRST_EFFECT, event.firstEffect());
        assertEquals(TestUlts.FIRST_PARAM, event.firstParam());
        assertEquals(TestUlts.SECOND_EFFECT, event.secondEffect());
        assertEquals(TestUlts.SECOND_PARAM, event.secondParam());
    }

    /**
     * An event may stand for a run of identical rows, which is how a channel's silence is written.
     */
    @Test
    void repeatsAnEventForTheRowsItStandsFor() throws IOException {
        final UltFile file = UltReader.read(TestUlts.ult());

        assertEquals(UltEvent.EMPTY, file.event(0, 0, 1));
        assertEquals(UltEvent.EMPTY, file.event(0, 0, 63));
        assertEquals(UltEvent.EMPTY, file.event(0, 1, 0));
    }

    @Test
    void readsTheSampleDataAfterThePatterns() throws IOException {
        final UltSample sample = UltReader.read(TestUlts.ult()).samples()[0];

        assertEquals(TestUlts.SAMPLE_LENGTH, sample.data().length);
        assertEquals(TestUlts.SQUARE_HIGH, sample.data()[0]);
        assertEquals(-TestUlts.SQUARE_HIGH, sample.data()[TestUlts.SAMPLE_LENGTH - 1]);
    }

    @Test
    void refusesWhatIsNotAnUltraTrackerModule() {
        final byte[] bytes = TestUlts.ult();
        bytes[0] = 'X';

        assertThrows(IOException.class, () -> UltReader.read(bytes));
        assertThrows(IOException.class, () -> UltReader.read(TestUlts.ult('5')));
        assertThrows(IOException.class, () -> UltReader.read(new byte[10]));
    }
}

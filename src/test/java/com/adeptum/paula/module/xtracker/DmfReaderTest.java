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

package com.adeptum.paula.module.xtracker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DmfReaderTest {

    private static final int VERSION_AT = 4;
    private static final int TITLE_AT = 13;
    private static final int TITLE_LENGTH = 30;
    private static final byte NO_BREAK_SPACE_BYTE = (byte) 0xFF;
    private static final int CHUNK_HEADER = 8;
    private static final int TRACKS_IN_PATT = 2;
    private static final int SECOND_ORDER_IN_SEQU = 6;
    private static final int SQUARE_LENGTH_IN_SMPI = 1 + 1 + 6;
    private static final int SQUARE_FLAGS_IN_SMPI = 1 + 1 + 6 + 12 + 2 + 1;
    private static final int SIXTEEN_BIT = 0x02;
    private static final int WIDEN = 8;
    private static final int HEADER_LENGTH = 66;
    private static final int OVERSIZED_PATTERNS = 21;
    private static final int OVERSIZED_PATTERN_TRACKS = 2;
    private static final int OVERSIZED_PATTERN_ROWS = 0xFFFF;

    private static DmfFile fixture() throws IOException {
        return DmfReader.read(TestModules.dmf());
    }

    @Test
    void readsWhatTheSongSaysAboutItself() throws IOException {
        final DmfFile file = fixture();

        assertEquals(8, file.version());
        assertEquals(TestModules.DMF_TITLE, file.title());
        assertEquals(TestModules.DMF_COMPOSER, file.composer());
        assertEquals(TestModules.DMF_TRACKS, file.tracks());
        assertArrayEquals(new int[] {0, 1}, file.orders());
        assertEquals(2, file.patterns().size());
        assertEquals(2, file.samples().size());
    }

    @Test
    void unpacksTheRowsOfAPatternAndItsCounters() throws IOException {
        final DmfPattern pattern = fixture().patterns().get(0);

        assertEquals(TestModules.DMF_TRACKS, pattern.tracks());
        assertEquals(TestModules.DMF_BEAT, pattern.beat());
        assertEquals(TestModules.DMF_FIRST_ROWS, pattern.rows());
        assertEquals(new DmfGlobalEntry(1, TestModules.DMF_TICK_SPEED), pattern.global(0));
        assertNull(pattern.global(1), "the global counter passes over the rows after");

        final DmfTrackEntry note = pattern.entry(0, 0);
        assertEquals(1, note.instrument());
        assertEquals(TestModules.DMF_NOTE, note.note());
        assertEquals(TestModules.DMF_VOLUME, note.volume());
        assertEquals(DmfTrackEntry.NONE, note.noteEffect());
        assertNull(pattern.entry(3, 0), "and so does the first track's");

        final DmfTrackEntry balance = pattern.entry(0, 1);
        assertEquals(DmfTrackEntry.NONE, balance.note());
        assertEquals(7, balance.volumeEffect());
        assertEquals(TestModules.DMF_BALANCE, balance.volumeData());
        assertNull(pattern.entry(1, 1));
        assertTrue(pattern.entry(2, 1).isNoteOff());
        assertNull(pattern.entry(3, 1));
    }

    @Test
    void keepsALeadingSpaceAndTurnsAnEmbeddedZeroIntoOne() throws IOException {
        final byte[] module = TestModules.dmf();
        final byte[] title = new byte[TITLE_LENGTH];
        System.arraycopy(" Foo\0Bar".getBytes(StandardCharsets.US_ASCII), 0, title, 0, 8);
        System.arraycopy(title, 0, module, TITLE_AT, TITLE_LENGTH);

        assertEquals(" Foo Bar", DmfReader.read(module).title());
    }

    @Test
    void keepsATrailingByteThatIsNotTheLiteralSpaceTheTrackerPadsWith() throws IOException {
        final byte[] module = TestModules.dmf();
        final byte[] title = new byte[TITLE_LENGTH];
        final byte[] prefix = "Foo".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, title, 0, prefix.length);
        title[prefix.length] = NO_BREAK_SPACE_BYTE;
        Arrays.fill(title, prefix.length + 1, TITLE_LENGTH, (byte) ' ');
        System.arraycopy(title, 0, module, TITLE_AT, TITLE_LENGTH);

        assertEquals("Foo ", DmfReader.read(module).title());
    }

    @Test
    void readsBufferNotesAndInstrumentsOnTheirOwn() throws IOException {
        final DmfPattern pattern = fixture().patterns().get(1);

        assertEquals(1, pattern.tracks());
        assertEquals(0, pattern.beat());
        assertNull(pattern.global(0), "an info byte of nothing carries no command");
        final DmfTrackEntry buffer = pattern.entry(0, 0);
        assertTrue(buffer.hasBufferNote());
        assertFalse(buffer.hasNote());
        assertEquals(4, buffer.noteEffect());
        assertEquals(TestModules.DMF_PORTAMENTO, buffer.noteData());
        final DmfTrackEntry instrument = pattern.entry(1, 0);
        assertTrue(instrument.hasInstrument());
        assertFalse(instrument.hasNote());
        assertNull(pattern.entry(0, 1), "the pattern has no second track");
    }

    @Test
    void readsPlainAndPackedSamples() throws IOException {
        final DmfFile file = fixture();

        final DmfSample square = file.sample(1);
        assertEquals(TestModules.SAMPLE_NAME, square.name());
        assertEquals(TestModules.DMF_SAMPLE_LENGTH, square.data().length);
        assertEquals(TestModules.DMF_SQUARE_HIGH << WIDEN, square.data()[0]);
        assertEquals(-TestModules.DMF_SQUARE_HIGH << WIDEN, square.data()[TestModules.DMF_SAMPLE_LENGTH - 1]);
        assertTrue(square.looped());
        assertEquals(TestModules.DMF_SAMPLE_LENGTH, square.loopEnd());
        assertEquals(TestModules.DMF_C3_FREQUENCY, square.c3Frequency());
        assertEquals(0, square.volume());
        assertFalse(square.sixteenBit());

        final DmfSample packed = file.sample(2);
        assertEquals(TestModules.DMF_PACKED_NAME, packed.name());
        assertFalse(packed.looped());
        assertEquals(TestModules.DMF_PACKED_VOLUME, packed.volume());
        for (int at = 0; at < TestModules.DMF_PACKED.length; at++) {
            assertEquals(TestModules.DMF_PACKED[at] << WIDEN, packed.data()[at]);
        }
        assertNull(file.sample(3));
    }

    @Test
    void halvesSixteenBitSamples() throws IOException {
        final byte[] module = TestModules.dmf();
        module[body(module, "SMPI") + SQUARE_FLAGS_IN_SMPI] |= SIXTEEN_BIT;

        final DmfSample square = DmfReader.read(module).sample(1);
        assertTrue(square.sixteenBit());
        assertEquals(TestModules.DMF_SAMPLE_LENGTH / 2, square.data().length);
        assertEquals(TestModules.DMF_SAMPLE_LENGTH / 2, square.loopEnd());
        assertEquals(TestModules.DMF_SQUARE_HIGH | TestModules.DMF_SQUARE_HIGH << WIDEN, square.data()[0]);
    }

    @Test
    void refusesAnotherFormatsDotDmf() {
        final byte[] module = TestModules.dmf();
        module[0] = 'X';

        assertThrows(IOException.class, () -> DmfReader.read(module));
    }

    @Test
    void refusesVersionsItDoesNotKnow() {
        for (final int version : new int[] {0, 11}) {
            final byte[] module = TestModules.dmf();
            module[VERSION_AT] = (byte) version;
            assertThrows(IOException.class, () -> DmfReader.read(module), "version " + version);
        }
    }

    @Test
    void refusesTrackCountsOutsideWhatTheTrackerHas() {
        for (final int tracks : new int[] {0, 33}) {
            final byte[] module = TestModules.dmf();
            module[body(module, "PATT") + TRACKS_IN_PATT] = (byte) tracks;
            assertThrows(IOException.class, () -> DmfReader.read(module), tracks + " tracks");
        }
    }

    @Test
    void refusesAnOrderNamingAPatternTheModuleLacks() {
        final byte[] module = TestModules.dmf();
        module[body(module, "SEQU") + SECOND_ORDER_IN_SEQU] = 5;

        assertThrows(IOException.class, () -> DmfReader.read(module));
    }

    @Test
    void refusesASampleBlockRunningPastItsChunk() {
        final byte[] module = TestModules.dmf();
        ByteBuffer.wrap(module).order(ByteOrder.LITTLE_ENDIAN).putInt(body(module, "SMPD"), 100_000);

        assertThrows(IOException.class, () -> DmfReader.read(module));
    }

    @Test
    void refusesAFileCutShortInItsHeader() {
        assertThrows(IOException.class, () -> DmfReader.read(Arrays.copyOf(TestModules.dmf(), 40)));
    }

    @Test
    void refusesPatternsLargerThanAnyModuleHolds() {
        assertThrows(IOException.class, () -> DmfReader.read(oversizedPatternsModule()));
    }

    @Test
    void boundsASamplesFramesByWhatItsDataBlockCanHold() throws IOException {
        final byte[] module = TestModules.dmf();
        ByteBuffer.wrap(module).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(body(module, "SMPI") + SQUARE_LENGTH_IN_SMPI, 0x3FFFFFF);

        final DmfSample square = DmfReader.read(module).sample(1);

        assertEquals(TestModules.DMF_SAMPLE_LENGTH, square.data().length);
        assertEquals(TestModules.DMF_SAMPLE_LENGTH, square.loopEnd());
    }

    /**
     * A module whose PATT chunk declares enough rowless, zero-length patterns to run the running total of rows
     * over what any real X-Tracker module needs, without allocating anything near that much itself.
     */
    private static byte[] oversizedPatternsModule() {
        final ByteArrayOutputStream patt = new ByteArrayOutputStream();
        patt.writeBytes(littleEndianShort(OVERSIZED_PATTERNS));
        patt.write(OVERSIZED_PATTERN_TRACKS);
        for (int number = 0; number < OVERSIZED_PATTERNS; number++) {
            patt.write(OVERSIZED_PATTERN_TRACKS);
            patt.write(0);
            patt.writeBytes(littleEndianShort(OVERSIZED_PATTERN_ROWS));
            patt.writeBytes(new byte[Integer.BYTES]);
        }
        final ByteArrayOutputStream sequ = new ByteArrayOutputStream();
        sequ.writeBytes(new byte[2 * Short.BYTES]);
        sequ.writeBytes(littleEndianShort(0));

        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes("DDMF".getBytes(StandardCharsets.US_ASCII));
        file.write(8);
        file.writeBytes(new byte[HEADER_LENGTH - file.size()]);
        file.writeBytes(chunk("PATT", patt.toByteArray()));
        file.writeBytes(chunk("SEQU", sequ.toByteArray()));
        return file.toByteArray();
    }

    private static byte[] chunk(String id, byte[] body) {
        return ByteBuffer.allocate(CHUNK_HEADER + body.length).order(ByteOrder.LITTLE_ENDIAN)
                .put(id.getBytes(StandardCharsets.US_ASCII)).putInt(body.length).put(body).array();
    }

    private static byte[] littleEndianShort(int value) {
        return ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array();
    }

    private static int body(byte[] module, String id) {
        final byte[] name = id.getBytes(StandardCharsets.US_ASCII);
        for (int at = 0; at + name.length <= module.length; at++) {
            if (Arrays.equals(module, at, at + name.length, name, 0, name.length)) {
                return at + CHUNK_HEADER;
            }
        }
        throw new AssertionError("no " + id + " chunk");
    }
}

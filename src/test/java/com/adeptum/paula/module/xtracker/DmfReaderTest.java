package com.adeptum.paula.module.xtracker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DmfReaderTest {

    private static final int VERSION_AT = 4;
    private static final int CHUNK_HEADER = 8;
    private static final int TRACKS_IN_PATT = 2;
    private static final int SECOND_ORDER_IN_SEQU = 6;
    private static final int SQUARE_FLAGS_IN_SMPI = 1 + 1 + 6 + 12 + 2 + 1;
    private static final int SIXTEEN_BIT = 0x02;
    private static final int WIDEN = 8;

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

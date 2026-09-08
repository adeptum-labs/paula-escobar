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

package com.adeptum.paula.module.mo3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * Read against the module the fixture was packed from: a four-channel ProTracker module of one pattern, one
 * note on the first channel and one looping square-wave sample in the first of its thirty-one slots.
 */
class Mo3ReaderTest {

    private static final String FIXTURE = "/mo3/paula-test.mo3";
    private static final int CHANNELS = 4;
    private static final int ROWS = 64;
    private static final int SAMPLE_SLOTS = 31;
    private static final int SAMPLE_LENGTH = 64;
    private static final int SAMPLE_DATA_AT = 106;
    private static final int COMPRESSED_SAMPLE_SIZE = 28;
    private static final int EMPTY_TRACK = 1;
    private static final int NOTE = 0x30;
    private static final int SPEED = 6;
    private static final int TEMPO = 125;

    /**
     * A note and an instrument on the first row, then the byte that ends the track.
     */
    private static final String SOUNDING_TRACK = "120130020000";

    @Test
    void readsWhatTheSongSaysAboutItself() throws IOException {
        final Mo3File file = Mo3Reader.read(fixture());

        assertEquals(TestModules.TITLE, file.name());
        assertEquals("", file.message());
        assertEquals(Mo3Kind.PROTRACKER, file.kind());
        assertEquals(CHANNELS, file.song().channels());
        assertEquals(SPEED, file.song().speed());
        assertEquals(TEMPO, file.song().tempo());
    }

    @Test
    void readsTheOrderTheOnePatternIsPlayedIn() throws IOException {
        final Mo3File file = Mo3Reader.read(fixture());

        assertArrayEquals(new int[] {0}, file.orders());
        assertEquals(0, file.song().restart());
        assertFalse(file.hasOrderSeparators(), "ProTracker has patterns of every number an order can hold");
    }

    @Test
    void readsThePanningTheAmigaGaveTheChannels() throws IOException {
        final int[] panning = Mo3Reader.read(fixture()).song().channelPanning();

        assertArrayEquals(new int[] {64, 192, 192, 64},
                Arrays.copyOf(panning, CHANNELS), "left, right, right, left");
    }

    @Test
    void readsThePatternAsTheTracksItIsMadeOf() throws IOException {
        final Mo3Pattern pattern = Mo3Reader.read(fixture()).patterns().getFirst();

        assertEquals(ROWS, pattern.rows());
        assertEquals(CHANNELS, pattern.channels());
        assertEquals(0, pattern.trackFor(0), "the sounding channel");
        assertEquals(EMPTY_TRACK, pattern.trackFor(1), "and the three that share the empty one");
        assertEquals(EMPTY_TRACK, pattern.trackFor(2));
        assertEquals(EMPTY_TRACK, pattern.trackFor(3));
    }

    @Test
    void readsTheTracksThePatternsShare() throws IOException {
        final Mo3File file = Mo3Reader.read(fixture());

        assertEquals(2, file.tracks().size());
        assertEquals(SOUNDING_TRACK, HexFormat.of().formatHex(file.tracks().getFirst()));
        assertArrayEquals(new byte[1], file.tracks().get(EMPTY_TRACK), "nothing but the byte that ends it");
    }

    @Test
    void readsEverySampleSlotTheModuleHad() throws IOException {
        final Mo3File file = Mo3Reader.read(fixture());

        assertEquals(SAMPLE_SLOTS, file.samples().size());
        assertEquals(0, file.instruments().size(), "a ProTracker module reaches its samples directly");
        assertFalse(file.hasInstruments());
    }

    @Test
    void readsTheSampleTheModuleSounds() throws IOException {
        final Mo3Sample sample = Mo3Reader.read(fixture()).samples().getFirst();

        assertEquals(TestModules.SAMPLE_NAME, sample.name());
        assertEquals(SAMPLE_LENGTH, sample.length());
        assertEquals(0, sample.loopStart());
        assertEquals(SAMPLE_LENGTH, sample.loopEnd());
        assertTrue(sample.has(Mo3Sample.LOOP));
        assertFalse(sample.has(Mo3Sample.SIXTEEN_BIT), "the Amiga sampled it eight bits wide");
        assertEquals(1, sample.channels());
    }

    @Test
    void readsHowTheSoundingSampleIsPacked() throws IOException {
        final Mo3Sample sample = Mo3Reader.read(fixture()).samples().getFirst();

        assertEquals(Mo3Sample.DELTA, sample.compression());
        assertEquals(COMPRESSED_SAMPLE_SIZE, sample.compressedSize());
        assertFalse(sample.isDuplicate());
    }

    @Test
    void readsTheEmptySlotsAsEmpty() throws IOException {
        final Mo3Sample sample = Mo3Reader.read(fixture()).samples().get(1);

        assertEquals(0, sample.length());
        assertEquals(0, sample.compressedSize());
        assertEquals("", sample.name());
    }

    @Test
    void saysWhereTheWaveformsBegin() throws IOException {
        final Mo3File file = Mo3Reader.read(fixture());

        assertEquals(SAMPLE_DATA_AT, file.sampleData());
        assertEquals(SAMPLE_DATA_AT + COMPRESSED_SAMPLE_SIZE, file.file().length,
                "the one packed waveform accounts for the rest of the file");
    }

    @Test
    void readsTheNoteWrittenOnTheFirstRow() throws IOException {
        final byte[] track = Mo3Reader.read(fixture()).tracks().getFirst();

        assertEquals(0x12, track[0] & 0xFF, "two commands, on one row");
        assertEquals(1, track[1], "the first says a note follows");
        assertEquals(NOTE, track[2] & 0xFF);
    }

    private static byte[] fixture() throws IOException {
        try (InputStream fixture = Mo3ReaderTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(fixture, FIXTURE);
            return fixture.readAllBytes();
        }
    }
}

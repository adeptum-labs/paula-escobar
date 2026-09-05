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

package com.adeptum.paula.module.hively;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HvlReaderTest {

    private static final int SEPARATION = HvlReader.STEREO_SEPARATION;
    private static final int AHX_MIX_GAIN = 194;
    private static final int HVL_MIX_GAIN = 256;
    private static final int SQUARE_WAVEFORM = 3;
    private static final int C1 = 1;
    private static final int POSITION_COUNT_OFFSET = 7;
    private static final int POSITION_TRACK_OFFSET = 14;
    private static final int FIRST_STEP_OFFSET = 22;
    private static final int WAVE_OFFSET = 35;
    private static final int PERFORMANCE_PACKED_OFFSET = 56;
    private static final int PERFORMANCE_NOTE_OFFSET = 57;
    private static final int HIGHEST_NOTE = 60;
    private static final int LONGEST_WAVE = 5;
    private static final int NOISE_WAVEFORM = 4;
    private static final int TRACK_LENGTH_OFFSET = 10;
    private static final int INSTRUMENT_COUNT_OFFSET = 12;
    private static final int CHANNEL_OFFSET = 8;
    private static final int RESTART_OFFSET = 9;
    private static final int TOO_MANY = 65;
    private static final int TRUNCATED_LENGTH = 40;

    private static HvlTune ahx() throws IOException {
        return HvlReader.read(TestModules.hively());
    }

    private static HvlTune hvl() throws IOException {
        return HvlReader.read(TestModules.hivelyTracker());
    }

    private static byte[] withByte(byte[] module, int offset, int value) {
        final byte[] changed = module.clone();
        changed[offset] = (byte) value;
        return changed;
    }

    @Test
    void readsWhatAnAhxModuleSaysAboutItself() throws IOException {
        final HvlTune tune = ahx();

        assertEquals(TestModules.HIVELY_TITLE, tune.name());
        assertEquals(0, tune.version());
        assertEquals(4, tune.channels());
        assertEquals(TestModules.HIVELY_POSITIONS, tune.positionCount());
        assertEquals(TestModules.HIVELY_TRACK_LENGTH, tune.trackLength());
        assertEquals(1, tune.trackCount());
        assertEquals(1, tune.speedMultiplier());
        assertEquals(0, tune.restart());
        assertEquals(0, tune.subsongs().length);
    }

    @Test
    void laysThePositionOutOverTheChannels() throws IOException {
        final HvlPosition position = ahx().positions().getFirst();

        assertEquals(1, position.track()[0]);
        assertEquals(0, position.transpose()[0]);
        assertEquals(0, position.track()[1], "the other channels play the blank track");
        assertEquals(HvlTune.MAX_CHANNELS, position.track().length);
    }

    @Test
    void keepsTheFirstTrackBlankWhenTheModuleLeavesItOut() throws IOException {
        final HvlStep[][] tracks = ahx().tracks();

        assertEquals(2, tracks.length);
        assertEquals(HvlStep.EMPTY, tracks[0][0]);
        assertEquals(C1, tracks[1][0].note());
        assertEquals(1, tracks[1][0].instrument());
        assertEquals(HvlStep.EMPTY, tracks[1][1]);
    }

    @Test
    void leavesTheFirstInstrumentEmptyBecauseNotesNumberFromOne() throws IOException {
        final HvlInstrument empty = ahx().instruments().getFirst();

        assertEquals(2, ahx().instruments().size());
        assertEquals("", empty.name());
        assertEquals(0, empty.volume());
        assertTrue(empty.playlist().entries().isEmpty());
    }

    @Test
    void readsTheInstrumentWithItsEnvelopeAndPerformanceList() throws IOException {
        final HvlInstrument instrument = ahx().instruments().get(1);

        assertEquals(TestModules.HIVELY_INSTRUMENT, instrument.name());
        assertEquals(64, instrument.volume());
        assertEquals(SQUARE_WAVEFORM, instrument.waveLength());
        assertEquals(0x20, instrument.squareLowerLimit());
        assertEquals(0x3f, instrument.squareUpperLimit());
        assertEquals(1, instrument.squareSpeed());
        assertEquals(new HvlEnvelope(1, 64, 1, 64, 1, 1, 0), instrument.envelope());
        assertEquals(1, instrument.playlist().speed());

        final HvlPlaylistEntry entry = instrument.playlist().entries().getFirst();
        assertEquals(SQUARE_WAVEFORM, entry.waveform());
        assertEquals(0, entry.note());
        assertFalse(entry.fixed());
    }

    @Test
    void derivesTheStereoMixFromTheSeparation() throws IOException {
        final HvlTune tune = ahx();

        assertEquals(AHX_MIX_GAIN, tune.mixGain());
        assertEquals(HvlTables.STEREO_PAN_LEFT[SEPARATION], tune.defaultPanLeft());
        assertEquals(HvlTables.STEREO_PAN_RIGHT[SEPARATION], tune.defaultPanRight());
        assertEquals(SEPARATION, tune.stereo());
    }

    @Test
    void readsAHivelyTrackerModuleWithItsOwnMixAndChannels() throws IOException {
        final HvlTune tune = hvl();

        assertEquals(TestModules.HIVELY_TITLE, tune.name());
        assertEquals(TestModules.HIVELY_CHANNELS, tune.channels());
        assertEquals(HVL_MIX_GAIN, tune.mixGain());
        assertEquals(HvlTables.STEREO_PAN_LEFT[SEPARATION], tune.defaultPanLeft());
        assertEquals(HvlTables.STEREO_PAN_RIGHT[SEPARATION], tune.defaultPanRight());
        assertEquals(TestModules.HIVELY_STEREO, tune.stereo());
        assertEquals(TestModules.HIVELY_TRACK_LENGTH, tune.trackLength());
    }

    @Test
    void readsHivelyTrackerTracksAndInstruments() throws IOException {
        final HvlTune tune = hvl();

        assertEquals(HvlStep.EMPTY, tune.tracks()[0][0]);
        assertEquals(C1, tune.tracks()[1][0].note());
        assertEquals(1, tune.tracks()[1][0].instrument());
        assertEquals(HvlStep.EMPTY, tune.tracks()[1][1], "a step stored as a single 0x3f byte");
        assertEquals(TestModules.HIVELY_INSTRUMENT, tune.instruments().get(1).name());
        assertEquals(SQUARE_WAVEFORM, tune.instruments().get(1).playlist().entries().getFirst().waveform());
        assertEquals(1, tune.positions().getFirst().track()[0]);
    }

    @Test
    void clampsARestartBeyondTheLastPosition() throws IOException {
        final HvlTune tune = HvlReader.read(withByte(TestModules.hively(), RESTART_OFFSET, 5));

        assertEquals(0, tune.restart());
    }

    @Test
    void recognisesBothMagics() {
        assertTrue(HvlReader.looksLikeHively(TestModules.hively()));
        assertTrue(HvlReader.looksLikeHively(TestModules.hivelyTracker()));
        assertFalse(HvlReader.looksLikeHively("XXX\0".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(HvlReader.looksLikeHively(new byte[]{'T', 'H'}));
        assertFalse(HvlReader.looksLikeHively(withByte(TestModules.hivelyTracker(), 3, 9)));
    }

    @Test
    void rejectsAFileThatIsNotAModule() {
        final byte[] file = withByte(withByte(withByte(TestModules.hively(), 0, 'X'), 1, 'X'), 2, 'X');

        assertEquals("Not an AHX or HivelyTracker module",
                assertThrows(IOException.class, () -> HvlReader.read(file)).getMessage());
    }

    @Test
    void rejectsATruncatedFileWithoutReadingPastItsEnd() {
        final byte[] file = Arrays.copyOf(TestModules.hively(), TRUNCATED_LENGTH);

        final IOException thrown = assertThrows(IOException.class, () -> HvlReader.read(file));
        assertTrue(thrown.getMessage().contains("short"), thrown.getMessage());
        assertSame(IOException.class, thrown.getClass());
    }

    @Test
    void rejectsATrackLongerThanTheReplayerHolds() {
        final byte[] file = withByte(TestModules.hively(), TRACK_LENGTH_OFFSET, TOO_MANY);

        assertTrue(assertThrows(IOException.class, () -> HvlReader.read(file)).getMessage().contains("65"));
    }

    @Test
    void rejectsMoreInstrumentsThanTheReplayerHolds() {
        final byte[] file = withByte(TestModules.hively(), INSTRUMENT_COUNT_OFFSET, TOO_MANY);

        assertTrue(assertThrows(IOException.class, () -> HvlReader.read(file)).getMessage().contains("65"));
    }

    @Test
    void rejectsMoreChannelsThanTheReplayerHolds() {
        final byte[] file = withByte(TestModules.hivelyTracker(), CHANNEL_OFFSET, (20 - 4) << 2);

        assertTrue(assertThrows(IOException.class, () -> HvlReader.read(file)).getMessage().contains("20"));
    }

    @Test
    void rejectsATuneWithoutASinglePosition() {
        final byte[] file = withByte(TestModules.hively(), POSITION_COUNT_OFFSET, 0);

        assertTrue(assertThrows(IOException.class, () -> HvlReader.read(file)).getMessage().contains("no positions"));
    }

    @Test
    void rejectsAPositionPlayingATrackTheModuleHasNot() {
        final byte[] file = withByte(TestModules.hively(), POSITION_TRACK_OFFSET, 5);

        assertTrue(assertThrows(IOException.class, () -> HvlReader.read(file)).getMessage().contains("track 5 of 1"));
    }

    @Test
    void clampsAWaveLongerThanTheReplayerSynthesises() throws IOException {
        final HvlTune tune = HvlReader.read(withByte(TestModules.hively(), WAVE_OFFSET, 7));

        assertEquals(LONGEST_WAVE, tune.instruments().get(1).waveLength());
    }

    @Test
    void clampsANoteAboveTheHighestTheReplayerPlays() throws IOException {
        final HvlTune tune = HvlReader.read(withByte(TestModules.hively(), FIRST_STEP_OFFSET, 0xfc));

        assertEquals(HIGHEST_NOTE, tune.tracks()[1][0].note());
    }

    @Test
    void clampsANoteThePerformanceListPlaysAboveThatSame() throws IOException {
        final HvlTune tune = HvlReader.read(withByte(TestModules.hively(), PERFORMANCE_NOTE_OFFSET, 0xff));

        assertEquals(HIGHEST_NOTE, tune.instruments().get(1).playlist().entries().getFirst().note());
    }

    @Test
    void clampsAWaveformThePerformanceListCannotName() throws IOException {
        final HvlTune tune = HvlReader.read(withByte(TestModules.hively(), PERFORMANCE_PACKED_OFFSET, 7));

        assertEquals(NOISE_WAVEFORM, tune.instruments().get(1).playlist().entries().getFirst().waveform());
    }
}

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

import static com.adeptum.paula.module.xtracker.DmfFiles.C3;
import static com.adeptum.paula.module.xtracker.DmfFiles.C3_NOTE;
import static com.adeptum.paula.module.xtracker.DmfFiles.ROW_FRAMES;
import static com.adeptum.paula.module.xtracker.DmfFiles.SAMPLE_RATE;
import static com.adeptum.paula.module.xtracker.DmfFiles.engine;
import static com.adeptum.paula.module.xtracker.DmfFiles.instrumentEffect;
import static com.adeptum.paula.module.xtracker.DmfFiles.instrumentOnly;
import static com.adeptum.paula.module.xtracker.DmfFiles.note;
import static com.adeptum.paula.module.xtracker.DmfFiles.noteOff;
import static com.adeptum.paula.module.xtracker.DmfFiles.rows;
import static com.adeptum.paula.module.xtracker.DmfFiles.track;
import static com.adeptum.paula.module.xtracker.DmfFiles.tracks;
import static com.adeptum.paula.module.xtracker.DmfFiles.units;
import static com.adeptum.paula.module.xtracker.DmfFiles.withVolume;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DmfEngineTest {

    private static final int NONE = DmfTrackEntry.NONE;
    private static final int SQUARE = 1;
    private static final int RAMP = 2;
    private static final int RETRIG = 5;
    private static final int ROWS_IN_FIXTURE = DmfFiles.ROWS;
    private static final int FRAMES = 1024;

    @Test
    void soundsTheNoteAtTheSamplesRateOnCThree() {
        final DmfEngine engine = engine(track(note(C3_NOTE, SQUARE)));
        engine.nextUnit();

        final DmfChannel channel = engine.channel(0);
        assertTrue(channel.voice.sounding);
        assertEquals(SQUARE, channel.instrument);
        assertEquals(C3, channel.frequency(), 1e-9);
        assertEquals(DmfVoice.FULL, channel.loudness(), "a sample volume of 0 keeps the channel's full volume");
    }

    @Test
    void tunesTheOtherNotesInEqualTemperament() {
        assertEquals(C3 * 2.0, frequencyOf(C3_NOTE + 12), 1e-9);
        assertEquals(C3 / 2.0, frequencyOf(C3_NOTE - 12), 1e-9);
        assertEquals(C3 * Math.pow(2, 1 / 12.0), frequencyOf(C3_NOTE + 1), 1e-9);
    }

    @Test
    void startsAtTheSamplesVolumeUnlessThatIsZero() {
        final DmfFile file = track(note(C3_NOTE, SQUARE));
        final DmfFile loud = new DmfFile(8, "", "", 1, file.orders(), file.patterns(), List.of(DmfFiles.square(180)));
        final DmfEngine engine = engine(loud);
        engine.nextUnit();

        assertEquals(180, engine.channel(0).loudness());
    }

    @Test
    void setsTheVolumeByteOverTheSamplesVolume() {
        final DmfEngine engine = engine(track(withVolume(note(C3_NOTE, SQUARE), 100)));
        engine.nextUnit();

        assertEquals(100, engine.channel(0).loudness());
    }

    @Test
    void holdsTheSampleWhereItStandsOnANoteOff() {
        final DmfEngine engine = engine(track(note(C3_NOTE, RAMP), noteOff()));
        rows(engine, 1);
        engine.nextUnit();
        final double held = engine.channel(0).voice.position;
        engine.mix(new short[FRAMES * 2], FRAMES);

        assertEquals(0, engine.channel(0).frequency(), 1e-9);
        assertEquals(held, engine.channel(0).voice.position, 1e-9, "a held sample does not move on");
    }

    @Test
    void soundsAHeldSampleAgainOnANoteWithoutAnInstrument() {
        final DmfEngine engine = engine(track(note(C3_NOTE, RAMP), noteOff(), note(C3_NOTE + 12, NONE)));
        rows(engine, 2);
        final double held = engine.channel(0).voice.position;
        engine.nextUnit();

        assertEquals(C3 * 2.0, engine.channel(0).frequency(), 1e-9);
        assertEquals(held, engine.channel(0).voice.position, 1e-9, "from where it was held, not from its head");
    }

    @Test
    void restartsTheLastNoteOnAnInstrumentAlone() {
        final DmfEngine engine = engine(track(withVolume(note(C3_NOTE, RAMP), 90), instrumentOnly(SQUARE)));
        rows(engine, 1);
        engine.nextUnit();

        final DmfChannel channel = engine.channel(0);
        assertEquals(0, channel.voice.position, 1e-9);
        assertEquals("ramp", channel.voice.sample.name(), "on the sample it already has, as OpenMPT plays it");
        assertEquals(90, channel.loudness(), "and at the volume it had");
        assertEquals(C3, channel.frequency(), 1e-9);
    }

    @Test
    void changesOnlyThePitchOnANoteWithoutAnInstrument() {
        final DmfEngine engine = engine(track(note(C3_NOTE, RAMP), note(C3_NOTE + 1, NONE)));
        rows(engine, 1);
        final double before = engine.channel(0).voice.position;
        engine.nextUnit();

        assertTrue(engine.channel(0).voice.position >= before, "the sample runs on");
    }

    @Test
    void walksTheRowsAndEndsWithTheOrderList() {
        final DmfEngine engine = engine(track(note(C3_NOTE, SQUARE)));
        rows(engine, 3);
        assertEquals(3, engine.row());
        assertEquals(0, engine.order());

        rows(engine, ROWS_IN_FIXTURE - 3);
        engine.nextUnit();
        assertTrue(engine.hasEnded());
    }

    @Test
    void measuresTheSongByPlayingItThroughWithoutMixing() {
        assertEquals(ROWS_IN_FIXTURE * ROW_FRAMES,
                DmfEngine.songFrames(track(note(C3_NOTE, SQUARE)), SAMPLE_RATE, Long.MAX_VALUE).orElseThrow());
        assertTrue(DmfEngine.songFrames(track(note(C3_NOTE, SQUARE)), SAMPLE_RATE, ROW_FRAMES).isEmpty());
    }

    @Test
    void cutsTheTracksAPatternDoesNotHave() {
        final DmfFile two = tracks(new DmfTrackEntry[] {note(C3_NOTE, SQUARE), note(C3_NOTE, SQUARE)});
        final DmfPattern narrow = new DmfPattern(1, 0, new DmfGlobalEntry[1], new DmfTrackEntry[1][1]);
        final DmfFile file = new DmfFile(8, "", "", 2, new int[] {0, 1},
                List.of(two.patterns().getFirst(), narrow), two.samples());
        final DmfEngine engine = engine(file);
        rows(engine, ROWS_IN_FIXTURE);
        engine.nextUnit();

        assertTrue(engine.channel(0).voice.sounding, "the track the pattern has plays on");
        assertFalse(engine.channel(1).voice.sounding);
    }

    @Test
    void letsACutTrackCarryNoEffectIntoSilence() {
        final DmfTrackEntry retrig = instrumentEffect(note(C3_NOTE, SQUARE), RETRIG, 16);
        final DmfFile two = tracks(new DmfTrackEntry[] {note(C3_NOTE, SQUARE), retrig});
        final DmfPattern narrow = new DmfPattern(1, 0, new DmfGlobalEntry[1], new DmfTrackEntry[1][1]);
        final DmfFile file = new DmfFile(8, "", "", 2, new int[] {0, 1},
                List.of(two.patterns().getFirst(), narrow), two.samples());
        final DmfEngine engine = engine(file);
        rows(engine, ROWS_IN_FIXTURE);
        rows(engine, 1);

        assertFalse(engine.channel(1).voice.sounding);
    }

    @Test
    void mixesByBalanceAndDividesByTheTracks() {
        final DmfEngine engine = engine(track(note(C3_NOTE, SQUARE)));
        final short[] out = new short[2];
        engine.mix(out, 1);

        assertEquals(DmfFiles.HIGH * 127 / 255, out[0], "the middle leans a step to the right");
        assertEquals(DmfFiles.HIGH * 128 / 255, out[1]);
    }

    @Test
    void mixesAsManyFramesAsTheSongStillHas() {
        final DmfEngine engine = engine(track(note(C3_NOTE, SQUARE)));
        final int frames = ROWS_IN_FIXTURE * ROW_FRAMES;

        assertEquals(frames, engine.mix(new short[(frames + FRAMES) * 2], frames + FRAMES));
    }

    private static double frequencyOf(int note) {
        final DmfEngine engine = engine(track(note(note, SQUARE)));
        units(engine, 1);
        return engine.channel(0).frequency();
    }
}

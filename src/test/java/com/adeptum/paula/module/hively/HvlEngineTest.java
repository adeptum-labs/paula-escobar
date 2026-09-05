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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class HvlEngineTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int SUBSONG = 0;
    private static final int DEFAULT_TEMPO = 6;
    private static final int FRAMES_PER_TICK = SAMPLE_RATE / 50;
    private static final int SONG_TICKS = TestModules.HIVELY_TRACK_LENGTH * DEFAULT_TEMPO;
    private static final long AN_HOUR = 3600L * SAMPLE_RATE;

    private static HvlEngine engine() throws IOException {
        return new HvlEngine(HvlReader.read(TestModules.hively()), SAMPLE_RATE, SUBSONG);
    }

    private static boolean sounding(final HvlVoice voice) {
        for (final byte sample : voice.mixSource) {
            if (sample != 0) {
                return true;
            }
        }
        return false;
    }

    @Test
    void startsAtTheFirstRowOfTheFirstPosition() throws IOException {
        final HvlEngine engine = engine();

        assertEquals(4, engine.voices());
        assertEquals(0, engine.position());
        assertEquals(0, engine.row());
        assertFalse(engine.songEnded());
    }

    @Test
    void soundsTheNoteOnTheChannelItIsWrittenOn() throws IOException {
        final HvlEngine engine = engine();

        engine.tick();

        assertTrue(engine.voice(0).audioVolume > 0, "the note in the track is heard");
        assertTrue(sounding(engine.voice(0)), "the square of the performance list is planted");
        assertEquals(0, engine.voice(1).audioVolume);
        assertFalse(sounding(engine.voice(1)), "the channel without a note stays silent");
    }

    @Test
    void reachesTheEndOfTheSongWhenThePositionWraps() throws IOException {
        final HvlEngine engine = engine();

        for (int tick = 0; tick < SONG_TICKS - 1; tick++) {
            engine.tick();
            assertFalse(engine.songEnded(), "the song lasts a full position");
        }
        engine.tick();

        assertTrue(engine.songEnded());
    }

    @Test
    void countsTheFramesOfTheWholeSong() throws IOException {
        final HvlTune tune = HvlReader.read(TestModules.hively());

        assertEquals(OptionalLong.of((long) SONG_TICKS * FRAMES_PER_TICK),
                HvlEngine.songFrames(tune, SAMPLE_RATE, AN_HOUR));
    }

    @Test
    void countsNothingForASongThatOutlastsTheLimit() throws IOException {
        final HvlTune tune = HvlReader.read(TestModules.hively());

        assertEquals(OptionalLong.empty(), HvlEngine.songFrames(tune, SAMPLE_RATE, FRAMES_PER_TICK - 1));
    }

    @Test
    void playsTheHivelyTrackerFormTheSameWayAsTheAhxOne() throws IOException {
        final HvlEngine hively = new HvlEngine(HvlReader.read(TestModules.hivelyTracker()), SAMPLE_RATE, SUBSONG);
        final HvlEngine ahx = engine();

        hively.tick();
        ahx.tick();

        assertEquals(TestModules.HIVELY_CHANNELS, hively.voices());
        assertEquals(ahx.voice(0).audioPeriod, hively.voice(0).audioPeriod);
        assertEquals(ahx.voice(0).delta, hively.voice(0).delta);
        assertArrayEquals(ahx.voice(0).voiceBuffer, hively.voice(0).voiceBuffer);
    }
}

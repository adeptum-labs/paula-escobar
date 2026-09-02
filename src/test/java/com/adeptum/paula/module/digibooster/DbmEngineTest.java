/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

package com.adeptum.paula.module.digibooster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class DbmEngineTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 4096;
    private static final int STEREO = 2;
    private static final int LONGER_THAN_THE_SONG = 200;

    private final short[] out = new short[FRAMES * STEREO];

    private DbmEngine engine() throws IOException {
        return new DbmEngine(DbmReader.read(TestModules.digiBooster()), SAMPLE_RATE);
    }

    @Test
    void mixesOneTrackPerTrackOfTheModule() throws IOException {
        assertEquals(TestModules.DBM_TRACKS, engine().tracks());
    }

    @Test
    void soundsTheNoteThePatternHolds() throws IOException {
        final DbmEngine engine = engine();

        assertEquals(FRAMES, engine.mix(out, FRAMES));
        assertTrue(loudest() > 0, "the note in the pattern is heard");
    }

    @Test
    void playsTheNoteOnTheTrackItIsWrittenOn() throws IOException {
        final DbmEngine engine = engine();

        engine.mix(out, FRAMES);

        assertEquals(0, engine.track(0).instrument, "nothing is written on the first track");
        assertEquals(1, engine.track(1).instrument);
    }

    @Test
    void walksThePlaylistAndStopsAtTheEndOfTheSong() throws IOException {
        final DbmEngine engine = engine();
        int buffers = 0;
        int mixed = FRAMES;
        while (mixed == FRAMES && buffers < LONGER_THAN_THE_SONG) {
            mixed = engine.mix(out, FRAMES);
            buffers++;
        }

        assertTrue(buffers < LONGER_THAN_THE_SONG, "the song ends by itself");
        assertTrue(mixed < FRAMES, "the last buffer is the one the song ran out in");
    }

    @Test
    void startsWhereItIsAskedTo() throws IOException {
        final DbmEngine engine = engine();

        engine.position(0, 1, 0);
        engine.mix(out, FRAMES);

        assertNotEquals(0, engine.order(), "the second order of the playlist is playing");
    }

    private int loudest() {
        int loudest = 0;
        for (final short frame : out) {
            loudest = Math.max(loudest, Math.abs(frame));
        }
        return loudest;
    }
}

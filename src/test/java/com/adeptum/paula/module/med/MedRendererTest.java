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

package com.adeptum.paula.module.med;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MedRendererTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 4096;
    private static final int STEREO = 2;
    private static final int AUDIBLE = 1000;
    private static final int FIRST_TRACK = 1;
    private static final int NO_SUCH_TRACK = 99;

    private final short[] out = new short[FRAMES * STEREO];

    private Renderer renderer() throws IOException {
        return new MedRenderer(MedReader.read(TestModules.medMmd0()), SAMPLE_RATE);
    }

    @Test
    void soundsTheSongItWasGiven() throws IOException {
        final Renderer renderer = renderer();

        renderer.render(out);

        assertTrue(loudest() > AUDIBLE, "the square wave is heard, peak was " + loudest());
    }

    @Test
    void hasAScopeForEveryTrack() throws IOException {
        final Renderer renderer = renderer();
        renderer.render(out);

        assertEquals(TestModules.MED_TRACKS, renderer.channels().size());
        assertTrue(renderer.channels().getFirst().volume() > 0, "the track carrying the note is sounding");
    }

    @Test
    void silencesTheTrackTheListenerMutes() throws IOException {
        final Renderer renderer = renderer();

        renderer.mute(FIRST_TRACK, true);
        renderer.render(out);

        assertEquals(0, loudest(), "nothing is heard from the only track that had a note");
        assertTrue(renderer.channels().getFirst().muted());
    }

    /**
     * A seek backwards starts the song over on a fresh sequencer, which would put back a track the listener
     * had silenced if the muting were not kept outside it.
     */
    @Test
    void keepsATrackSilencedAcrossASeekBackwards() throws IOException {
        final Renderer renderer = renderer();
        renderer.mute(FIRST_TRACK, true);
        renderer.render(out);

        renderer.seek(Duration.ZERO);
        renderer.render(out);

        assertEquals(0, loudest());
    }

    @Test
    void ignoresATrackThatIsNotThere() throws IOException {
        final Renderer renderer = renderer();

        renderer.mute(NO_SUCH_TRACK, true);
        renderer.render(out);

        assertTrue(loudest() > AUDIBLE, "the song plays on");
    }

    @Test
    void knowsHowLongTheSongIs() throws IOException {
        assertTrue(renderer().length().orElseThrow().toMillis() > 0);
    }

    private int loudest() {
        int peak = 0;
        for (final short sample : out) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}

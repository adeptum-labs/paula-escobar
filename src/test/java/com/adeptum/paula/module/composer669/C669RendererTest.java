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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class C669RendererTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 4096;
    private static final int STEREO = 2;
    private static final int AUDIBLE = 1000;
    private static final int FIRST_CHANNEL = 1;
    private static final int NO_SUCH_CHANNEL = 99;
    private static final int CHANNELS = 8;
    private static final int CATCH_UP_BUFFERS = 100;

    private final short[] out = new short[FRAMES * STEREO];

    private Renderer renderer() throws IOException {
        return new C669Renderer(C669Reader.read(TestModules.composer669()), SAMPLE_RATE);
    }

    @Test
    void soundsTheSongItWasGiven() throws IOException {
        final Renderer renderer = renderer();
        renderer.render(out);

        assertTrue(loudest() > AUDIBLE, "the square wave is heard, peak was " + loudest());
    }

    @Test
    void hasAScopeForEveryChannel() throws IOException {
        final Renderer renderer = renderer();
        renderer.render(out);

        assertEquals(CHANNELS, renderer.channels().size());
        assertEquals(FIRST_CHANNEL, renderer.channels().getFirst().number());
        assertTrue(renderer.channels().getFirst().volume() > 0, "the channel carrying the note is sounding");
    }

    @Test
    void silencesTheChannelTheListenerMutes() throws IOException {
        final Renderer renderer = renderer();
        renderer.mute(FIRST_CHANNEL, true);
        renderer.render(out);

        assertEquals(0, loudest(), "nothing is heard from the only channel that had a note");
        assertTrue(renderer.channels().getFirst().muted());
    }

    @Test
    void keepsAChannelSilencedAcrossASeekBackwards() throws IOException {
        final Renderer renderer = renderer();
        renderer.mute(FIRST_CHANNEL, true);
        renderer.render(out);
        renderer.seek(Duration.ZERO);
        renderer.render(out);

        assertEquals(0, loudest());
    }

    @Test
    void ignoresAChannelThatIsNotThere() throws IOException {
        final Renderer renderer = renderer();
        renderer.mute(NO_SUCH_CHANNEL, true);
        renderer.render(out);

        assertTrue(loudest() > AUDIBLE, "the song plays on");
    }

    /**
     * A seek forwards is caught up in slices from the audio thread, so a few buffers of silence come first.
     */
    @Test
    void knowsHowLongTheSongIsAndEndsThere() throws IOException {
        final Renderer renderer = renderer();
        final Duration length = renderer.length().orElseThrow();
        assertTrue(length.toMillis() > 0);

        renderer.seek(length.plusSeconds(1));
        int buffers = 0;
        while (renderer.render(out) > 0 && buffers < CATCH_UP_BUFFERS) {
            buffers++;
        }
        assertTrue(buffers < CATCH_UP_BUFFERS, "past the end there is nothing left to play");
    }

    @Test
    void reportsWhereItStands() throws IOException {
        final Renderer renderer = renderer();
        renderer.render(out);

        assertEquals(FRAMES * 1000L / SAMPLE_RATE, renderer.position().toMillis());
    }

    private int loudest() {
        int peak = 0;
        for (final short sample : out) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}

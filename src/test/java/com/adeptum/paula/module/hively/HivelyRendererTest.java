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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The one note of the test module is released to silence a few ticks in, so every buffer these tests play
 * stays inside the frames the note still sounds through.
 */
class HivelyRendererTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 512;
    private static final int STEREO = 2;
    private static final int PLAYING_CHANNEL = 1;
    private static final int SILENT_CHANNEL = 2;
    private static final int CHANNELS = 4;

    private final short[] out = new short[FRAMES * STEREO];

    private static Renderer renderer() throws IOException {
        return new HivelyRenderer(HvlReader.read(TestModules.hively()), SAMPLE_RATE);
    }

    @Test
    void silencesTheChannelThatWasMuted() throws IOException {
        final Renderer renderer = renderer();

        renderer.render(out);
        assertTrue(loudest() > 0, "the note in the track is heard");

        renderer.mute(PLAYING_CHANNEL, true);
        renderer.render(out);
        assertEquals(0, loudest(), "the only channel with a note on it went quiet");
        assertTrue(renderer.channels().getFirst().muted(), "and says so");
        assertEquals(0, renderer.channels().getFirst().instrument(), "a muted channel shows nothing");
        assertFalse(renderer.channels().get(SILENT_CHANNEL - 1).muted(), "while the others are left alone");

        renderer.mute(PLAYING_CHANNEL, false);
        renderer.render(out);
        assertTrue(loudest() > 0, "and is heard again once it is let back in");
    }

    /**
     * A seek backwards starts the song over on a fresh engine, whose voices know nothing of what the listener
     * silenced until it is put back on them.
     */
    @Test
    void keepsTheMuteAcrossASeekBackwards() throws IOException {
        final Renderer renderer = renderer();
        renderer.render(out);

        renderer.mute(PLAYING_CHANNEL, true);
        renderer.seek(Duration.ZERO);
        renderer.render(out);

        assertTrue(renderer.channels().getFirst().muted());
        assertEquals(0, loudest());
    }

    @Test
    void ignoresAChannelTheModuleDoesNotHave() throws IOException {
        final Renderer renderer = renderer();

        renderer.mute(0, true);
        renderer.mute(CHANNELS + 1, true);
        renderer.render(out);

        assertEquals(CHANNELS, renderer.channels().size());
        assertTrue(loudest() > 0);
    }

    private int loudest() {
        int loudest = 0;
        for (final short frame : out) {
            loudest = Math.max(loudest, Math.abs(frame));
        }
        return loudest;
    }
}

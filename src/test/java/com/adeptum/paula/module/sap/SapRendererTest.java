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

package com.adeptum.paula.module.sap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.testing.TestSaps;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SapRendererTest {

    private static final int SAMPLE_RATE = 8000;
    private static final int FRAMES = 1024;
    private static final int LENGTH_FRAMES = SAMPLE_RATE * 2;
    private static final int AUDIBLE = 500;
    private static final int SILENT = 50;

    private final Renderer renderer = new SapRenderer(Path.of("tune.sap"), TestSaps.sap(), 0, TestSaps.LENGTH, SAMPLE_RATE);
    private final short[] buffer = new short[FRAMES * 2];

    @Test
    void rendersATone() {
        assertEquals(FRAMES, renderer.render(buffer));
        assertTrue(peak(buffer) > AUDIBLE, "the tone should be audible, peak was " + peak(buffer));
        assertEquals(Duration.ofMillis(FRAMES * 1000L / SAMPLE_RATE), renderer.position());
        assertEquals(Optional.of(TestSaps.LENGTH), renderer.length());
    }

    @Test
    void finishesExactlyAtTheSongLength() {
        int total = 0;
        for (int frames = renderer.render(buffer); frames > 0; frames = renderer.render(buffer)) {
            total += frames;
        }
        assertEquals(LENGTH_FRAMES, total);
        assertEquals(TestSaps.LENGTH, renderer.position());
        assertEquals(0, renderer.render(buffer), "a finished song stays finished");
    }

    @Test
    void seeksForwardToTheTarget() {
        renderer.seek(Duration.ofSeconds(1));
        assertEquals(Duration.ofSeconds(1), renderer.position());
        assertEquals(FRAMES, renderer.render(buffer));
        assertTrue(peak(buffer) > AUDIBLE);
    }

    @Test
    void seeksBackwardAndKeepsRendering() {
        renderer.seek(Duration.ofMillis(1500));
        renderer.seek(Duration.ofMillis(500));
        assertEquals(Duration.ofMillis(500), renderer.position());
        assertEquals(FRAMES, renderer.render(buffer));
        assertTrue(peak(buffer) > AUDIBLE);
    }

    @Test
    void clampsSeeksToTheSong() {
        renderer.seek(Duration.ofSeconds(-5));
        assertEquals(Duration.ZERO, renderer.position());
        renderer.seek(Duration.ofMinutes(5));
        assertEquals(TestSaps.LENGTH, renderer.position());
        assertEquals(0, renderer.render(buffer), "a target beyond the end leaves the song finished");
    }

    @Test
    void reportsOneScopePerPokeyChannelWithTheFirstSounding() {
        renderer.render(buffer);
        final List<ChannelState> channels = renderer.channels();

        assertEquals(4, channels.size());
        assertEquals(List.of(1, 2, 3, 4), channels.stream().map(ChannelState::number).toList());
        assertEquals(1.0, channels.get(0).volume(), 1e-9, "full volume on the tone's channel");
        assertTrue(channels.get(0).waveform().length == 128 && peak(channels.get(0).waveform()) > 0.01);
        assertEquals(0.0, channels.get(1).volume());
        assertEquals(0.0, peak(channels.get(1).waveform()));
    }

    @Test
    void silencesAMutedChannel() {
        renderer.mute(1, true);
        renderer.render(buffer);

        assertTrue(peak(buffer) < SILENT, "the only sounding channel is muted, peak was " + peak(buffer));
        assertTrue(renderer.channels().get(0).muted());
        assertEquals(0.0, renderer.channels().get(0).volume());

        renderer.mute(1, false);
        renderer.render(buffer);
        assertTrue(peak(buffer) > AUDIBLE);
        assertFalse(renderer.channels().get(0).muted());
    }

    @Test
    void keepsTheMuteAcrossASeek() {
        renderer.mute(1, true);
        renderer.seek(Duration.ofSeconds(1));
        renderer.seek(Duration.ZERO);
        renderer.render(buffer);

        assertTrue(peak(buffer) < SILENT);
        assertTrue(renderer.channels().get(0).muted());
    }

    @Test
    void ignoresChannelsItDoesNotHave() {
        renderer.mute(0, true);
        renderer.mute(5, true);
        renderer.render(buffer);

        assertTrue(peak(buffer) > AUDIBLE);
        assertTrue(renderer.channels().stream().noneMatch(ChannelState::muted));
    }

    private static int peak(short[] samples) {
        int peak = 0;
        for (final short sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    private static double peak(double[] samples) {
        double peak = 0;
        for (final double sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}

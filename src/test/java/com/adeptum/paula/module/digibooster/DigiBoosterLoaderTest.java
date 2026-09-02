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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DigiBoosterLoaderTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 2048;

    private final DigiBoosterLoader loader = new DigiBoosterLoader();

    @Test
    void takesTheModulesNamedForTheTracker(@TempDir Path dir) throws IOException {
        assertTrue(loader.supports(TestModules.writeDigiBooster(dir)));
        assertFalse(loader.supports(TestModules.writeProTracker(dir)));
    }

    @Test
    void isOneOfTheBuiltInLoaders(@TempDir Path dir) throws IOException {
        final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none());

        assertTrue(registry.loaderFor(TestModules.writeDigiBooster(dir)).orElseThrow() instanceof DigiBoosterLoader);
        assertTrue(registry.formats().contains(DigiBoosterLoader.FORMAT));
    }

    @Test
    void tellsWhatTheModuleHolds(@TempDir Path dir) throws IOException {
        final Module module = loader.load(TestModules.writeDigiBooster(dir));

        assertEquals(TestModules.TITLE, module.metadata().title());
        assertEquals(TestModules.DBM_TRACKS, module.metadata().channels());
        assertEquals(TestModules.DBM_ORDERS, module.metadata().songLength());
        assertEquals(List.of(TestModules.SAMPLE_NAME), module.metadata().instruments());
        assertEquals(List.of("DigiBooster Pro 2.21", TestModules.SONG_NAME), module.metadata().credits());
    }

    @Test
    void playsTheModuleAndKeepsTimeWhileItDoes(@TempDir Path dir) throws IOException {
        final Renderer renderer = loader.load(TestModules.writeDigiBooster(dir)).createRenderer(SAMPLE_RATE);
        final short[] frames = new short[FRAMES * 2];

        assertEquals(FRAMES, renderer.render(frames));

        assertEquals(Duration.ofMillis(FRAMES * 1000L / SAMPLE_RATE), renderer.position());
        assertTrue(renderer.length().orElseThrow().isPositive(), "the song is counted out before it is played");
    }

    @Test
    void showsWhatEveryTrackIsPlaying(@TempDir Path dir) throws IOException {
        final Renderer renderer = loader.load(TestModules.writeDigiBooster(dir)).createRenderer(SAMPLE_RATE);
        renderer.render(new short[FRAMES * 2]);

        final List<ChannelState> channels = renderer.channels();

        assertEquals(TestModules.DBM_TRACKS, channels.size());
        assertEquals(0, channels.getFirst().instrument(), "nothing is written on the first track");
        assertEquals(1, channels.get(1).instrument());
        assertTrue(channels.get(1).volume() > 0);
    }

    @Test
    void seekingBackwardsStartsTheSongAgain(@TempDir Path dir) throws IOException {
        final Renderer renderer = loader.load(TestModules.writeDigiBooster(dir)).createRenderer(SAMPLE_RATE);
        final short[] frames = new short[FRAMES * 2];
        renderer.render(frames);

        renderer.seek(Duration.ZERO);
        renderer.render(frames);

        assertEquals(Duration.ofMillis(FRAMES * 1000L / SAMPLE_RATE), renderer.position());
    }
}

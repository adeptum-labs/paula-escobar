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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.UnsupportedModuleException;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HivelyLoaderTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 2048;
    private static final int AHX_CHANNELS = 4;

    private final HivelyLoader loader = new HivelyLoader();

    @Test
    void takesTheModulesOfBothTrackers(@TempDir Path dir) throws IOException {
        assertTrue(loader.supports(Path.of("x.ahx")));
        assertTrue(loader.supports(Path.of("x.hvl")));
        assertTrue(loader.supports(Path.of("x.thx")));
        assertTrue(loader.supports(Path.of("AHX.tune")), "as Modland names its files");
        assertFalse(loader.supports(Path.of("x.mod")));
        assertTrue(loader.supports(TestModules.writeHivelyTracker(dir)));
    }

    @Test
    void isOneOfTheBuiltInLoaders(@TempDir Path dir) throws IOException {
        final ModuleLoaderRegistry registry = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none());

        assertTrue(registry.loaderFor(TestModules.writeHively(dir)).orElseThrow() instanceof HivelyLoader);
        assertTrue(registry.formats().contains(HivelyLoader.FORMAT));
    }

    @Test
    void tellsWhatTheModuleHolds(@TempDir Path dir) throws IOException {
        final Module module = loader.load(TestModules.writeHively(dir));

        assertEquals(TestModules.HIVELY_TITLE, module.metadata().title());
        assertEquals(AHX_CHANNELS, module.metadata().channels());
        assertEquals(TestModules.HIVELY_POSITIONS + " positions", module.metadata().displayLength());
        assertEquals(List.of(TestModules.HIVELY_INSTRUMENT), module.metadata().instruments());
        assertTrue(module.metadata().credits().isEmpty());
    }

    @Test
    void playsTheModuleAndKeepsTimeWhileItDoes(@TempDir Path dir) throws IOException {
        final Renderer renderer = loader.load(TestModules.writeHively(dir)).createRenderer(SAMPLE_RATE);
        final short[] frames = new short[FRAMES * 2];

        assertEquals(FRAMES, renderer.render(frames));

        assertEquals(Duration.ofMillis(FRAMES * 1000L / SAMPLE_RATE), renderer.position());
        assertTrue(renderer.length().orElseThrow().isPositive(), "the song is counted out before it is played");
    }

    @Test
    void showsWhatEveryChannelIsPlaying(@TempDir Path dir) throws IOException {
        final Renderer renderer = loader.load(TestModules.writeHively(dir)).createRenderer(SAMPLE_RATE);
        renderer.render(new short[FRAMES * 2]);

        final List<ChannelState> channels = renderer.channels();

        assertEquals(AHX_CHANNELS, channels.size());
        assertEquals(1, channels.getFirst().number());
        assertEquals(1, channels.getFirst().instrument(), "the one instrument the note names");
        assertTrue(channels.getFirst().volume() > 0);
        assertTrue(sounding(channels.getFirst()), "the square of the performance list is shown");
        assertEquals(0, channels.get(1).instrument(), "nothing is written on the other channels");
        assertFalse(sounding(channels.get(1)));
    }

    private static boolean sounding(ChannelState channel) {
        for (final double sample : channel.waveform()) {
            if (sample != 0) {
                return true;
            }
        }
        return false;
    }

    @Test
    void seekingBackwardsStartsTheSongAgain(@TempDir Path dir) throws IOException {
        final Renderer renderer = loader.load(TestModules.writeHively(dir)).createRenderer(SAMPLE_RATE);
        final short[] frames = new short[FRAMES * 2];
        renderer.render(frames);

        renderer.seek(Duration.ZERO);
        renderer.render(frames);

        assertEquals(Duration.ofMillis(FRAMES * 1000L / SAMPLE_RATE), renderer.position());
    }

    @Test
    void refusesAFileThatOnlyLooksLikeAModuleByItsName(@TempDir Path dir) throws IOException {
        final Path file = Files.write(dir.resolve("x.ahx"), "not a tune at all".getBytes(StandardCharsets.ISO_8859_1));

        assertThrows(UnsupportedModuleException.class, () -> loader.load(file));
    }
}

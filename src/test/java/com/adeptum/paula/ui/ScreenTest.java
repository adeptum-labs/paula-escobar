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

package com.adeptum.paula.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.PlaybackState;
import com.adeptum.paula.testing.TestModule;
import com.adeptum.paula.ui.visual.Palette;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

class ScreenTest {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    private final PlayerView view = PlayerView.builder()
            .module(new TestModule(Path.of("dir", "space.mod"), ModuleMetadata.builder()
                    .title("Space Debris")
                    .format(new ModuleFormat("mod", "ProTracker", Set.of("mod")))
                    .channels(4)
                    .songLength(42)
                    .instruments(List.of("kick", "snare"))
                    .build()))
            .trackLabel("Assembly 1995 · 4 Channel Music  #1 Space Debris by Captain")
            .state(PlaybackState.PLAYING)
            .position(Duration.ofSeconds(83))
            .length(Duration.ofSeconds(250))
            .track(2)
            .trackCount(5)
            .spectrum(new double[32])
            .peaks(new double[32])
            .vuLeft(0.5)
            .vuRight(0.25)
            .channels(List.of(
                    new ChannelState(1, 2, 0.8, new double[] {0, 1, 0, -1}),
                    new ChannelState(2, 0, 0, new double[4]),
                    new ChannelState(3, 0, 0, new double[4]),
                    new ChannelState(4, 0, 0, new double[4])))
            .build();

    @Test
    void fillsTheWholeTerminalWithBarsAndPanels() {
        final List<AttributedString> lines = Screen.render(view, WIDTH, HEIGHT);

        assertEquals(HEIGHT, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.columnLength() == WIDTH), "every line spans the width");
        assertTrue(lines.get(0).toString().contains("Paula"), "title bar");
        assertTrue(lines.get(HEIGHT - 1).toString().contains("quit"), "key bar at the bottom");
        final String all = String.join("\n", text(lines));
        assertTrue(all.contains("Spectrum") && all.contains("Channels") && all.contains("Now playing"));
    }

    @Test
    void showsTheSongDetails() {
        final String all = String.join("\n", text(Screen.render(view, WIDTH, HEIGHT)));

        assertTrue(all.contains("Space Debris"));
        assertTrue(all.contains("space.mod"));
        assertTrue(all.contains("ProTracker, 4 channels, 42 positions"));
        assertTrue(all.contains("2 / 5"));
        assertTrue(all.contains("PLAYING"));
        assertTrue(all.contains("kick") && all.contains("snare"));
        assertTrue(all.contains("01:23") && all.contains("04:10"), "elapsed and total time");
    }

    @Test
    void highlightsTheInstrumentThatIsPlaying() {
        final List<AttributedString> lines = Screen.render(view, WIDTH, HEIGHT);
        final AttributedString snare = lines.stream().filter(line -> line.toString().contains("snare")).findFirst().orElseThrow();
        final AttributedString kick = lines.stream().filter(line -> line.toString().contains("kick")).findFirst().orElseThrow();

        assertEquals(Palette.ACTIVE, snare.styleAt(snare.toString().indexOf("snare")), "instrument two sounds on channel one");
        assertEquals(Palette.VALUE, kick.styleAt(kick.toString().indexOf("kick")));
    }

    @Test
    void drawsOneScopePerChannel() {
        final String all = String.join("\n", text(Screen.render(view, WIDTH, HEIGHT)));
        assertTrue(all.contains("⠤") || all.contains("⣀") || all.contains("⠉"), "braille dots appear in the scopes");
        for (int channel = 1; channel <= 4; channel++) {
            assertTrue(all.contains(" " + channel + " "), "channel " + channel + " is labelled");
        }
    }

    @Test
    void stacksPanelsOnNarrowTerminals() {
        final List<AttributedString> lines = Screen.render(view, 80, 30);

        assertEquals(30, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.columnLength() == 80));
        final String all = String.join("\n", text(lines));
        assertTrue(all.contains("Spectrum") && all.contains("Channels") && all.contains("Space Debris"));
    }

    @Test
    void showsAnIdleScreenWithoutAModule() {
        final PlayerView idle = PlayerView.builder().state(PlaybackState.STOPPED).position(Duration.ZERO).build();
        final List<AttributedString> lines = Screen.render(idle, WIDTH, HEIGHT);

        assertEquals(HEIGHT, lines.size());
        assertTrue(String.join("\n", text(lines)).contains("Nothing playing, press b to browse the party archives"));
        assertTrue(lines.get(HEIGHT - 1).toString().contains("browse"));
    }

    @Test
    void showsTheStatusLine() {
        final PlayerView loading = PlayerView.builder().state(PlaybackState.STOPPED).position(Duration.ZERO)
                .trackLabel("Funkyeeh").status("Loading Funkyeeh").build();

        assertTrue(String.join("\n", text(Screen.render(loading, WIDTH, HEIGHT))).contains("Loading Funkyeeh"));
    }

    @Test
    void survivesTinyTerminals() {
        final List<AttributedString> lines = Screen.render(view, 20, 5);
        assertEquals(5, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.columnLength() == 20));
    }

    private static List<String> text(List<AttributedString> lines) {
        return lines.stream().map(AttributedString::toString).toList();
    }
}

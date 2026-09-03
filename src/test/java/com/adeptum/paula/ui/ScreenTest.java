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
        assertTrue(lines.get(0).toString().contains("Paula Escobar"), "title bar");
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
            assertTrue(all.matches("(?s).*[│ \\u2800-\\u28ff]" + channel + " {2}[\\u2800-\\u28ff].*"), "channel " + channel + " is labelled before its scope");
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
    void drawsTheKeyBarExactlyOnceOnShortScreens() {
        final PlayerView idle = PlayerView.builder().state(PlaybackState.STOPPED).position(Duration.ZERO).build();
        final long keyBars = text(Screen.render(idle, WIDTH, HEIGHT)).stream().filter(line -> line.contains("quit")).count();
        assertEquals(1, keyBars);
    }

    @Test
    void showsCreditsForFormatsWithoutInstruments() {
        final PlayerView sid = PlayerView.builder()
                .module(new TestModule(Path.of("tune.sid"), ModuleMetadata.builder()
                        .title("Commando")
                        .format(new ModuleFormat("sid", "PSID", Set.of("sid")))
                        .channels(3)
                        .songLength(4)
                        .lengthUnit("subtunes")
                        .credits(List.of("Rob Hubbard", "1985 Elite"))
                        .build()))
                .trackLabel("tune.sid")
                .state(PlaybackState.PLAYING)
                .position(Duration.ZERO)
                .track(1)
                .trackCount(1)
                .build();

        final String all = String.join("\n", text(Screen.render(sid, WIDTH, HEIGHT)));
        assertTrue(all.contains("Rob Hubbard") && all.contains("1985 Elite"));
        assertTrue(all.contains("PSID, 3 channels, 4 subtunes"));
        assertTrue(all.contains("mix"), "a format without channels gets one mixed scope");
    }

    @Test
    void spectrumBarsFillThePanelWidth() {
        final PlayerView loud = PlayerView.builder().module(view.module()).state(PlaybackState.PLAYING).position(Duration.ZERO)
                .track(1).trackCount(1).spectrum(full(32)).peaks(full(32)).build();
        final List<String> lines = text(Screen.render(loud, WIDTH, HEIGHT));
        final String bars = lines.stream().filter(line -> line.contains("█")).findFirst().orElseThrow();
        final int panelEnd = bars.lastIndexOf('│');
        final int lastBar = bars.lastIndexOf('█');
        assertTrue(panelEnd - lastBar <= 2, "bars reach the right edge of the panel: " + bars);
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

    private static double[] full(int bands) {
        final double[] levels = new double[bands];
        java.util.Arrays.fill(levels, 1.0);
        return levels;
    }

    private static List<String> text(List<AttributedString> lines) {
        return lines.stream().map(AttributedString::toString).toList();
    }
}

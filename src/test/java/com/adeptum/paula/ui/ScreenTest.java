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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.PlaybackState;
import com.adeptum.paula.playback.Progress;
import com.adeptum.paula.testing.TestModule;
import com.adeptum.paula.ui.visual.Palette;
import com.adeptum.paula.ui.visual.Waterfall;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

class ScreenTest {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    private final PlayerView.PlayerViewBuilder playing = PlayerView.builder()
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
                    new ChannelState(1, 2, 0.8, new double[] {0, 1, 0, -1}, false),
                    new ChannelState(2, 0, 0, new double[4], false),
                    new ChannelState(3, 0, 0, new double[4], false),
                    new ChannelState(4, 0, 0, new double[4], false)));

    private final PlayerView view = playing.build();

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
    void knowsWhereTheUpperPanelIsSoAClickCanTurnItOver() {
        final Scopes grid = Screen.scopes(view, WIDTH, HEIGHT);

        assertTrue(Screen.onVisual(view, WIDTH, HEIGHT, WIDTH - 2, 2), "inside the panel");
        assertFalse(Screen.onVisual(view, WIDTH, HEIGHT, WIDTH - 2, 0), "the title bar is not it");
        assertFalse(Screen.onVisual(view, WIDTH, HEIGHT, WIDTH - 2, grid.top()), "nor the scopes below it");
        assertFalse(Screen.onVisual(view, WIDTH, HEIGHT, 2, 2), "nor the details panel beside it");
    }

    @Test
    void hasNoPanelToClickOnWithoutAModule() {
        final PlayerView idle = PlayerView.builder().state(PlaybackState.STOPPED).position(Duration.ZERO).build();

        assertFalse(Screen.onVisual(idle, WIDTH, HEIGHT, WIDTH / 2, 2));
    }

    @Test
    void findsTheChannelUnderAClickOnItsScope() {
        assertScopesAreClickable(WIDTH, HEIGHT);
        assertScopesAreClickable(80, 30);
    }

    @Test
    void ignoresClicksThatMissTheScopes() {
        final Scopes grid = Screen.scopes(view, WIDTH, HEIGHT);

        assertEquals(OptionalInt.empty(), Screen.channelAt(view, WIDTH, HEIGHT, 0, 0), "the title bar");
        assertEquals(OptionalInt.empty(), Screen.channelAt(view, WIDTH, HEIGHT, grid.left() - 1, grid.top()), "left of the box");
        assertEquals(OptionalInt.empty(), Screen.channelAt(view, WIDTH, HEIGHT, grid.left(), grid.top() - 1), "above the box");
        assertEquals(OptionalInt.empty(),
                Screen.channelAt(view, WIDTH, HEIGHT, grid.left() + grid.columns() * grid.cellWidth(), grid.top()), "right of the last cell");
    }

    @Test
    void leavesTheMixOfAFormatWithoutChannelsUnclickable() {
        final PlayerView mixed = playing.channels(List.of()).build();
        final Scopes grid = Screen.scopes(mixed, WIDTH, HEIGHT);

        assertEquals(OptionalInt.empty(), Screen.channelAt(mixed, WIDTH, HEIGHT, grid.left(), grid.top()));
    }

    @Test
    void strikesOutTheLabelOfASilencedChannel() {
        final PlayerView silenced = playing.channels(List.of(
                new ChannelState(1, 2, 0.8, new double[] {0, 1, 0, -1}, false),
                new ChannelState(2, 0, 0, new double[4], true))).build();
        final Scopes grid = Screen.scopes(silenced, WIDTH, HEIGHT);
        final List<AttributedString> lines = Screen.render(silenced, WIDTH, HEIGHT);

        assertEquals(Palette.MUTED, lines.get(grid.top()).styleAt(grid.left() + grid.cellWidth()), "channel two is off");
        assertEquals(Palette.ACTIVE, lines.get(grid.top()).styleAt(grid.left()), "channel one still sounds");
    }

    @Test
    void namesTheVisualiserAboveTheUpperPanel() {
        for (final Visual visual : Visual.values()) {
            final String all = String.join("\n", text(Screen.render(playing.visual(visual).build(), WIDTH, HEIGHT)));
            assertTrue(all.contains(visual.title()), visual + " names its panel");
            assertTrue(all.contains("Channels"), "the scopes stay whatever the upper panel shows");
        }
    }

    @Test
    void drawsTheVectorscopeWithBrailleDots() {
        final PlayerView scope = playing.visual(Visual.VECTORSCOPE).stereo(hardPanned()).build();

        final String all = String.join("\n", text(Screen.render(scope, WIDTH, HEIGHT)));

        assertTrue(all.matches("(?s).*[\u2801-\u28ff].*"), "a lit braille cell appears");
    }

    @Test
    void drawsTheWaterfallAsShadedRows() {
        final Waterfall history = new Waterfall(32, 8);
        history.feed(full(32));
        final PlayerView fall = playing.visual(Visual.WATERFALL).waterfall(history).build();

        final String all = String.join("\n", text(Screen.render(fall, WIDTH, HEIGHT)));

        assertTrue(all.contains("█"), "a loud row is drawn solid");
    }

    @Test
    void cyclesThroughTheVisualisersAndBackAgain() {
        assertEquals(Visual.WATERFALL, Visual.SPECTRUM.next());
        assertEquals(Visual.VECTORSCOPE, Visual.WATERFALL.next());
        assertEquals(Visual.SPECTRUM, Visual.VECTORSCOPE.next(), "and round to the start");
    }

    @Test
    void stacksPanelsOnNarrowTerminals() {
        final List<AttributedString> lines = Screen.render(view, 80, 30);

        assertEquals(30, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.columnLength() == 80));
        final String all = String.join("\n", text(lines));
        assertTrue(all.contains("Spectrum") && all.contains("Channels") && all.contains("Space Debris"));
    }

    /**
     * The grid a click is measured against has to be the one the scopes were drawn on, so every cell it names
     * is checked against the label actually written there.
     */
    private void assertScopesAreClickable(int width, int height) {
        final List<AttributedString> lines = Screen.render(view, width, height);
        final Scopes grid = Screen.scopes(view, width, height);

        for (int channel = 1; channel <= 4; channel++) {
            final int row = grid.top() + (channel - 1) / grid.columns() * grid.cellHeight();
            final int column = grid.left() + (channel - 1) % grid.columns() * grid.cellWidth();
            assertEquals(String.valueOf(channel), lines.get(row).toString().substring(column, column + 1),
                    "channel " + channel + " is drawn where the grid says at " + width + "x" + height);
            assertEquals(OptionalInt.of(channel), Screen.channelAt(view, width, height, column + 1, row),
                    "a click anywhere in the cell picks channel " + channel);
        }
    }

    @Test
    void drawsABarUnderWorkWhoseEndIsKnown() {
        final PlayerView downloading = playing.progress(new Progress.Step("Downloading tune.mod · 50% of 700 kB", 0.5, 0)).build();

        final List<String> lines = text(Screen.render(downloading, WIDTH, HEIGHT));

        assertTrue(lines.stream().anyMatch(l -> l.contains("Downloading tune.mod")), "it says what it is doing");
        assertTrue(lines.stream().anyMatch(l -> l.contains("████") && !l.contains("Downloading")), "and draws a bar");
    }

    /**
     * An archive says how many entries it has got through, never how many are left, so the bar sweeps rather
     * than fills and claims nothing it does not know.
     */
    @Test
    void sweepsABarUnderWorkWhoseEndIsNotInSight() {
        final PlayerView unpacking = playing.progress(new Progress.Step("Unpacking party.zip · 12 entries", -1, 12)).build();

        final List<String> lines = text(Screen.render(unpacking, WIDTH, HEIGHT));
        final String bar = lines.stream().filter(l -> l.contains("█") && !l.contains("Unpacking")).findFirst().orElseThrow();

        final String inside = bar.substring(bar.indexOf('│') + 1, bar.indexOf('│', 1));
        assertTrue(inside.startsWith(" "), "the block has moved off the left: " + inside);
        assertTrue(inside.strip().chars().allMatch(c -> c == '█'), "and it is one unbroken block: " + inside);
        assertEquals(12, inside.indexOf('█'), "as far along as there are entries got through");
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

    /**
     * A quarter turn of a circle in the scope, which is what a channel panned away from the other draws.
     */
    private static double[] hardPanned() {
        final double[] frames = new double[512];
        for (int frame = 0; frame < frames.length / 2; frame++) {
            final double angle = 2 * Math.PI * frame / (frames.length / 2.0);
            frames[frame * 2] = Math.sin(angle);
            frames[frame * 2 + 1] = Math.cos(angle);
        }
        return frames;
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

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

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.PlaybackState;
import com.adeptum.paula.testing.TestModule;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

class ScreenTest {

    private static final int WIDTH = 80;
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
            .track(2)
            .trackCount(5)
            .build();

    @Test
    void showsTitleFileStatusAndInstruments() {
        final List<String> lines = render(view, HEIGHT);

        assertTrue(lines.contains("Title   Space Debris"));
        assertTrue(lines.contains("File    space.mod"));
        assertTrue(lines.contains("Format  ProTracker, 4 channels, 42 positions"));
        assertTrue(lines.contains("Track   2 / 5  Assembly 1995 · 4 Channel Music  #1 Space Debris by Captain"));
        assertTrue(lines.contains("Status  PLAYING  01:23"));
        assertTrue(lines.contains("01 kick"));
        assertTrue(lines.contains("02 snare"));
    }

    @Test
    void showsAnIdleScreenWithoutAModule() {
        final List<String> lines = render(PlayerView.builder().state(PlaybackState.STOPPED).position(Duration.ZERO).build(), HEIGHT);

        assertTrue(lines.contains("Nothing playing, press b to browse the party archives"));
        assertTrue(lines.stream().anyMatch(line -> line.contains("b browse")));
    }

    @Test
    void showsTheStatusLine() {
        final PlayerView loading = PlayerView.builder().state(PlaybackState.STOPPED).position(Duration.ZERO)
                .trackLabel("Funkyeeh").status("Loading Funkyeeh").build();

        assertTrue(render(loading, HEIGHT).contains("Loading Funkyeeh"));
    }

    @Test
    void showsCreditsForFormatsWithoutInstruments() {
        final PlayerView sid = PlayerView.builder()
                .module(new TestModule(Path.of("tune.sid"), ModuleMetadata.builder()
                        .title("Commando")
                        .format(new ModuleFormat("sid", "PSID", Set.of("sid")))
                        .channels(3)
                        .songLength(1)
                        .credits(List.of("Rob Hubbard", "1985 Elite"))
                        .build()))
                .trackLabel("tune.sid")
                .state(PlaybackState.PLAYING)
                .position(Duration.ZERO)
                .track(1)
                .trackCount(1)
                .build();

        final List<String> lines = render(sid, HEIGHT);
        assertTrue(lines.contains("Rob Hubbard"));
        assertTrue(lines.contains("1985 Elite"));
    }

    @Test
    void clipsLinesToTerminalWidth() {
        assertTrue(Screen.render(view, 10, HEIGHT).stream().allMatch(line -> line.columnLength() <= 10));
    }

    @Test
    void clipsToTerminalHeightKeepingTheKeyBar() {
        final List<String> lines = render(view, 5);
        assertTrue(lines.size() <= 5);
        assertTrue(lines.get(lines.size() - 1).contains("q quit"));
    }

    private static List<String> render(PlayerView view, int height) {
        return Screen.render(view, WIDTH, height).stream().map(AttributedString::toString).toList();
    }
}

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

import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.protracker.ProTrackerLoader;
import com.adeptum.paula.module.protracker.ProTrackerModule;
import com.adeptum.paula.playback.PlaybackState;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

class ScreenTest {

    private final PlayerView view = PlayerView.builder()
            .module(new ProTrackerModule(Path.of("dir", "space.mod"), ModuleMetadata.builder()
                    .title("Space Debris")
                    .format(ProTrackerLoader.FORMAT)
                    .channels(4)
                    .songLength(42)
                    .instruments(List.of("kick", "snare"))
                    .build()))
            .state(PlaybackState.PLAYING)
            .position(Duration.ofSeconds(83))
            .track(2)
            .trackCount(5)
            .build();

    @Test
    void showsTitleFileStatusAndInstruments() {
        final List<String> lines = Screen.render(view, 80).stream().map(AttributedString::toString).toList();

        assertTrue(lines.contains("Title   Space Debris"));
        assertTrue(lines.contains("File    space.mod"));
        assertTrue(lines.contains("Track   2 / 5"));
        assertTrue(lines.contains("Status  PLAYING  01:23"));
        assertTrue(lines.contains("01 kick"));
        assertTrue(lines.contains("02 snare"));
    }

    @Test
    void clipsLinesToTerminalWidth() {
        assertTrue(Screen.render(view, 10).stream().allMatch(line -> line.columnLength() <= 10));
    }
}

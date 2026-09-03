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

import java.util.List;
import java.util.stream.IntStream;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

class ShortcutsTest {

    private static final int WIDTH = 60;
    private static final int HEIGHT = 12;
    private static final List<Frame.Key> KEYS = List.of(
            new Frame.Key("r", "fetch this list afresh"), new Frame.Key("q", "quit"));

    private static List<AttributedString> screen() {
        return IntStream.range(0, HEIGHT)
                .mapToObj(row -> new AttributedString("|" + String.valueOf('a' + row).repeat(1).charAt(0) + "|".repeat(WIDTH - 2)))
                .toList();
    }

    @Test
    void laysTheKeysOverTheScreen() {
        final List<String> lines = Shortcuts.over(screen(), KEYS, WIDTH, HEIGHT).stream().map(AttributedString::toString).toList();

        assertEquals(HEIGHT, lines.size(), "the screen keeps its shape");
        assertTrue(lines.stream().anyMatch(line -> line.contains("Keys")), "the box is titled");
        assertTrue(lines.stream().anyMatch(line -> line.contains("fetch this list afresh")), "and holds every key");
        assertTrue(lines.stream().anyMatch(line -> line.contains("quit")));
    }

    @Test
    void leavesTheScreenShowingAroundTheBox() {
        final List<String> lines = Shortcuts.over(screen(), KEYS, WIDTH, HEIGHT).stream().map(AttributedString::toString).toList();
        final String overlaid = lines.stream().filter(line -> line.contains("quit")).findFirst().orElseThrow();

        assertTrue(overlaid.startsWith("|"), "what the box does not cover is left alone");
        assertTrue(overlaid.endsWith("|"), "on both sides");
        assertEquals(WIDTH, overlaid.length(), "and the line keeps its width");
    }

    @Test
    void aScreenWithNoRoomIsLeftAsItIs() {
        final List<AttributedString> narrow = List.of(new AttributedString("|||"));

        assertEquals(narrow, Shortcuts.over(narrow, KEYS, 3, 1));
    }
}

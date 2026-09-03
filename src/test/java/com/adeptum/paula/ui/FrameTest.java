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
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

class FrameTest {

    @Test
    void barsSpanTheWholeWidth() {
        final AttributedString title = Frame.titleBar("Paula", "browse", 40);
        assertEquals(40, title.columnLength());
        assertTrue(title.toString().contains("Paula"));
        assertEquals(40, Frame.footer(List.of(new Frame.Key("q", "quit")), 40).columnLength());
    }

    @Test
    void boxesHaveCornersAndExactSize() {
        final List<AttributedString> box = Frame.box("Scope", List.of(new AttributedString("hello")), 12, 4);
        assertEquals(4, box.size());
        assertTrue(box.stream().allMatch(line -> line.columnLength() == 12));
        assertTrue(box.get(0).toString().startsWith("┌") && box.get(0).toString().endsWith("┐"));
        assertTrue(box.get(0).toString().contains("Scope"));
        assertEquals("│hello     │", box.get(1).toString());
        assertEquals("│          │", box.get(2).toString(), "missing content rows are blank");
        assertEquals("└──────────┘", box.get(3).toString());
    }

    @Test
    void contentWiderOrTallerThanTheBoxIsClipped() {
        final List<AttributedString> box = Frame.box("", List.of(new AttributedString("abcdefghijkl"), new AttributedString("x"), new AttributedString("y")), 6, 3);
        assertEquals(List.of("┌────┐", "│abcd│", "└────┘"), box.stream().map(AttributedString::toString).toList());
    }

    @Test
    void columnsSitSideBySide() {
        final List<AttributedString> merged = Frame.sideBySide(
                List.of(new AttributedString("ab"), new AttributedString("c")),
                List.of(new AttributedString("xy")), 3, 6);
        assertEquals(List.of("ab xy ", "c     "), merged.stream().map(AttributedString::toString).toList());
    }
}

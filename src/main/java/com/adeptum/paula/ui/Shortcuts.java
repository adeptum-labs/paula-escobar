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

import com.adeptum.paula.ui.visual.Palette;
import java.util.ArrayList;
import java.util.List;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * The keys of the screen in view, laid over it in a box. The footer has room for the handful in constant use;
 * the rest are here, a keypress away.
 */
public final class Shortcuts {

    private static final String TITLE = " Keys ";
    private static final String GAP = "  ";
    private static final String SEPARATOR = "  ";
    private static final int PADDING = 2;
    private static final int SHORTEST_KEY = 9;

    private Shortcuts() {
    }

    public static List<AttributedString> over(List<AttributedString> screen, List<Frame.Key> keys, int width, int height) {
        final int keyWidth = Math.max(SHORTEST_KEY, keys.stream().mapToInt(key -> key.key().length()).max().orElse(0));
        final List<AttributedString> content = keys.stream().map(key -> line(key, keyWidth)).toList();
        final int inner = keyWidth + SEPARATOR.length()
                + keys.stream().mapToInt(key -> key.action().length()).max().orElse(0) + PADDING * 2;
        final int boxWidth = Math.min(width, inner + 2);
        final int boxHeight = Math.min(height, content.size() + 2);
        if (boxWidth < TITLE.length() + 2 || boxHeight < 3) {
            return screen;
        }
        final List<AttributedString> box = Frame.box(TITLE, content, boxWidth, boxHeight);
        final int left = (width - boxWidth) / 2;
        final int top = Math.max(0, (height - boxHeight) / 2);
        final List<AttributedString> lines = new ArrayList<>(screen);
        for (int row = 0; row < box.size() && top + row < lines.size(); row++) {
            lines.set(top + row, laid(lines.get(top + row), box.get(row), left, width));
        }
        return List.copyOf(lines);
    }

    private static AttributedString line(Frame.Key key, int keyWidth) {
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT).append(GAP)
                .style(Palette.ACCENT).append(Frame.pad(new AttributedString(key.key()), keyWidth, AttributedStyle.DEFAULT))
                .style(Palette.VALUE).append(SEPARATOR).append(key.action())
                .toAttributedString();
    }

    /**
     * The box is written across the screen line rather than over the whole of it, so what it does not cover is
     * left showing.
     */
    private static AttributedString laid(AttributedString line, AttributedString box, int left, int width) {
        final AttributedString padded = Frame.pad(line, width, AttributedStyle.DEFAULT);
        final int right = Math.min(padded.length(), left + box.length());
        return new AttributedStringBuilder()
                .append(padded.subSequence(0, Math.min(left, padded.length())))
                .append(box)
                .append(padded.subSequence(right, padded.length()))
                .toAttributedString();
    }
}

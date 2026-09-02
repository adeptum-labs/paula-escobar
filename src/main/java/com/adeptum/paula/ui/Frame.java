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

import com.adeptum.paula.ui.visual.Palette;
import java.util.ArrayList;
import java.util.List;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Layout pieces that always fill their width, so backgrounds and borders cover the whole terminal.
 */
public final class Frame {

    public record Key(String key, String action) {
    }

    private static final String SEPARATOR = " ▎ ";
    private static final String TITLE_PADDING = "  ";
    private static final char HORIZONTAL = '─';
    private static final char VERTICAL = '│';

    private Frame() {
    }

    public static AttributedString titleBar(String application, String section, int width) {
        final String text = TITLE_PADDING + application + SEPARATOR + section;
        final AttributedStringBuilder bar = new AttributedStringBuilder();
        for (int column = 0; column < width; column++) {
            bar.style(Palette.titleBackground((double) column / Math.max(1, width - 1)));
            bar.append(column < text.length() ? text.charAt(column) : ' ');
        }
        return bar.toAttributedString();
    }

    public static AttributedString footer(List<Key> keys, int width) {
        final AttributedStringBuilder bar = new AttributedStringBuilder();
        bar.style(Palette.FOOTER).append(' ');
        for (final Key key : keys) {
            bar.style(Palette.FOOTER_KEY).append(key.key()).style(Palette.FOOTER).append(' ').append(key.action()).append("  ");
        }
        return pad(bar.toAttributedString(), width, Palette.FOOTER);
    }

    /**
     * A bordered panel of exactly the given size; content is clipped to fit and missing rows are left blank.
     */
    public static List<AttributedString> box(String title, List<AttributedString> content, int width, int height) {
        final int inner = Math.max(0, width - 2);
        final List<AttributedString> lines = new ArrayList<>(height);
        lines.add(edge('┌', title, '┐', width));
        for (int row = 0; row < height - 2; row++) {
            final AttributedString body = row < content.size() ? content.get(row) : AttributedString.EMPTY;
            lines.add(new AttributedStringBuilder()
                    .style(Palette.BORDER).append(VERTICAL)
                    .append(pad(body, inner, AttributedStyle.DEFAULT))
                    .style(Palette.BORDER).append(VERTICAL)
                    .toAttributedString());
        }
        if (height > 1) {
            lines.add(edge('└', "", '┘', width));
        }
        return lines.subList(0, Math.min(height, lines.size()));
    }

    public static List<AttributedString> sideBySide(List<AttributedString> left, List<AttributedString> right, int leftWidth, int width) {
        final List<AttributedString> lines = new ArrayList<>();
        for (int row = 0; row < Math.max(left.size(), right.size()); row++) {
            final AttributedString a = row < left.size() ? left.get(row) : AttributedString.EMPTY;
            final AttributedString b = row < right.size() ? right.get(row) : AttributedString.EMPTY;
            lines.add(new AttributedStringBuilder()
                    .append(pad(a, leftWidth, AttributedStyle.DEFAULT))
                    .append(pad(b, width - leftWidth, AttributedStyle.DEFAULT))
                    .toAttributedString());
        }
        return lines;
    }

    public static AttributedString pad(AttributedString line, int width, AttributedStyle padding) {
        final AttributedStringBuilder padded = new AttributedStringBuilder();
        padded.append(line.columnSubSequence(0, Math.min(line.columnLength(), width)));
        padded.style(padding);
        for (int column = padded.columnLength(); column < width; column++) {
            padded.append(' ');
        }
        return padded.toAttributedString();
    }

    public static AttributedString centered(String text, int width, AttributedStyle style) {
        final int left = Math.max(0, (width - text.length()) / 2);
        return pad(new AttributedStringBuilder().style(style).append(" ".repeat(left)).append(text).toAttributedString(), width, style);
    }

    private static AttributedString edge(char start, String title, char end, int width) {
        final AttributedStringBuilder line = new AttributedStringBuilder().style(Palette.BORDER).append(start);
        if (!title.isEmpty()) {
            line.append(HORIZONTAL).style(Palette.PANEL_TITLE).append(' ').append(title).append(' ').style(Palette.BORDER);
        }
        while (line.columnLength() < width - 1) {
            line.append(HORIZONTAL);
        }
        return line.append(end).toAttributedString().columnSubSequence(0, Math.max(0, width));
    }
}

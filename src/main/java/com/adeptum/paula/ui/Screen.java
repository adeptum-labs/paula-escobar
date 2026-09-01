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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import com.adeptum.paula.module.ModuleMetadata;

/**
 * Builds the lines of the player screen from a {@link PlayerView}; has no terminal dependency so it can be unit tested.
 */
public final class Screen {

    private static final int MAX_INSTRUMENT_LINES = 16;

    private Screen() {
    }

    public static List<AttributedString> render(PlayerView view, int width) {
        final ModuleMetadata meta = view.module().metadata();
        final List<AttributedString> lines = new ArrayList<>();
        lines.add(line(b -> b.style(Theme.TITLE).append("Paula").style(Theme.LABEL).append("  chip & module player")));
        lines.add(AttributedString.EMPTY);
        lines.add(field("Title   ", meta.displayTitle()));
        lines.add(field("File    ", view.module().source().getFileName().toString()));
        lines.add(field("Format  ", meta.format().name() + ", " + meta.channels() + " channels, " + meta.songLength() + " positions"));
        lines.add(field("Track   ", view.track() + " / " + view.trackCount()));
        lines.add(line(b -> b.style(Theme.LABEL).append("Status  ").style(Theme.STATUS).append(view.state().name())
                .style(Theme.LABEL).append("  ").style(Theme.ACCENT).append(clock(view.position()))));
        lines.add(AttributedString.EMPTY);
        instrumentLines(meta, lines);
        lines.add(AttributedString.EMPTY);
        lines.add(line(b -> b.style(Theme.KEY).append("space").style(Theme.LABEL).append(" pause  ")
                .style(Theme.KEY).append("←/→").style(Theme.LABEL).append(" seek  ")
                .style(Theme.KEY).append("n").style(Theme.LABEL).append(" next  ")
                .style(Theme.KEY).append("p").style(Theme.LABEL).append(" previous  ")
                .style(Theme.KEY).append("q").style(Theme.LABEL).append(" quit")));
        return lines.stream().map(l -> l.columnSubSequence(0, Math.min(l.columnLength(), width))).toList();
    }

    static String clock(Duration position) {
        return String.format("%02d:%02d", position.toMinutes(), position.toSecondsPart());
    }

    private static void instrumentLines(ModuleMetadata meta, List<AttributedString> lines) {
        final List<String> instruments = meta.instruments();
        for (int i = 0; i < Math.min(instruments.size(), MAX_INSTRUMENT_LINES); i++) {
            final String number = String.format("%02d ", i + 1);
            final String name = instruments.get(i);
            lines.add(line(b -> b.style(Theme.LABEL).append(number).style(Theme.VALUE).append(name)));
        }
    }

    private static AttributedString field(String label, String value) {
        return line(b -> b.style(Theme.LABEL).append(label).style(Theme.VALUE).append(value));
    }

    private static AttributedString line(java.util.function.Consumer<AttributedStringBuilder> content) {
        final AttributedStringBuilder builder = new AttributedStringBuilder();
        content.accept(builder);
        return builder.toAttributedString();
    }
}

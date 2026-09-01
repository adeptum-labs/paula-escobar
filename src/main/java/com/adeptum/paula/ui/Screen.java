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
import java.util.function.Consumer;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import com.adeptum.paula.module.ModuleMetadata;

/**
 * Builds the lines of the player screen from a {@link PlayerView}; has no terminal dependency so it can be unit tested.
 */
public final class Screen {

    private static final int MAX_INSTRUMENT_LINES = 16;
    private static final String IDLE = "Nothing playing, press b to browse the party archives";

    private Screen() {
    }

    public static List<AttributedString> render(PlayerView view, int width, int height) {
        final List<AttributedString> lines = new ArrayList<>();
        lines.add(line(b -> b.style(Theme.TITLE).append("Paula").style(Theme.LABEL).append("  chip & module player")));
        lines.add(AttributedString.EMPTY);
        if (view.module() == null) {
            lines.add(line(b -> b.style(Theme.VALUE).append(IDLE)));
        } else {
            moduleLines(view, lines);
        }
        if (view.status() != null) {
            lines.add(line(b -> b.style(Theme.ACCENT).append(view.status())));
        }
        lines.add(AttributedString.EMPTY);
        return fit(lines, keyBar(), width, height);
    }

    static String clock(Duration position) {
        return String.format("%02d:%02d", position.toMinutes(), position.toSecondsPart());
    }

    static List<AttributedString> fit(List<AttributedString> body, AttributedString keyBar, int width, int height) {
        final List<AttributedString> lines = new ArrayList<>(body.subList(0, Math.max(0, Math.min(body.size(), height - 1))));
        lines.add(keyBar);
        return lines.stream().map(l -> l.columnSubSequence(0, Math.min(l.columnLength(), width))).toList();
    }

    static AttributedString line(Consumer<AttributedStringBuilder> content) {
        final AttributedStringBuilder builder = new AttributedStringBuilder();
        content.accept(builder);
        return builder.toAttributedString();
    }

    static void key(AttributedStringBuilder builder, String key, String action) {
        builder.style(Theme.KEY).append(key).style(Theme.LABEL).append(' ').append(action).append("  ");
    }

    private static void moduleLines(PlayerView view, List<AttributedString> lines) {
        final ModuleMetadata meta = view.module().metadata();
        lines.add(field("Title   ", meta.displayTitle()));
        lines.add(field("File    ", view.module().source().getFileName().toString()));
        lines.add(field("Format  ", meta.format().name() + ", " + meta.channels() + " channels, " + meta.displayLength()));
        lines.add(field("Track   ", view.track() + " / " + view.trackCount() + "  " + view.trackLabel()));
        lines.add(line(b -> b.style(Theme.LABEL).append("Status  ").style(Theme.STATUS).append(view.state().name())
                .style(Theme.LABEL).append("  ").style(Theme.ACCENT).append(clock(view.position()))));
        lines.add(AttributedString.EMPTY);
        meta.credits().forEach(credit -> lines.add(line(b -> b.style(Theme.VALUE).append(credit))));
        instrumentLines(meta, lines);
    }

    private static AttributedString keyBar() {
        return line(b -> {
            key(b, "space", "pause");
            key(b, "←/→", "seek");
            key(b, "n", "next");
            key(b, "p", "previous");
            key(b, "b", "browse");
            key(b, "q", "quit");
        });
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
}

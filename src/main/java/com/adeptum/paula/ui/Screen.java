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

import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.ui.visual.Bars;
import com.adeptum.paula.ui.visual.Braille;
import com.adeptum.paula.ui.visual.Palette;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Builds the player screen: a title bar, a details panel beside the spectrum analyser, the channel scopes and the
 * meters, and a key bar. It has no terminal dependency so it can be unit tested.
 */
public final class Screen {

    private static final String APPLICATION = "Paula";
    private static final String SECTION = "player";
    private static final String IDLE = "Nothing playing, press b to browse the party archives";
    private static final int WIDE_LAYOUT_WIDTH = 100;
    private static final int DETAILS_WIDTH = 50;
    private static final int METER_ROWS = 1;
    private static final int MIN_SPECTRUM_ROWS = 3;
    private static final int VU_WIDTH = 10;
    private static final int MIN_SCOPE_WIDTH = 12;
    private static final int SCOPE_LABEL_WIDTH = 3;
    private static final char PEAK_MARK = '─';
    private static final List<Frame.Key> KEYS = List.of(
            new Frame.Key("space", "pause"), new Frame.Key("←/→", "seek"), new Frame.Key("n", "next"),
            new Frame.Key("p", "previous"), new Frame.Key("b", "browse"), new Frame.Key("q", "quit"));

    private Screen() {
    }

    public static List<AttributedString> render(PlayerView view, int width, int height) {
        final int body = Math.max(0, height - 2);
        final List<AttributedString> lines = new ArrayList<>(height);
        lines.add(Frame.titleBar(APPLICATION, SECTION, width));
        lines.addAll(view.module() == null ? idle(view, width, body) : playing(view, width, body));
        lines.add(Frame.footer(KEYS, width));
        return fit(lines, width, height);
    }

    static String clock(Duration position) {
        return String.format("%02d:%02d", position.toMinutes(), position.toSecondsPart());
    }

    static AttributedString line(Consumer<AttributedStringBuilder> content) {
        final AttributedStringBuilder builder = new AttributedStringBuilder();
        content.accept(builder);
        return builder.toAttributedString();
    }

    static List<AttributedString> fit(List<AttributedString> lines, int width, int height) {
        final List<AttributedString> fitted = new ArrayList<>(height);
        for (int row = 0; row < height; row++) {
            fitted.add(Frame.pad(row < lines.size() ? lines.get(row) : AttributedString.EMPTY, width, AttributedStyle.DEFAULT));
        }
        if (!lines.isEmpty() && height > 1) {
            fitted.set(height - 1, Frame.pad(lines.get(lines.size() - 1), width, AttributedStyle.DEFAULT));
        }
        return fitted;
    }

    private static List<AttributedString> idle(PlayerView view, int width, int height) {
        final List<AttributedString> lines = new ArrayList<>();
        for (int row = 0; row < height / 2 - 1; row++) {
            lines.add(AttributedString.EMPTY);
        }
        lines.add(Frame.centered(IDLE, width, Palette.VALUE));
        if (view.status() != null) {
            lines.add(Frame.centered(view.status(), width, Palette.ACCENT));
        }
        return lines;
    }

    private static List<AttributedString> playing(PlayerView view, int width, int height) {
        if (width >= WIDE_LAYOUT_WIDTH) {
            return Frame.sideBySide(details(view, DETAILS_WIDTH, height), visuals(view, width - DETAILS_WIDTH, height), DETAILS_WIDTH, width);
        }
        final int detailRows = Math.min(height, Math.max(3, Math.min(detailLines(view, width - 2).size() + 2, height / 2)));
        final List<AttributedString> lines = new ArrayList<>(details(view, width, detailRows));
        lines.addAll(visuals(view, width, height - detailRows));
        return lines;
    }

    private static List<AttributedString> details(PlayerView view, int width, int height) {
        return Frame.box("Now playing", detailLines(view, width - 2), width, height);
    }

    private static List<AttributedString> detailLines(PlayerView view, int width) {
        final ModuleMetadata meta = view.module().metadata();
        final Set<Integer> active = view.channels().stream().filter(c -> c.volume() > 0).map(ChannelState::instrument).collect(Collectors.toSet());
        final List<AttributedString> lines = new ArrayList<>();
        lines.add(field("Title  ", meta.displayTitle()));
        lines.add(field("File   ", view.module().source().getFileName().toString()));
        lines.add(field("Format ", meta.format().name() + ", " + meta.channels() + " channels, " + meta.displayLength()));
        lines.add(field("Track  ", view.track() + " / " + view.trackCount()));
        if (view.trackLabel() != null) {
            lines.add(line(b -> b.style(Palette.LABEL).append("       ").style(Palette.VALUE).append(view.trackLabel())));
        }
        lines.add(line(b -> b.style(Palette.LABEL).append("Status ").style(Palette.ACTIVE).append(view.state().name())));
        if (view.status() != null) {
            lines.add(line(b -> b.style(Palette.ACCENT).append(view.status())));
        }
        lines.add(AttributedString.EMPTY);
        meta.credits().forEach(credit -> lines.add(line(b -> b.style(Palette.VALUE).append(credit))));
        for (int i = 0; i < meta.instruments().size(); i++) {
            final int number = i + 1;
            final AttributedStyle style = active.contains(number) ? Palette.ACTIVE : Palette.VALUE;
            lines.add(line(b -> b.style(Palette.LABEL).append(String.format("%02d ", number)).style(style).append(meta.instruments().get(number - 1))));
        }
        return lines;
    }

    private static List<AttributedString> visuals(PlayerView view, int width, int height) {
        final List<AttributedString> lines = new ArrayList<>();
        if (height <= 0) {
            return lines;
        }
        final int panels = Math.max(0, height - METER_ROWS);
        final int spectrumRows = Math.min(panels, Math.max(Math.min(MIN_SPECTRUM_ROWS, panels), panels * 2 / 5));
        final int scopeRows = panels - spectrumRows;
        if (spectrumRows > 0) {
            lines.addAll(Frame.box("Spectrum", spectrum(view, width - 2, spectrumRows - 2), width, spectrumRows));
        }
        if (scopeRows > 0) {
            lines.addAll(Frame.box("Channels", scopes(view, width - 2, scopeRows - 2), width, scopeRows));
        }
        lines.add(meters(view, width));
        return lines;
    }

    private static List<AttributedString> spectrum(PlayerView view, int width, int height) {
        final double[] levels = view.spectrum();
        final double[] peaks = view.peaks();
        final List<AttributedString> rows = new ArrayList<>();
        if (levels.length == 0 || width <= 0 || height <= 0) {
            return rows;
        }
        final int slot = Math.max(1, width / levels.length);
        final int barWidth = slot > 1 ? slot - 1 : 1;
        for (int row = 0; row < height; row++) {
            final AttributedStringBuilder line = new AttributedStringBuilder();
            final double rowHeight = height == 1 ? 1 : (double) (height - 1 - row) / (height - 1);
            for (int band = 0; band < levels.length && line.columnLength() + slot <= width; band++) {
                final char glyph = Bars.column(levels[band], height)[row];
                final int peakRow = height - 1 - (int) Math.min(height - 1, Math.floor(peaks[band] * height));
                final boolean showPeak = glyph == ' ' && peaks[band] > levels[band] && row == peakRow;
                line.style(showPeak ? Palette.PEAK : Palette.level(rowHeight));
                line.append(String.valueOf(showPeak ? PEAK_MARK : glyph).repeat(barWidth));
                line.append(" ".repeat(slot - barWidth));
            }
            rows.add(line.toAttributedString());
        }
        return rows;
    }

    private static List<AttributedString> scopes(PlayerView view, int width, int height) {
        final List<ChannelState> channels = view.channels().isEmpty()
                ? List.of(new ChannelState(0, 0, 1, view.mixed()))
                : view.channels();
        final int columns = Math.clamp((int) Math.ceil(Math.sqrt(channels.size())), 1, Math.max(1, width / MIN_SCOPE_WIDTH));
        final int rows = (int) Math.ceil((double) channels.size() / columns);
        final int cellHeight = Math.max(1, height / rows);
        final int cellWidth = Math.max(SCOPE_LABEL_WIDTH + 1, width / columns);
        final List<AttributedString> lines = new ArrayList<>();
        for (int row = 0; row < rows && lines.size() < height; row++) {
            final List<List<String>> plots = new ArrayList<>();
            for (int column = 0; column < columns; column++) {
                final int index = row * columns + column;
                plots.add(index < channels.size() ? Braille.plot(channels.get(index).waveform(), cellWidth - SCOPE_LABEL_WIDTH, cellHeight) : List.of());
            }
            for (int y = 0; y < cellHeight && lines.size() < height; y++) {
                final AttributedStringBuilder line = new AttributedStringBuilder();
                for (int column = 0; column < columns; column++) {
                    final int index = row * columns + column;
                    if (index >= channels.size()) {
                        break;
                    }
                    final ChannelState channel = channels.get(index);
                    final boolean sounding = channel.volume() > 0;
                    final String label = y == 0 ? String.format("%2s ", channel.number() == 0 ? "mix" : channel.number()) : " ".repeat(SCOPE_LABEL_WIDTH);
                    line.style(sounding ? Palette.ACTIVE : Palette.DIMMED).append(label.substring(0, SCOPE_LABEL_WIDTH));
                    line.style(sounding ? Palette.SCOPE : Palette.SCOPE_QUIET).append(plots.get(column).get(y));
                }
                lines.add(line.toAttributedString());
            }
        }
        return lines;
    }

    private static AttributedString meters(PlayerView view, int width) {
        final Duration length = view.length() == null ? Duration.ZERO : view.length();
        final double progress = length.isZero() ? 0 : (double) view.position().toMillis() / length.toMillis();
        final String vu = "  L " + Bars.row(view.vuLeft(), VU_WIDTH) + " R " + Bars.row(view.vuRight(), VU_WIDTH);
        final int barWidth = Math.max(1, width - 2 * (clock(Duration.ZERO).length() + 1) - vu.length() - 1);
        return line(b -> b.style(Palette.VALUE).append(' ').append(clock(view.position())).append(' ')
                .style(Palette.ACCENT).append(Bars.row(progress, barWidth))
                .style(Palette.VALUE).append(' ').append(clock(length))
                .style(Palette.LABEL).append("  L ").style(Palette.level(view.vuLeft())).append(Bars.row(view.vuLeft(), VU_WIDTH))
                .style(Palette.LABEL).append(" R ").style(Palette.level(view.vuRight())).append(Bars.row(view.vuRight(), VU_WIDTH)));
    }

    private static AttributedString field(String label, String value) {
        return line(b -> b.style(Palette.LABEL).append(label).style(Palette.VALUE).append(value));
    }
}

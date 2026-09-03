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

import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.ui.visual.Bars;
import com.adeptum.paula.ui.visual.Braille;
import com.adeptum.paula.ui.visual.Palette;
import com.adeptum.paula.ui.visual.Vectorscope;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
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

    private static final String APPLICATION = "Paula Escobar";
    private static final String SECTION = "player";
    private static final String IDLE = "Nothing playing, press b to browse the party archives";
    private static final String DETAILS_TITLE = "Now playing";
    private static final int MIN_BOX_ROWS = 3;
    private static final int CLOCK_WIDTH = 5;
    private static final int WIDE_LAYOUT_WIDTH = 100;
    private static final int DETAILS_WIDTH = 50;
    private static final int METER_ROWS = 1;
    private static final int MIN_SPECTRUM_ROWS = 3;
    private static final int VU_WIDTH = 10;
    private static final char PEAK_MARK = '─';
    private static final char[] SHADES = {' ', '░', '▒', '▓', '█'};
    private static final List<Frame.Key> KEYS = List.of(
            new Frame.Key("space", "pause"), new Frame.Key("←/→", "seek"), new Frame.Key("n", "next"),
            new Frame.Key("p", "previous"), new Frame.Key("b", "browse"), new Frame.Key("?", "keys"),
            new Frame.Key("q", "quit"));
    private static final List<Frame.Key> ALL_KEYS = List.of(
            new Frame.Key("space", "pause or resume"),
            new Frame.Key("← →", "seek five seconds"),
            new Frame.Key("n", "next track"),
            new Frame.Key("p", "previous track"),
            new Frame.Key("b", "switch to the browser"),
            new Frame.Key("v", "next visualiser: spectrum, waterfall, vectorscope"),
            new Frame.Key("?", "close these keys"),
            new Frame.Key("click", "next visualiser, or mute the channel clicked"),
            new Frame.Key("shift/double click", "solo a channel"),
            new Frame.Key("q", "quit"));

    private Screen() {
    }

    /**
     * The rows the spectrum and the scopes get of what the meters leave, each zero when the panel has too few
     * to be worth drawing; too little for both leaves the spectrum alone with them.
     */
    private record Panels(int spectrum, int scopes) {

        static Panels of(int height) {
            final int available = Math.max(0, height - METER_ROWS);
            final int scopes = available < MIN_BOX_ROWS * 2 ? 0 : available - Math.max(MIN_SPECTRUM_ROWS, available * 2 / 5);
            return new Panels(available - scopes >= MIN_BOX_ROWS ? available - scopes : 0, scopes);
        }

        boolean hasSpectrum() {
            return spectrum > 0;
        }

        boolean hasScopes() {
            return scopes > 0;
        }
    }

    public static List<Frame.Key> keys() {
        return ALL_KEYS;
    }

    public static List<AttributedString> render(PlayerView view, int width, int height) {
        final int body = Math.max(0, height - 2);
        final List<AttributedString> lines = new ArrayList<>(height);
        lines.add(Frame.titleBar(APPLICATION, SECTION, width));
        lines.addAll(view.module() == null ? idle(view, width, body) : playing(view, scopes(view, width, height), width, body));
        lines.add(Frame.footer(KEYS, width));
        return fit(lines, width, height);
    }

    /**
     * The channel drawn at a screen cell, numbered from one; a format that mixes no distinct channels shows one
     * scope of the mix, which is nobody's to silence.
     */
    public static OptionalInt channelAt(PlayerView view, int width, int height, int column, int row) {
        return view.channels().isEmpty() ? OptionalInt.empty() : scopes(view, width, height).channelAt(column, row);
    }

    /**
     * True where the upper panel is drawn, which a click turns over to the next visualiser.
     */
    public static boolean onVisual(PlayerView view, int width, int height, int column, int row) {
        if (view.module() == null) {
            return false;
        }
        final int body = Math.max(0, height - 2);
        final boolean wide = width >= WIDE_LAYOUT_WIDTH;
        final int detailRows = wide ? 0 : detailRows(detailLines(view), body);
        final Panels panels = Panels.of(body - detailRows);
        final int left = wide ? DETAILS_WIDTH : 0;
        final int top = 1 + detailRows;
        return panels.hasSpectrum() && column >= left && row >= top && row < top + panels.spectrum();
    }

    /**
     * Where the scopes land on the screen, which is what the drawing lays them out on and what a click is
     * measured against. A format without channels of its own still gets a grid, for its one scope of the mix.
     */
    static Scopes scopes(PlayerView view, int width, int height) {
        if (view.module() == null) {
            return Scopes.NONE;
        }
        final int body = Math.max(0, height - 2);
        final boolean wide = width >= WIDE_LAYOUT_WIDTH;
        final int detailRows = wide ? 0 : detailRows(detailLines(view), body);
        final Panels panels = Panels.of(body - detailRows);
        if (!panels.hasScopes()) {
            return Scopes.NONE;
        }
        final int left = wide ? DETAILS_WIDTH : 0;
        return Scopes.grid(1 + detailRows + panels.spectrum() + 1, left + 1,
                width - left - 2, panels.scopes() - 2, Math.max(1, view.channels().size()));
    }

    static String clock(Duration position) {
        return String.format("%02d:%02d", position.toMinutes(), position.toSecondsPart());
    }

    static AttributedString line(Consumer<AttributedStringBuilder> content) {
        final AttributedStringBuilder builder = new AttributedStringBuilder();
        content.accept(builder);
        return builder.toAttributedString();
    }

    /**
     * Pads to the exact size; when there are more lines than rows the last line, the key bar, still wins the bottom row.
     */
    static List<AttributedString> fit(List<AttributedString> lines, int width, int height) {
        final List<AttributedString> fitted = new ArrayList<>(height);
        for (int row = 0; row < height; row++) {
            fitted.add(Frame.pad(row < lines.size() ? lines.get(row) : AttributedString.EMPTY, width, AttributedStyle.DEFAULT));
        }
        if (lines.size() > height && height > 0) {
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
        while (lines.size() < height) {
            lines.add(AttributedString.EMPTY);
        }
        return lines.subList(0, height);
    }

    private static List<AttributedString> playing(PlayerView view, Scopes grid, int width, int height) {
        final List<AttributedString> detailLines = detailLines(view);
        if (width >= WIDE_LAYOUT_WIDTH) {
            return Frame.sideBySide(Frame.box(DETAILS_TITLE, detailLines, DETAILS_WIDTH, height), visuals(view, grid, width - DETAILS_WIDTH, height), DETAILS_WIDTH, width);
        }
        final int detailRows = detailRows(detailLines, height);
        final List<AttributedString> lines = new ArrayList<>(Frame.box(DETAILS_TITLE, detailLines, width, detailRows));
        lines.addAll(visuals(view, grid, width, height - detailRows));
        return lines;
    }

    private static int detailRows(List<AttributedString> detailLines, int height) {
        return Math.min(height, Math.max(MIN_BOX_ROWS, Math.min(detailLines.size() + 2, height / 2)));
    }

    private static List<AttributedString> detailLines(PlayerView view) {
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

    private static List<AttributedString> visuals(PlayerView view, Scopes grid, int width, int height) {
        final List<AttributedString> lines = new ArrayList<>();
        if (height <= 0) {
            return lines;
        }
        final Panels panels = Panels.of(height);
        if (panels.hasSpectrum()) {
            lines.addAll(Frame.box(view.visual().title(), chosen(view, width - 2, panels.spectrum() - 2), width, panels.spectrum()));
        }
        if (panels.hasScopes()) {
            lines.addAll(Frame.box("Channels", scopeCells(view, grid, panels.scopes() - 2), width, panels.scopes()));
        }
        lines.add(meters(view, width));
        return lines;
    }

    private static List<AttributedString> chosen(PlayerView view, int width, int height) {
        return switch (view.visual()) {
            case WATERFALL -> waterfall(view, width, height);
            case VECTORSCOPE -> vectorscope(view, width, height);
            case SPECTRUM -> spectrum(view, width, height);
        };
    }

    /**
     * The spectrum as it was, newest at the top, one cell per band and row, lit by how loud that band was.
     */
    private static List<AttributedString> waterfall(PlayerView view, int width, int height) {
        final List<AttributedString> rows = new ArrayList<>();
        if (view.waterfall() == null || view.spectrum().length == 0 || width <= 0) {
            return rows;
        }
        final int bands = view.spectrum().length;
        for (int age = 0; age < height; age++) {
            final double[] levels = view.waterfall().row(age);
            final AttributedStringBuilder line = new AttributedStringBuilder();
            for (int column = 0; column < width; column++) {
                final double level = levels[Math.min(bands - 1, column * bands / width)];
                line.style(Palette.level(level)).append(shade(level));
            }
            rows.add(line.toAttributedString());
        }
        return rows;
    }

    private static char shade(double level) {
        return SHADES[Math.clamp((int) (level * SHADES.length), 0, SHADES.length - 1)];
    }

    private static List<AttributedString> vectorscope(PlayerView view, int width, int height) {
        return Vectorscope.plot(view.stereo(), width, height).stream()
                .map(row -> line(b -> b.style(Palette.SCOPE).append(row)))
                .toList();
    }

    /**
     * Bands share the width evenly, each keeping a one-column gap when there is room, so the bars reach the edge.
     */
    private static List<AttributedString> spectrum(PlayerView view, int width, int height) {
        final double[] levels = view.spectrum();
        final double[] peaks = view.peaks();
        final List<AttributedString> rows = new ArrayList<>();
        if (levels.length == 0 || width <= 0 || height <= 0) {
            return rows;
        }
        final char[][] columns = new char[levels.length][];
        final int[] peakRows = new int[levels.length];
        for (int band = 0; band < levels.length; band++) {
            columns[band] = Bars.column(levels[band], height);
            peakRows[band] = height - 1 - (int) Math.min(height - 1, Math.floor(peaks[band] * height));
        }
        final boolean gaps = width >= levels.length * 2;
        for (int row = 0; row < height; row++) {
            final AttributedStringBuilder line = new AttributedStringBuilder();
            final double rowHeight = height == 1 ? 1 : (double) (height - 1 - row) / (height - 1);
            for (int band = 0; band < levels.length; band++) {
                final int start = band * width / levels.length;
                final int end = (band + 1) * width / levels.length;
                final int barWidth = Math.max(0, end - start - (gaps ? 1 : 0));
                final boolean showPeak = columns[band][row] == ' ' && peaks[band] > levels[band] && row == peakRows[band];
                line.style(showPeak ? Palette.PEAK : Palette.level(rowHeight));
                line.append(String.valueOf(showPeak ? PEAK_MARK : columns[band][row]).repeat(barWidth));
                line.append(" ".repeat(end - start - barWidth));
            }
            rows.add(line.toAttributedString());
        }
        return rows;
    }

    private static List<AttributedString> scopeCells(PlayerView view, Scopes grid, int height) {
        final List<ChannelState> channels = view.channels().isEmpty()
                ? List.of(new ChannelState(0, 0, 1, view.mixed(), false))
                : view.channels();
        final int plotWidth = grid.cellWidth() - Scopes.LABEL_WIDTH;
        final List<AttributedString> lines = new ArrayList<>();
        for (int row = 0; row < grid.rows() && lines.size() < height; row++) {
            final List<List<String>> plots = new ArrayList<>();
            for (int column = 0; column < grid.columns(); column++) {
                final int index = row * grid.columns() + column;
                plots.add(index < channels.size() ? Braille.plot(channels.get(index).waveform(), plotWidth, grid.cellHeight()) : List.of());
            }
            for (int y = 0; y < grid.cellHeight() && lines.size() < height; y++) {
                final AttributedStringBuilder line = new AttributedStringBuilder();
                for (int column = 0; column < grid.columns(); column++) {
                    final int index = row * grid.columns() + column;
                    if (index >= channels.size()) {
                        break;
                    }
                    line.append(cell(channels.get(index), plots.get(column).get(y), y == 0));
                }
                lines.add(line.toAttributedString());
            }
        }
        return lines;
    }

    /**
     * One scope: its channel number, written on the first row only, and the waveform beside it. A silenced
     * channel is struck through so it reads as off rather than merely quiet.
     */
    private static AttributedString cell(ChannelState channel, String plot, boolean labelled) {
        final boolean sounding = channel.volume() > 0;
        final String label = labelled
                ? String.format("%-" + Scopes.LABEL_WIDTH + "s", channel.number() == 0 ? "mix" : channel.number())
                : " ".repeat(Scopes.LABEL_WIDTH);
        final AttributedStyle labelStyle = channel.muted() ? Palette.MUTED : sounding ? Palette.ACTIVE : Palette.DIMMED;
        return line(b -> b.style(labelStyle).append(label, 0, Scopes.LABEL_WIDTH)
                .style(sounding ? Palette.SCOPE : Palette.SCOPE_QUIET).append(plot));
    }

    private static AttributedString meters(PlayerView view, int width) {
        final Duration length = view.length() == null ? Duration.ZERO : view.length();
        final double progress = length.isZero() ? 0 : (double) view.position().toMillis() / length.toMillis();
        final int metersWidth = "  L ".length() + VU_WIDTH + " R ".length() + VU_WIDTH;
        final int barWidth = Math.max(1, width - 2 * (CLOCK_WIDTH + 1) - metersWidth - 1);
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

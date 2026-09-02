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

import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.CuratedSeries;
import com.adeptum.paula.demozoo.DemozooClient;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.TrackResolver;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.Playlist;
import com.adeptum.paula.playlist.Track;
import com.adeptum.paula.ui.visual.Bars;
import com.adeptum.paula.ui.visual.Palette;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Walks party series, parties, music competitions and ranked entries as a stack of lists. Demozoo is read on the
 * executor and the answer is applied on the next tick, so key handling never waits for the network.
 */
@Slf4j
public final class Browser {

    private sealed interface Item permits SeriesItem, PartyItem, CompoItem, EntryItem {

        String label();

        default boolean dimmed() {
            return false;
        }
    }

    private record SeriesItem(CuratedSeries series) implements Item {

        @Override
        public String label() {
            return series.name();
        }
    }

    private record PartyItem(Party party) implements Item {

        @Override
        public String label() {
            return party.name();
        }
    }

    private record CompoItem(Party party, Competition compo) implements Item {

        @Override
        public String label() {
            return compo.name() + " (" + compo.entries().size() + ")";
        }

        String compoLabel() {
            return party.name() + COMPO_SEPARATOR + compo.name();
        }
    }

    private record EntryItem(CompoItem compo, CompoEntry entry, int index, Map<Integer, Boolean> downloads) implements Item {

        @Override
        public String label() {
            return placingText() + titleText();
        }

        String placingText() {
            return String.format("%" + PLACING_WIDTH + "s", entry.placing());
        }

        String titleText() {
            final String text = "  " + entry.title() + "  " + entry.author();
            return hasNoDownload() ? text + NO_DOWNLOAD : text;
        }

        @Override
        public boolean dimmed() {
            return !playable();
        }

        boolean playable() {
            return entry.likelyPlayable() && !hasNoDownload();
        }

        private boolean hasNoDownload() {
            return Boolean.FALSE.equals(downloads.get(entry.productionId()));
        }
    }

    private interface Loader {
        List<Item> load() throws IOException;
    }

    private static final class Level {

        private final String title;
        private final String emptyText;
        private final List<Item> items;
        private int cursor;
        private int offset;

        private Level(String title, String emptyText, List<Item> items) {
            this.title = title;
            this.emptyText = emptyText;
            this.items = items;
        }

        private Optional<Item> selected() {
            return items.isEmpty() ? Optional.empty() : Optional.of(items.get(cursor));
        }

        private void move(int delta) {
            cursor = Math.clamp(cursor + delta, 0, Math.max(0, items.size() - 1));
        }

        private void scrollTo(int rows) {
            offset = Math.clamp(offset, Math.max(0, cursor - rows + 1), cursor);
        }
    }

    private static final String ROOT_TITLE = "Parties";
    private static final String NOTHING_HERE = "Nothing here";
    private static final String NO_MUSIC = "No music competitions";
    private static final String LOADING = "Loading ";
    private static final String COMPO_SEPARATOR = " · ";
    private static final String CRUMB_SEPARATOR = " › ";
    private static final String CURSOR = "> ";
    private static final String NO_CURSOR = "  ";
    private static final String NO_DOWNLOAD = "  (no download)";
    private static final String APPLICATION = "Paula";
    private static final String SECTION = "browse";
    private static final String NOW_PLAYING_MARK = "♪ ";
    private static final int STRIP_BANDS = 16;
    private static final int PLACING_WIDTH = 3;
    private static final int CHROME_LINES = 6;
    private static final List<Frame.Key> KEYS = List.of(
            new Frame.Key("↑/↓", "move"), new Frame.Key("enter", "open"), new Frame.Key("backspace", "back"),
            new Frame.Key("b", "player"), new Frame.Key("q", "quit"));

    private final DemozooClient demozoo;
    private final Executor executor;
    private final Deque<Level> levels = new ArrayDeque<>();
    private final Map<Integer, Boolean> downloads = new ConcurrentHashMap<>();
    private CompletableFuture<Level> pending;
    private String error;
    private Playlist selection;
    private int pageSize = 1;
    private String nowPlayingLabel;
    private double[] nowPlayingSpectrum = new double[0];

    public Browser(DemozooClient demozoo, Executor executor) {
        this.demozoo = demozoo;
        this.executor = executor;
        levels.push(new Level(ROOT_TITLE, NOTHING_HERE, CuratedSeries.ALL.stream().<Item>map(SeriesItem::new).toList()));
    }

    public boolean atRoot() {
        return levels.size() == 1;
    }

    /**
     * Escape backs out of a level but is left to the player at the root, where it means quit.
     */
    public boolean consumes(Key key) {
        return switch (key.special()) {
            case UP, DOWN, PAGE_UP, PAGE_DOWN, HOME, END, ENTER, BACKSPACE, LEFT, RIGHT -> true;
            case ESCAPE -> !atRoot();
            default -> false;
        };
    }

    public void handle(Key key) {
        final Level level = levels.peek();
        switch (key.special()) {
            case UP -> level.move(-1);
            case DOWN -> level.move(1);
            case PAGE_UP -> level.move(-pageSize);
            case PAGE_DOWN -> level.move(pageSize);
            case HOME -> level.move(-level.items.size());
            case END -> level.move(level.items.size());
            case ENTER, RIGHT -> open(level);
            case BACKSPACE, LEFT, ESCAPE -> back();
            default -> {
            }
        }
    }

    public void tick() {
        if (pending == null || !pending.isDone()) {
            return;
        }
        try {
            levels.push(pending.join());
        } catch (CompletionException | CancellationException e) {
            final Throwable cause = e.getCause() == null ? e : e.getCause();
            error = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        }
        pending = null;
    }

    /**
     * Shows a message from the player, for example why a chosen entry could not be played.
     */
    public void report(String message) {
        error = message;
    }

    public Optional<Playlist> takeSelection() {
        final Optional<Playlist> taken = Optional.ofNullable(selection);
        selection = null;
        return taken;
    }

    /**
     * Tells the browser what the player is doing so it can show it under the list while music keeps playing.
     */
    public void nowPlaying(String label, double[] spectrum) {
        nowPlayingLabel = label;
        nowPlayingSpectrum = spectrum;
    }

    public List<AttributedString> render(int width, int height) {
        final Level level = levels.peek();
        pageSize = Math.max(1, height - CHROME_LINES);
        level.scrollTo(pageSize);
        final List<AttributedString> rows = new ArrayList<>();
        if (level.items.isEmpty()) {
            rows.add(Screen.line(b -> b.style(Palette.LABEL).append(level.emptyText)));
        }
        for (int i = level.offset; i < Math.min(level.items.size(), level.offset + pageSize); i++) {
            rows.add(row(level.items.get(i), i == level.cursor, width - 2));
        }
        final List<AttributedString> lines = new ArrayList<>();
        lines.add(Frame.titleBar(APPLICATION, SECTION, width));
        lines.addAll(Frame.box(breadcrumb(), rows, width, pageSize + 2));
        lines.add(statusLine());
        lines.add(nowPlayingLine(width));
        lines.add(Frame.footer(KEYS, width));
        return Screen.fit(lines, width, height);
    }

    private void open(Level level) {
        if (pending != null) {
            return;
        }
        error = null;
        level.selected().ifPresent(item -> {
            switch (item) {
                case SeriesItem series -> load(series.label(), NOTHING_HERE, () -> partyItems(series.series().id()));
                case PartyItem party -> load(party.label(), NO_MUSIC, () -> compoItems(party.party()));
                case CompoItem compo -> openCompo(compo);
                case EntryItem entry -> selection = playlistFrom(level, entry);
            }
        });
    }

    /**
     * A fetch still in flight belongs to the level being left, so its answer is dropped when it arrives.
     */
    private void back() {
        if (!atRoot()) {
            levels.pop();
        }
        pending = null;
        error = null;
    }

    private void load(String title, String emptyText, Loader loader) {
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                return new Level(title, emptyText, loader.load());
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private List<Item> partyItems(int seriesId) throws IOException {
        return demozoo.series(seriesId).parties().stream().<Item>map(PartyItem::new).toList();
    }

    private List<Item> compoItems(Party party) throws IOException {
        return demozoo.competitions(party.id()).stream().<Item>map(compo -> new CompoItem(party, compo)).toList();
    }

    /**
     * The entry list shows at once; whether each production actually has a download is looked up behind it, so
     * entries Demozoo knows no file for get marked before anyone tries to play them.
     */
    private void openCompo(CompoItem compo) {
        final List<CompoEntry> entries = compo.compo().entries();
        final List<Item> items = IntStream.range(0, entries.size()).<Item>mapToObj(i -> new EntryItem(compo, entries.get(i), i, downloads)).toList();
        levels.push(new Level(compo.compoLabel(), NOTHING_HERE, items));
        CompletableFuture.runAsync(() -> lookUpDownloads(entries), executor);
    }

    private void lookUpDownloads(List<CompoEntry> entries) {
        for (final CompoEntry entry : entries) {
            if (!downloads.containsKey(entry.productionId())) {
                try {
                    downloads.put(entry.productionId(), TrackResolver.preferredLink(demozoo.production(entry.productionId())).isPresent());
                } catch (IOException e) {
                    log.debug("Could not look up downloads for {}: {}", entry.title(), e.getMessage());
                }
            }
        }
    }

    /**
     * The chosen entry plays even when it looks unplayable, since a streaming compo can still hold a module; the
     * rest of the competition follows in ranked order.
     */
    private static Playlist playlistFrom(Level level, EntryItem chosen) {
        final List<Track> tracks = level.items.subList(chosen.index(), level.items.size()).stream()
                .map(EntryItem.class::cast)
                .filter(item -> item == chosen || item.playable())
                .<Track>map(item -> new DemozooTrack(item.entry(), item.compo().compoLabel()))
                .toList();
        return new Playlist(tracks);
    }

    private String breadcrumb() {
        final List<String> titles = new ArrayList<>(levels.stream().map(level -> level.title).toList());
        Collections.reverse(titles);
        return String.join(CRUMB_SEPARATOR, titles);
    }

    /**
     * The cursor row is painted edge to edge in one style; other rows colour the top three placings like medals.
     */
    private static AttributedString row(Item item, boolean selected, int width) {
        if (selected) {
            return Frame.pad(new AttributedStringBuilder().style(Palette.SELECTED).append(CURSOR).append(item.label()).toAttributedString(), width, Palette.SELECTED);
        }
        final AttributedStringBuilder line = new AttributedStringBuilder().style(Palette.ACCENT).append(NO_CURSOR);
        final AttributedStyle text = item.dimmed() ? Palette.DIMMED : Palette.VALUE;
        if (item instanceof EntryItem entry) {
            line.style(medal(entry.entry().placing(), text)).append(entry.placingText());
            line.style(text).append(entry.titleText());
        } else {
            line.style(text).append(item.label());
        }
        return line.toAttributedString();
    }

    private static AttributedStyle medal(String placing, AttributedStyle fallback) {
        return switch (placing) {
            case "1" -> Palette.GOLD;
            case "2" -> Palette.SILVER;
            case "3" -> Palette.BRONZE;
            default -> fallback;
        };
    }

    private AttributedString statusLine() {
        if (pending != null) {
            return Screen.line(b -> b.style(Palette.ACCENT).append(LOADING).append(loadingTitle()).append('…'));
        }
        if (error != null) {
            return Screen.line(b -> b.style(Palette.ACCENT).append(error));
        }
        return AttributedString.EMPTY;
    }

    private AttributedString nowPlayingLine(int width) {
        if (nowPlayingLabel == null) {
            return AttributedString.EMPTY;
        }
        final AttributedStringBuilder line = new AttributedStringBuilder()
                .style(Palette.ACCENT).append(NOW_PLAYING_MARK).style(Palette.VALUE).append(nowPlayingLabel).append("  ");
        final int bands = Math.min(STRIP_BANDS, nowPlayingSpectrum.length);
        for (int band = 0; band < bands; band++) {
            double level = 0;
            for (int i = band * nowPlayingSpectrum.length / bands; i < (band + 1) * nowPlayingSpectrum.length / bands; i++) {
                level = Math.max(level, nowPlayingSpectrum[i]);
            }
            line.style(Palette.level(level)).append(Bars.column(level, 1)[0]);
        }
        return line.toAttributedString().columnSubSequence(0, Math.min(width, line.columnLength()));
    }

    private String loadingTitle() {
        return levels.peek().selected().map(Item::label).orElse("");
    }

}

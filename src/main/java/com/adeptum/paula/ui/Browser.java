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
            final String label = String.format("%3s  %s  %s", entry.placing(), entry.title(), entry.author());
            return hasNoDownload() ? label + NO_DOWNLOAD : label;
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
    private static final int CHROME_LINES = 4;

    private final DemozooClient demozoo;
    private final Executor executor;
    private final Deque<Level> levels = new ArrayDeque<>();
    private final Map<Integer, Boolean> downloads = new ConcurrentHashMap<>();
    private CompletableFuture<Level> pending;
    private String error;
    private Playlist selection;
    private int pageSize = 1;

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

    public List<AttributedString> render(int width, int height) {
        final Level level = levels.peek();
        pageSize = Math.max(1, height - CHROME_LINES);
        level.scrollTo(pageSize);
        final List<AttributedString> lines = new ArrayList<>();
        lines.add(Screen.line(b -> b.style(Theme.TITLE).append("Paula").style(Theme.LABEL).append("  browse  ").style(Theme.VALUE).append(breadcrumb())));
        lines.add(AttributedString.EMPTY);
        if (level.items.isEmpty()) {
            lines.add(Screen.line(b -> b.style(Theme.LABEL).append(level.emptyText)));
        }
        for (int i = level.offset; i < Math.min(level.items.size(), level.offset + pageSize); i++) {
            lines.add(row(level.items.get(i), i == level.cursor));
        }
        lines.add(statusLine());
        return Screen.fit(lines, keyBar(), width, height);
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

    private static AttributedString row(Item item, boolean selected) {
        return Screen.line(b -> b.style(Theme.ACCENT).append(selected ? CURSOR : NO_CURSOR)
                .style(item.dimmed() ? Theme.DIMMED : Theme.VALUE).append(item.label()));
    }

    private AttributedString statusLine() {
        if (pending != null) {
            return Screen.line(b -> b.style(Theme.ACCENT).append(LOADING).append(loadingTitle()).append('…'));
        }
        if (error != null) {
            return Screen.line(b -> b.style(Theme.ACCENT).append(error));
        }
        return AttributedString.EMPTY;
    }

    private String loadingTitle() {
        return levels.peek().selected().map(Item::label).orElse("");
    }

    private static AttributedString keyBar() {
        return Screen.line(b -> {
            Screen.key(b, "↑/↓", "move");
            Screen.key(b, "enter", "open");
            Screen.key(b, "backspace", "back");
            Screen.key(b, "b", "player");
            Screen.key(b, "q", "quit");
        });
    }
}

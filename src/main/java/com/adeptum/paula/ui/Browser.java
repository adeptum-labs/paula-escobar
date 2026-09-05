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

import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.archive.Archives;
import com.adeptum.paula.demozoo.CuratedSeries;
import com.adeptum.paula.demozoo.DemozooClient;
import com.adeptum.paula.demozoo.Link;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.PartyArt;
import com.adeptum.paula.demozoo.ReleaseArt;
import com.adeptum.paula.demozoo.TrackResolver;
import com.adeptum.paula.modarchive.Chart;
import com.adeptum.paula.modarchive.ChartEntry;
import com.adeptum.paula.modarchive.ChartPage;
import com.adeptum.paula.modarchive.ModArchiveClient;
import com.adeptum.paula.modarchive.Slice;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.ModArchiveTrack;
import com.adeptum.paula.playlist.Playlist;
import com.adeptum.paula.playlist.Track;
import com.adeptum.paula.ui.visual.Bars;
import com.adeptum.paula.ui.visual.Palette;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Walks party series, parties, music competitions and ranked entries, and the ModArchive charts down to the
 * tunes they rank, as a stack of lists. Demozoo and modarchive.org are read on the executor and the answer is
 * applied on the next tick, so key handling never waits for the network.
 */
@Slf4j
public final class Browser {

    private sealed interface Item
            permits SeriesItem, PartyItem, CompoItem, EntryItem, ChartsItem, ChartItem, FormatItem, TuneItem {

        String label();

        /**
         * What goes in the second column, where the kind of list has one.
         */
        default String detail() {
            return "";
        }

        /**
         * What is set against the right edge of the row, or of the cell when the list flows into columns.
         */
        default String trailing() {
            return "";
        }

        /**
         * True for the lists long enough to be worth flowing into columns rather than scrolling through.
         */
        default boolean flows() {
            return false;
        }

        default boolean dimmed() {
            return false;
        }
    }

    private record SeriesItem(CuratedSeries series) implements Item {

        @Override
        public String label() {
            return series.name();
        }

        @Override
        public boolean flows() {
            return true;
        }
    }

    private record PartyItem(Party party) implements Item {

        @Override
        public String label() {
            return party.name();
        }

        @Override
        public String trailing() {
            return party.startDate() == null ? "" : party.startDate();
        }

        @Override
        public boolean flows() {
            return true;
        }
    }

    private record CompoItem(Party party, Competition compo) implements Item {

        @Override
        public String label() {
            return compo.name();
        }

        @Override
        public String detail() {
            return compo.typeName();
        }

        @Override
        public String trailing() {
            return String.valueOf(compo.entries().size());
        }

        String compoLabel() {
            return party.name() + COMPO_SEPARATOR + compo.name();
        }
    }

    private record EntryItem(CompoItem compo, CompoEntry entry, int index, Map<Integer, String> downloads) implements Item {

        @Override
        public String label() {
            return entry.title();
        }

        @Override
        public String detail() {
            return entry.author();
        }

        @Override
        public String trailing() {
            if (compo.compo().unsupportedFormat()) {
                return UNSUPPORTED_FORMAT;
            }
            if (hasNoDownload()) {
                return NO_DOWNLOAD;
            }
            return hasNoReader() ? NO_READER : "";
        }

        String placingText() {
            return String.format("%" + PLACING_WIDTH + "s", entry.placing());
        }

        @Override
        public boolean dimmed() {
            return !playable();
        }

        boolean playable() {
            return entry.likelyPlayable() && !compo.compo().unsupportedFormat()
                    && !hasNoDownload() && !hasNoReader();
        }

        private boolean hasNoDownload() {
            return NO_FILE.equals(downloads.get(entry.productionId()));
        }

        /**
         * The download is a container Paula cannot open, an Amiga disk image most often, so there is nothing
         * to be had from asking for it.
         */
        private boolean hasNoReader() {
            final String download = downloads.get(entry.productionId());
            return download != null && Archives.hasNoReader(download);
        }
    }

    private record ChartsItem() implements Item {

        @Override
        public String label() {
            return CHARTS;
        }

        @Override
        public boolean flows() {
            return true;
        }
    }

    private record ChartItem(Chart chart) implements Item {

        @Override
        public String label() {
            return chart.title();
        }
    }

    private record FormatItem(Chart chart, Slice slice) implements Item {

        @Override
        public String label() {
            return slice.label();
        }
    }

    private record TuneItem(Chart chart, ChartEntry entry, boolean readable) implements Item {

        @Override
        public String label() {
            return entry.title();
        }

        @Override
        public String detail() {
            return entry.fileName();
        }

        @Override
        public String trailing() {
            return readable ? entry.measure() : NO_READER;
        }

        @Override
        public boolean dimmed() {
            return !readable;
        }
    }

    /**
     * A run of pages read off a chart in one go, and where the reading got to.
     */
    private record Grown(List<Item> items, int nextPage, int lastPage) {
    }

    /**
     * How a level is laid out: the columns it flows into, and how a cell divides its width between the name,
     * the second field and whatever is set against the right edge.
     */
    private record Layout(int columns, int rows, int cellWidth, int label, int detail, int trailing) {

        private int page() {
            return columns * rows;
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
        private int artProduction;
        private int partyId;
        private int compoId;
        private Chart chart;
        private Slice slice;
        private int nextPage = 1;
        private int lastPage = Integer.MAX_VALUE;
        private boolean restingAtTheEnd;
        private final Set<Integer> seen = new HashSet<>();

        private Level(String title, String emptyText, List<Item> items) {
            this.title = title;
            this.emptyText = emptyText;
            this.items = new ArrayList<>(items);
        }

        private Optional<Item> selected() {
            return items.isEmpty() ? Optional.empty() : Optional.of(items.get(cursor));
        }

        private void move(int delta) {
            cursor = Math.clamp(cursor + delta, 0, Math.max(0, items.size() - 1));
        }

        private void cursorTo(int compoId) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof CompoItem compo && compo.compo().id() == compoId) {
                    cursor = i;
                    return;
                }
            }
        }

        /**
         * One column scrolls by the item, as it always did. Several scroll by the whole column, so that
         * stepping past the foot of one does not shuffle every other column along by a row.
         */
        private void scrollTo(Layout layout) {
            if (layout.columns() == 1) {
                offset = Math.clamp(offset, Math.max(0, cursor - layout.page() + 1), cursor);
                return;
            }
            final int column = cursor / layout.rows();
            final int first = Math.clamp(offset / layout.rows(),
                    Math.max(0, column - layout.columns() + 1), column);
            offset = first * layout.rows();
        }

        private int widest(Function<Item, String> field) {
            return items.stream().map(field).mapToInt(String::length).max().orElse(0);
        }
    }

    private static final String ROOT_TITLE = "Browse";
    private static final String CHARTS = "Charts";
    private static final String NOTHING_HERE = "Nothing here";
    private static final String NO_MUSIC = "No music competitions";
    private static final String LOADING = "Loading ";
    private static final String COMPO_SEPARATOR = " · ";
    private static final String CRUMB_SEPARATOR = " › ";
    private static final String CURSOR = "> ";
    private static final String NO_CURSOR = "  ";
    private static final String NO_DOWNLOAD = "(no download)";
    private static final String NO_READER = "(no reader)";
    private static final String UNSUPPORTED_FORMAT = "(unsupported music format)";
    private static final int COLUMN_GAP = 2;
    private static final int MOST_COLUMNS = 4;
    private static final int LEAST_DETAIL = 10;
    private static final String ELLIPSIS = "…";
    private static final String GAP = "  ";
    private static final String NO_FILE = "";
    private static final String APPLICATION = "Paula Escobar";
    private static final String SECTION = "browse";
    private static final String NOW_PLAYING_MARK = "♪ ";
    private static final char RELOAD = 'r';
    private static final int STRIP_BANDS = 16;
    private static final int PLACING_WIDTH = 3;
    private static final int CHROME_LINES = 6;
    private static final int ART_LINES = 12;
    private static final int FEWEST_ART_LINES = 3;
    private static final int FEWEST_ROWS = 6;
    private static final int MOST_PAGES_AT_ONCE = 4;
    private static final Set<Key.Special> MOVES = EnumSet.of(Key.Special.UP, Key.Special.DOWN,
            Key.Special.PAGE_UP, Key.Special.PAGE_DOWN, Key.Special.HOME, Key.Special.END);
    private static final Duration DWELL = Duration.ofMillis(500);
    private static final String TICKER_FRAMES = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏";
    private static final String TICKER_SPACE = "  ";
    private static final Duration TICKER_FRAME = Duration.ofMillis(100);
    private static final List<Frame.Key> KEYS = List.of(
            new Frame.Key("↑/↓", "move"), new Frame.Key("enter", "open"), new Frame.Key("backspace", "back"),
            new Frame.Key("b", "player"), new Frame.Key("?", "keys"), new Frame.Key("q", "quit"));
    private static final List<Frame.Key> ALL_KEYS = List.of(
            new Frame.Key("↑ ↓", "move the cursor"),
            new Frame.Key("PgUp PgDn", "move a page"),
            new Frame.Key("Home End", "jump to the first or last line"),
            new Frame.Key("tab", "hop between the charts and the series"),
            new Frame.Key("enter →", "open, or play an entry"),
            new Frame.Key("backspace", "go back one level"),
            new Frame.Key("← esc", "go back, or quit at the top"),
            new Frame.Key("r", "fetch this list and its logo afresh"),
            new Frame.Key("space", "pause or resume what is playing"),
            new Frame.Key("b", "switch to the player"),
            new Frame.Key("?", "close these keys"),
            new Frame.Key("q", "quit"));

    private final DemozooClient demozoo;
    private final ModArchiveClient modarchive;
    private final ModuleLoaderRegistry loaders;
    private final Executor executor;
    private final ReleaseArt art;
    private final PartyArt partyArt;
    private final Duration dwell;
    private final Clock clock;
    private final Deque<Level> levels = new ArrayDeque<>();
    private final Map<Integer, String> downloads = new ConcurrentHashMap<>();
    private CompletableFuture<Level> pending;
    private CompletableFuture<Grown> more;
    private Level filling;
    private Instant lastGrown;
    private String error;
    private Playlist selection;
    private int pageSize = 1;
    private int restingOn;
    private int reopening;
    private Instant restingSince;
    private int seriesLeft = 1;
    private Track nowPlaying;
    private double[] nowPlayingSpectrum = new double[0];

    public Browser(DemozooClient demozoo, ModArchiveClient modarchive, ModuleLoaderRegistry loaders, Executor executor) {
        this(demozoo, modarchive, loaders, executor, ReleaseArt.NONE);
    }

    public Browser(DemozooClient demozoo, ModArchiveClient modarchive, ModuleLoaderRegistry loaders, Executor executor,
            ReleaseArt art) {
        this(demozoo, modarchive, loaders, executor, art, PartyArt.NONE);
    }

    public Browser(DemozooClient demozoo, ModArchiveClient modarchive, ModuleLoaderRegistry loaders, Executor executor,
            ReleaseArt art, PartyArt partyArt) {
        this(demozoo, modarchive, loaders, executor, art, partyArt, DWELL, Clock.systemUTC());
    }

    Browser(DemozooClient demozoo, ModArchiveClient modarchive, ModuleLoaderRegistry loaders, Executor executor,
            ReleaseArt art, Duration dwell, Clock clock) {
        this(demozoo, modarchive, loaders, executor, art, PartyArt.NONE, dwell, clock);
    }

    Browser(DemozooClient demozoo, ModArchiveClient modarchive, ModuleLoaderRegistry loaders, Executor executor,
            ReleaseArt art, PartyArt partyArt, Duration dwell, Clock clock) {
        this.demozoo = demozoo;
        this.modarchive = modarchive;
        this.loaders = loaders;
        this.executor = executor;
        this.art = art;
        this.partyArt = partyArt;
        this.dwell = dwell;
        this.clock = clock;
        levels.push(new Level(ROOT_TITLE, NOTHING_HERE, rootItems()));
    }

    /**
     * Tab hops from wherever the cursor is among the series to the charts above them, and back to the series it
     * left.
     */
    private void hopBetweenTheChartsAndTheSeries(Level level) {
        if (level.cursor == 0) {
            level.cursor = Math.clamp(seriesLeft, 1, level.items.size() - 1);
        } else {
            seriesLeft = level.cursor;
            level.cursor = 0;
        }
    }

    /**
     * The charts sit above the series, so both are on the first page.
     */
    private static List<Item> rootItems() {
        return Stream.concat(Stream.<Item>of(new ChartsItem()),
                CuratedSeries.ALL.stream().sorted(CuratedSeries.BY_NAME).map(SeriesItem::new)).toList();
    }

    public boolean atRoot() {
        return levels.size() == 1;
    }

    /**
     * Escape backs out of a level but is left to the player at the root, where it means quit.
     */
    public static List<Frame.Key> keys() {
        return ALL_KEYS;
    }

    public boolean consumes(Key key) {
        return switch (key.special()) {
            case UP, DOWN, PAGE_UP, PAGE_DOWN, HOME, END, ENTER, BACKSPACE, LEFT, RIGHT -> true;
            case ESCAPE -> !atRoot();
            case TAB -> atRoot();
            case NONE -> Character.toLowerCase(key.character()) == RELOAD;
            default -> false;
        };
    }

    public void handle(Key key) {
        final Level level = levels.peek();
        if (MOVES.contains(key.special())) {
            level.restingAtTheEnd = false;
        }
        switch (key.special()) {
            case UP -> level.move(-1);
            case DOWN -> level.move(1);
            case PAGE_UP -> level.move(-pageSize);
            case PAGE_DOWN -> level.move(pageSize);
            case HOME -> level.move(-level.items.size());
            case END -> level.move(level.items.size());
            case ENTER, RIGHT -> open(level);
            case TAB -> hopBetweenTheChartsAndTheSeries(level);
            case BACKSPACE, LEFT, ESCAPE -> back();
            case NONE -> reload();
            default -> {
            }
        }
    }

    public void tick() {
        fetchArtOfTheEntryRestedOn();
        takeTheNextPage();
        growAtTheEndOfTheList();
        if (pending == null || !pending.isDone()) {
            return;
        }
        try {
            levels.push(pending.join());
        } catch (CompletionException | CancellationException e) {
            final Throwable cause = e.getCause() == null ? e : e.getCause();
            error = cause.getMessage() == null ? cause.toString() : cause.getMessage();
            reopening = 0;
        }
        pending = null;
        stepBackIntoTheCompetitionReloaded();
    }

    /**
     * A chart is read a page at a time as the cursor reaches the end of what has been read, so a list of
     * thousands costs nothing until it is walked. A slice as narrow as one format reads on for a few pages at
     * once, so that a step at the end brings a row rather than a wait; a run that turned up nothing then waits
     * for the next step of the cursor, rather than reading a format the chart never held to its last page, and
     * runs are held a dwell apart so that a key left on the mat does not empty the site.
     */
    private void growAtTheEndOfTheList() {
        final Level level = levels.peek();
        if (more != null || pending != null || level.chart == null || level.restingAtTheEnd
                || level.nextPage > level.lastPage || level.cursor < level.items.size() - 1
                || (lastGrown != null && lastGrown.plus(dwell).isAfter(clock.instant()))) {
            return;
        }
        filling = level;
        final Chart chart = level.chart;
        final Slice slice = level.slice;
        final int from = level.nextPage;
        final Set<Integer> seen = Set.copyOf(level.seen);
        more = CompletableFuture.supplyAsync(() -> {
            try {
                return grow(chart, slice, from, seen);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private Grown grow(Chart chart, Slice slice, int from, Set<Integer> seen) throws IOException {
        final List<Item> items = new ArrayList<>();
        int page = from;
        int lastPage = from;
        int read = 0;
        do {
            final ChartPage chartPage = modarchive.chart(chart, page);
            lastPage = chartPage.lastPage();
            chartPage.entries().stream()
                    .filter(slice::holds)
                    .filter(entry -> !seen.contains(entry.moduleId()))
                    .forEach(entry -> items.add(new TuneItem(chart, entry, canBeRead(entry))));
            page++;
            read++;
        } while (items.isEmpty() && read < MOST_PAGES_AT_ONCE && page <= lastPage);
        return new Grown(items, page, lastPage);
    }

    private boolean canBeRead(ChartEntry entry) {
        return loaders.loaderFor(Path.of(entry.fileName())).isPresent();
    }

    /**
     * A chart that could not be read is left where it stands until it is reloaded, rather than asked for
     * again on every tick.
     */
    private void takeTheNextPage() {
        if (more == null || !more.isDone()) {
            return;
        }
        final Level level = filling;
        try {
            final Grown grown = more.join();
            if (level == levels.peek()) {
                level.items.addAll(grown.items());
                grown.items().forEach(item -> level.seen.add(((TuneItem) item).entry().moduleId()));
                level.nextPage = grown.nextPage();
                level.lastPage = grown.lastPage();
                level.restingAtTheEnd = grown.items().isEmpty();
            }
        } catch (CompletionException | CancellationException e) {
            final Throwable cause = e.getCause() == null ? e : e.getCause();
            error = cause.getMessage() == null ? cause.toString() : cause.getMessage();
            level.lastPage = 0;
            level.nextPage = 1;
        }
        lastGrown = clock.instant();
        more = null;
        filling = null;
    }

    /**
     * An entry the cursor has come to rest on has its files brought down, so the art it was packed with can
     * take the place of the one the competition was opened with. Nothing is fetched while the cursor is still
     * moving.
     */
    private void fetchArtOfTheEntryRestedOn() {
        final Optional<CompoEntry> entry = levels.peek().selected()
                .filter(EntryItem.class::isInstance)
                .map(item -> ((EntryItem) item).entry());
        if (entry.map(CompoEntry::productionId).orElse(0) != restingOn) {
            restingOn = entry.map(CompoEntry::productionId).orElse(0);
            restingSince = clock.instant();
            return;
        }
        if (restingSince != null && restingSince.plus(dwell).isBefore(clock.instant())) {
            restingSince = null;
            entry.filter(this::hasArtOfItsOwn).ifPresent(art::fetch);
        }
    }

    /**
     * Art travels inside an archive, beside the module it belongs to. A release handed in as a bare recording
     * carries none, and fetching one to find that out costs the whole of it: a streaming competition is a list
     * of them, many megabytes apiece. Entries of a competition also often share the one file the party was
     * handed, so an entry downloaded from the same place as the one the competition was opened with is left
     * alone as well.
     */
    private boolean hasArtOfItsOwn(CompoEntry entry) {
        final String source = downloads.get(entry.productionId());
        return source != null && !source.equals(NO_FILE) && Archives.looksLikeArchive(source)
                && !source.equals(downloads.get(levels.peek().artProduction));
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
    public void nowPlaying(Track track, double[] spectrum) {
        nowPlaying = track;
        nowPlayingSpectrum = spectrum;
    }

    public List<AttributedString> render(int width, int height) {
        final Level level = levels.peek();
        final List<AttributedString> art = artLines(level, width, height);
        final int listRows = Math.max(1, height - CHROME_LINES - art.size());
        final Layout layout = layoutOf(level, width - 2, listRows);
        level.scrollTo(layout);
        pageSize = layout.page();
        final List<AttributedString> rows = new ArrayList<>();
        if (level.items.isEmpty()) {
            rows.add(Screen.line(b -> b.style(Palette.LABEL).append(level.emptyText)));
        } else {
            for (int row = 0; row < layout.rows(); row++) {
                rows.add(rowOf(level, layout, row));
            }
        }
        final List<AttributedString> lines = new ArrayList<>();
        lines.add(Frame.titleBar(APPLICATION, SECTION, width));
        lines.addAll(art);
        lines.addAll(Frame.box(breadcrumb() + ticker(level), rows, width, layout.rows() + 2));
        lines.add(statusLine());
        lines.add(nowPlayingLine(width));
        lines.add(Frame.footer(KEYS, width));
        return Screen.fit(lines, width, height);
    }

    private void stepBackIntoTheCompetitionReloaded() {
        if (reopening == 0) {
            return;
        }
        final Level level = levels.peek();
        level.cursorTo(reopening);
        reopening = 0;
        open(level);
    }

    private void open(Level level) {
        if (pending != null) {
            return;
        }
        error = null;
        level.selected().ifPresent(item -> {
            switch (item) {
                case ChartsItem charts -> levels.push(new Level(charts.label(), NOTHING_HERE,
                        Arrays.stream(Chart.values()).<Item>map(ChartItem::new).toList()));
                case ChartItem chart -> levels.push(new Level(chart.label(), NOTHING_HERE,
                        Arrays.stream(Slice.values()).<Item>map(slice -> new FormatItem(chart.chart(), slice)).toList()));
                case FormatItem format -> openChart(format);
                case SeriesItem series -> load(series.label(), NOTHING_HERE, () -> partyItems(series.series().id()));
                case PartyItem party -> load(party.label(), NO_MUSIC, () -> compoItems(party.party()));
                case CompoItem compo -> openCompo(compo);
                case EntryItem entry -> selection = playlistFrom(level, entry);
                case TuneItem tune -> selection = playlistFrom(level, tune);
            }
        });
    }

    private void openChart(FormatItem format) {
        final Level level = new Level(format.label(), NOTHING_HERE, List.of());
        level.chart = format.chart();
        level.slice = format.slice();
        levels.push(level);
    }

    /**
     * Throws away what was kept for the level in view and opens it again, so a list that has moved on since,
     * or a logo that never arrived, can be had afresh without leaving the browser. The entries of a
     * competition come with the party's answer, so reloading one goes back through the competition list and
     * steps into it again once it has been fetched. A chart drops every page it has read and begins again at
     * its first.
     */
    private void reload() {
        if (atRoot() || pending != null) {
            return;
        }
        final Level level = levels.peek();
        if (level.chart != null) {
            modarchive.forget(level.chart);
            more = null;
            filling = null;
            lastGrown = null;
            levels.pop();
            open(levels.peek());
            return;
        }
        reopening = level.compoId;
        if (level.compoId != 0) {
            partyArt.forget(level.partyId);
            levels.pop();
        }
        levels.pop();
        final Level parent = levels.peek();
        parent.selected().ifPresent(this::forget);
        open(parent);
    }

    private void forget(Item item) {
        switch (item) {
            case SeriesItem series -> demozoo.forgetSeries(series.series().id());
            case PartyItem party -> demozoo.forgetParty(party.party().id());
            default -> {
            }
        }
    }

    /**
     * A fetch still in flight belongs to the level being left, so its answer is dropped when it arrives.
     */
    private void back() {
        if (!atRoot()) {
            levels.pop();
        }
        pending = null;
        more = null;
        filling = null;
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
        final Level level = new Level(compo.compoLabel(), NOTHING_HERE, items);
        level.partyId = compo.party().id();
        level.compoId = compo.compo().id();
        partyArt.fetch(level.partyId);
        entries.stream().filter(CompoEntry::likelyPlayable).findFirst().ifPresent(entry -> {
            level.artProduction = entry.productionId();
            art.fetch(entry);
        });
        levels.push(level);
        CompletableFuture.runAsync(() -> lookUpDownloads(entries), executor);
    }

    private void lookUpDownloads(List<CompoEntry> entries) {
        for (final CompoEntry entry : entries) {
            if (!downloads.containsKey(entry.productionId())) {
                try {
                    downloads.put(entry.productionId(), TrackResolver.preferredLinks(demozoo.production(entry.productionId()))
                            .stream().map(Link::url).findFirst().orElse(NO_FILE));
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
                .<Track>map(item -> new DemozooTrack(item.entry(), item.compo().party(), item.compo().compo()))
                .toList();
        return new Playlist(tracks);
    }

    /**
     * The chosen tune plays even when nothing here reads its format, since the chart names a file Paula may yet
     * know what to do with; the rest of the chart as it has been read so far follows in ranked order.
     */
    private static Playlist playlistFrom(Level level, TuneItem chosen) {
        final List<Track> tracks = level.items.subList(level.cursor, level.items.size()).stream()
                .map(TuneItem.class::cast)
                .filter(tune -> tune == chosen || tune.readable())
                .<Track>map(tune -> new ModArchiveTrack(tune.chart(), tune.entry()))
                .toList();
        return new Playlist(tracks);
    }

    private String breadcrumb() {
        final List<String> titles = new ArrayList<>(levels.stream().map(level -> level.title).toList());
        Collections.reverse(titles);
        return String.join(CRUMB_SEPARATOR, titles);
    }

    /**
     * A list long enough to be worth it is filled column by column, so walking down with the cursor runs to
     * the foot of one column and on to the head of the next rather than off the bottom of the screen.
     */
    private Layout layoutOf(Level level, int width, int rows) {
        final int trailing = level.widest(Item::trailing);
        if (level.items.stream().noneMatch(Item::flows)) {
            final int label = level.widest(Item::label);
            final int room = Math.max(LEAST_DETAIL, width - label - NO_CURSOR.length() - COLUMN_GAP);
            final int detail = Math.min(level.widest(Item::detail), room);
            return new Layout(1, rows, width, label, detail, trailing);
        }
        final int cell = level.widest(Item::label) + trailing + NO_CURSOR.length() + COLUMN_GAP * 2;
        final int columns = Math.clamp(width / Math.max(1, cell), 1, MOST_COLUMNS);
        return new Layout(columns, rows, width / columns, level.widest(Item::label), 0, trailing);
    }

    private AttributedString rowOf(Level level, Layout layout, int row) {
        final AttributedStringBuilder line = new AttributedStringBuilder();
        for (int column = 0; column < layout.columns(); column++) {
            final int index = level.offset + column * layout.rows() + row;
            if (index < level.items.size()) {
                line.append(cell(level.items.get(index), index == level.cursor, layout, ticker(level.items.get(index))));
            }
        }
        return line.toAttributedString();
    }

    /**
     * One item across its share of the width: the cursor mark, the name, the second field where there is one,
     * and whatever is set against the right edge. The chosen one is painted across its own cell only, since
     * with several columns to a row the rest of the row belongs to other items.
     */
    private AttributedString cell(Item item, boolean selected, Layout layout, String ticker) {
        final boolean playing = playing(item);
        final AttributedStyle text = selected ? Palette.SELECTED
                : playing ? Palette.ACTIVE : item.dimmed() ? Palette.DIMMED : Palette.VALUE;
        final AttributedStringBuilder line = new AttributedStringBuilder();
        line.style(selected ? Palette.SELECTED : Palette.ACCENT)
                .append(playing ? NOW_PLAYING_MARK : selected ? CURSOR : NO_CURSOR);
        int written = NO_CURSOR.length();
        if (item instanceof EntryItem entry) {
            line.style(selected ? Palette.SELECTED : medal(entry.entry().placing(), text)).append(entry.placingText());
            written += entry.placingText().length() + COLUMN_GAP;
            line.style(text).append(GAP);
        }
        final int room = layout.cellWidth() - written - ticker.length();
        final int spare = Math.max(room / 4, room - layout.label() - layout.trailing() - COLUMN_GAP * 2);
        final int detail = item.detail().isEmpty() ? 0 : Math.min(layout.detail(), spare);
        final int trailing = Math.min(layout.trailing(), Math.max(0, room - detail - COLUMN_GAP));
        final int gaps = (detail > 0 ? COLUMN_GAP : 0) + (trailing > 0 ? COLUMN_GAP : 0);
        final int label = Math.max(1, Math.min(layout.label(), room - gaps - detail - trailing));
        line.style(text).append(fitted(item.label(), label));
        written += label;
        if (detail > 0) {
            line.append(GAP).append(fitted(item.detail(), detail));
            written += COLUMN_GAP + detail;
        }
        if (trailing > 0) {
            final int gap = Math.max(COLUMN_GAP, layout.cellWidth() - ticker.length() - written - trailing);
            line.append(" ".repeat(gap)).append(fitted(item.trailing(), trailing).stripTrailing());
        }
        line.style(selected ? Palette.SELECTED_ACCENT : Palette.ACCENT).append(ticker);
        return Frame.pad(line.toAttributedString(), layout.cellWidth(),
                selected ? Palette.SELECTED : AttributedStyle.DEFAULT);
    }

    /**
     * The text padded out to its column, or cut short with an ellipsis where it will not go.
     */
    private static String fitted(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (text.length() <= width) {
            return text + " ".repeat(width - text.length());
        }
        return width == 1 ? ELLIPSIS : text.substring(0, width - 1) + ELLIPSIS;
    }

    /**
     * The song being played marks its own row and every row on the way down to it, so it can be found again
     * from anywhere in the browser. Local files were never browsed to and mark nothing.
     */
    private boolean playing(Item item) {
        return switch (item) {
            case EntryItem entry -> nowPlaying instanceof DemozooTrack track
                    && entry.compo().compo().id() == track.compo().id()
                    && entry.entry().productionId() == track.entry().productionId();
            case CompoItem compo -> nowPlaying instanceof DemozooTrack track && compo.compo().id() == track.compo().id();
            case PartyItem party -> nowPlaying instanceof DemozooTrack track && party.party().id() == track.party().id();
            case SeriesItem series -> false;
            case ChartsItem charts -> nowPlaying instanceof ModArchiveTrack;
            case ChartItem chart -> nowPlaying instanceof ModArchiveTrack track && track.chart() == chart.chart();
            case FormatItem format -> nowPlaying instanceof ModArchiveTrack track && track.chart() == format.chart()
                    && format.slice().holds(track.entry());
            case TuneItem tune -> nowPlaying instanceof ModArchiveTrack track && track.chart() == tune.chart()
                    && track.entry().moduleId() == tune.entry().moduleId();
        };
    }

    /**
     * A turning ticker beside whatever is being brought down, so a wait for a logo or for the next page of a
     * chart is visible rather than looking like nothing happening.
     */
    private String ticker(Item item) {
        return item instanceof EntryItem entry && art.fetching(entry.entry().productionId()) ? ticker() : "";
    }

    private String ticker(Level level) {
        return level == filling || (level.partyId != 0 && partyArt.fetching(level.partyId)) ? ticker() : "";
    }

    private String ticker() {
        final long frame = clock.instant().toEpochMilli() / TICKER_FRAME.toMillis();
        return TICKER_SPACE + TICKER_FRAMES.charAt((int) Math.floorMod(frame, TICKER_FRAMES.length()));
    }

    private static AttributedStyle medal(String placing, AttributedStyle fallback) {
        return switch (placing) {
            case "1" -> Palette.GOLD;
            case "2" -> Palette.SILVER;
            case "3" -> Palette.BRONZE;
            default -> fallback;
        };
    }

    /**
     * The art a release was packed with is shown above the list while the cursor rests on it, so browsing a
     * competition shows the banner drawn for it. Where an entry has none of its own, the art of the entry the
     * competition was opened with stands for the whole of it. A screen too short to spare the room shows none.
     */
    private List<AttributedString> artLines(Level level, int width, int height) {
        final int room = Math.min(ART_LINES, height - CHROME_LINES - FEWEST_ROWS);
        if (room < FEWEST_ART_LINES) {
            return List.of();
        }
        return level.selected()
                .filter(EntryItem.class::isInstance)
                .map(item -> ((EntryItem) item).entry().productionId())
                .flatMap(production -> art.of(production)
                        .or(() -> art.of(level.artProduction))
                        .or(() -> partyArt.of(level.partyId)))
                .map(lines -> centred(lines.stream().limit(room).toList(), width))
                .orElseGet(List::of);
    }

    /**
     * The block is moved across as a whole, since art centred line by line would lose its shape.
     */
    private static List<AttributedString> centred(List<String> art, int width) {
        final int longest = art.stream().mapToInt(String::length).max().orElse(0);
        final String indent = " ".repeat(Math.max(0, (width - longest) / 2));
        return art.stream()
                .map(line -> Screen.line(b -> b.style(Palette.SCOPE_QUIET).append(clipped(indent + line, width))))
                .toList();
    }

    private static String clipped(String line, int width) {
        return line.length() <= width ? line : line.substring(0, width);
    }

    private AttributedString statusLine() {
        if (more != null) {
            return Screen.line(b -> b.style(Palette.ACCENT).append(LOADING).append(filling.chart.title())
                    .append(COMPO_SEPARATOR).append(filling.title).append('…'));
        }
        if (pending != null) {
            return Screen.line(b -> b.style(Palette.ACCENT).append(LOADING).append(loadingTitle()).append('…'));
        }
        if (error != null) {
            return Screen.line(b -> b.style(Palette.ACCENT).append(error));
        }
        return AttributedString.EMPTY;
    }

    private AttributedString nowPlayingLine(int width) {
        if (nowPlaying == null) {
            return AttributedString.EMPTY;
        }
        final AttributedStringBuilder line = new AttributedStringBuilder()
                .style(Palette.ACCENT).append(NOW_PLAYING_MARK).style(Palette.VALUE).append(nowPlaying.label()).append("  ");
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

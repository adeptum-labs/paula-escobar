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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.CuratedSeries;
import com.adeptum.paula.demozoo.DemozooClient;
import com.adeptum.paula.demozoo.PartyArt;
import com.adeptum.paula.demozoo.ReleaseArt;
import com.adeptum.paula.demozoo.FakeHttp;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.Playlist;
import com.adeptum.paula.ui.visual.Palette;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserTest {

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-09-02T18:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }


    private static final String TICKER_FRAMES = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏";
    private static final int WIDTH = 80;
    private static final int HEIGHT = 12;
    private static final int TALL = 24;
    private static final int THE_PARTY_SERIES = 19;
    private static final String SERIES_URL = "https://demozoo.org/api/v1/party_series/19/?format=json";
    private static final String PARTY_URL = "https://demozoo.org/api/v1/parties/5/?format=json";
    private static final String SERIES = """
            {"id":19,"name":"The Party","parties":[
              {"id":5,"name":"The Party 1995","start_date":"1995-12-27"},
              {"id":4,"name":"The Party 1994","start_date":"1994-12-27"}]}
            """;
    private static final String PARTY = """
            {"id":5,"name":"The Party 1995","competitions":[
              {"id":1,"name":"Demo","production_type":{"id":1,"name":"Demo","supertype":"production"},"results":[]},
              {"id":2,"name":"Multichannel Music","production_type":{"id":29,"name":"Tracked Music","supertype":"music"},"results":[
                {"position":1,"ranking":"1","production":{"id":11,"title":"First","author_nicks":[{"name":"A"}],"types":[{"id":29}]}},
                {"position":2,"ranking":"2","production":{"id":12,"title":"Second","author_nicks":[{"name":"B"}],"types":[{"id":29}]}},
                {"position":3,"ranking":"3","production":{"id":13,"title":"Exe","author_nicks":[{"name":"C"}],"types":[{"id":31}]}},
                {"position":4,"ranking":"4","production":{"id":14,"title":"Fourth","author_nicks":[{"name":"D"}],"types":[{"id":29}]}}]}]}
            """;
    private static final String EMPTY_PARTY = "{\"id\":5,\"name\":\"The Party 1995\",\"competitions\":[]}";
    private static final String PRODUCTION_WITH_DOWNLOAD = "{\"id\":0,\"title\":\"x\",\"download_links\":[{\"link_class\":\"SceneOrgFile\",\"url\":\"https://files.scene.org/view/x.zip\"}],\"external_links\":[]}";
    private static final String PRODUCTION_AS_DISK_IMAGE = "{\"id\":0,\"title\":\"x\",\"download_links\":[{\"link_class\":\"SceneOrgFile\",\"url\":\"https://files.scene.org/view/tune.adf\"}],\"external_links\":[]}";
    private static final String PRODUCTION_WITHOUT_DOWNLOAD = "{\"id\":0,\"title\":\"x\",\"download_links\":[],\"external_links\":[{\"link_class\":\"PouetProduction\",\"url\":\"https://www.pouet.net/prod.php?which=1\"}]}";

    private static String productionUrl(int id) {
        return "https://demozoo.org/api/v1/productions/" + id + "/?format=json";
    }

    private final FakeHttp http = new FakeHttp();
    private Browser browser;
    private CacheDirectory cache;

    @BeforeEach
    void createBrowser(@TempDir Path dir) {
        cache = new CacheDirectory(dir);
        browser = new Browser(new DemozooClient(http, cache), Runnable::run);
    }

    @Test
    void fetchesTheArtOfAnEntryTheCursorComesToRestOn() {
        final List<CompoEntry> fetched = new ArrayList<>();
        final MutableClock clock = new MutableClock();
        browser = new Browser(new DemozooClient(http, cache), Runnable::run, new ReleaseArt() {

            @Override
            public Optional<List<String>> of(int productionId) {
                return Optional.empty();
            }

            @Override
            public void fetch(CompoEntry entry) {
                fetched.add(entry);
            }
        }, Duration.ofMillis(500), clock);
        http.put(productionUrl(11), PRODUCTION_WITH_DOWNLOAD);
        http.put(productionUrl(12), PRODUCTION_WITH_DOWNLOAD.replace("x.zip", "another.zip"));
        openParty();
        press(Key.Special.ENTER);
        browser.tick();
        fetched.clear();

        press(Key.Special.DOWN);
        browser.tick();
        assertEquals(List.of(), fetched, "a cursor still on the move fetches nothing");

        clock.advance(Duration.ofSeconds(1));
        browser.tick();

        assertEquals(1, fetched.size(), "the entry rested on is fetched");
        assertEquals(12, fetched.getFirst().productionId());
    }

    @Test
    void leavesAloneAnEntryDownloadedFromTheSamePlaceAsTheCompetition() {
        final List<CompoEntry> fetched = new ArrayList<>();
        final MutableClock clock = new MutableClock();
        http.put(productionUrl(11), PRODUCTION_WITH_DOWNLOAD);
        http.put(productionUrl(12), PRODUCTION_WITH_DOWNLOAD);
        browser = new Browser(new DemozooClient(http, cache), Runnable::run, new ReleaseArt() {

            @Override
            public Optional<List<String>> of(int productionId) {
                return Optional.empty();
            }

            @Override
            public void fetch(CompoEntry entry) {
                fetched.add(entry);
            }
        }, Duration.ofMillis(500), clock);
        openParty();
        press(Key.Special.ENTER);
        browser.tick();
        fetched.clear();

        press(Key.Special.DOWN);
        browser.tick();
        clock.advance(Duration.ofSeconds(1));
        browser.tick();

        assertEquals(List.of(), fetched, "the party file it shares carries the art already fetched");
    }

    @Test
    void fetchesTheArtOfACompetitionAsItOpensAndShowsItForEveryEntry() {
        final List<CompoEntry> fetched = new ArrayList<>();
        final List<String> banner = List.of("== ICING ==", "== 1997  ==");
        browser = new Browser(new DemozooClient(http, cache), Runnable::run, new ReleaseArt() {

            @Override
            public Optional<List<String>> of(int productionId) {
                return productionId == 11 ? Optional.of(banner) : Optional.empty();
            }

            @Override
            public void fetch(CompoEntry entry) {
                fetched.add(entry);
            }
        });
        openParty();
        press(Key.Special.ENTER);
        browser.tick();
        press(Key.Special.DOWN);

        final List<String> lines = browser.render(WIDTH, TALL).stream().map(AttributedString::toString).toList();

        assertEquals(1, fetched.size(), "one entry is fetched for the competition");
        assertEquals(11, fetched.getFirst().productionId(), "the first entry that can be played");
        assertTrue(lines.get(1).contains("ICING"), "and its art stands for the entry below it too");
    }

    @Test
    void showsTheArtTheEntryWasPackedWith() {
        final List<String> banner = List.of(".------------------.", "|  I.C.I.N.G 9.7   |", "`------------------'");
        browser = new Browser(new DemozooClient(http, cache), Runnable::run, production -> Optional.of(banner));
        openParty();
        press(Key.Special.ENTER);
        browser.tick();

        final List<String> lines = browser.render(WIDTH, TALL).stream().map(AttributedString::toString).toList();

        assertTrue(lines.get(1).strip().equals(banner.getFirst()), "the art sits under the title bar");
        assertTrue(lines.get(2).contains("I.C.I.N.G"));
        assertTrue(lines.get(1).startsWith("   "), "and is centred as a block");
        assertTrue(lines.get(4).contains("Multichannel Music"), "the list follows it");
        assertEquals(TALL, lines.size());
    }

    @Test
    void reloadingACompetitionListFetchesItAgain() {
        openParty();
        final int requests = http.requests();

        press('r');
        browser.tick();

        assertTrue(http.requests() > requests, "the party is asked for again");
        assertTrue(render().stream().anyMatch(line -> line.contains("Multichannel Music")), "and the list comes back");
    }

    @Test
    void reloadingACompetitionStepsBackIntoItAndForgetsItsLogo() {
        final List<Integer> forgotten = new ArrayList<>();
        browser = new Browser(new DemozooClient(http, cache), Runnable::run, ReleaseArt.NONE, new PartyArt() {

            @Override
            public Optional<List<String>> of(int partyId) {
                return Optional.empty();
            }

            @Override
            public void forget(int partyId) {
                forgotten.add(partyId);
            }
        });
        openParty();
        press(Key.Special.ENTER);
        browser.tick();

        press('r');
        browser.tick();

        assertEquals(List.of(5), forgotten, "the logo of the party is thrown away");
        final List<String> lines = render();
        assertTrue(lines.stream().anyMatch(line -> line.contains("First")), "and the competition is open again");
        assertTrue(lines.stream().anyMatch(line -> line.contains("Multichannel Music")));
    }

    @Test
    void theRootIsLeftAloneByReload() {
        final int requests = http.requests();

        press('r');
        browser.tick();

        assertEquals(requests, http.requests(), "there is nothing cached behind the list of series");
        assertTrue(render().stream().anyMatch(line -> line.contains("Parties")));
    }

    @Test
    void showsThePartyLogoWhereTheReleasesCarryNoneOfTheirOwn() {
        final List<String> logo = List.of(".---------------.", "|   I C I N G   |", "`---------------'");
        browser = new Browser(new DemozooClient(http, cache), Runnable::run, ReleaseArt.NONE, partyArt(logo, false));
        openParty();
        press(Key.Special.ENTER);
        browser.tick();

        final List<String> lines = browser.render(WIDTH, TALL).stream().map(AttributedString::toString).toList();

        assertTrue(lines.get(2).contains("I C I N G"), "the logo of the party stands in for the competition");
    }

    @Test
    void theArtOfAnEntryComesBeforeTheLogoOfTheParty() {
        final List<String> logo = List.of(".---------------.", "|   I C I N G   |", "`---------------'");
        final List<String> banner = List.of(".------------------.", "|  R E L E A S E   |", "`------------------'");
        browser = new Browser(new DemozooClient(http, cache), Runnable::run, production -> Optional.of(banner), partyArt(logo, false));
        openParty();
        press(Key.Special.ENTER);
        browser.tick();

        final List<String> lines = browser.render(WIDTH, TALL).stream().map(AttributedString::toString).toList();

        assertTrue(lines.get(2).contains("R E L E A S E"), "what the release carried wins");
        assertFalse(lines.stream().anyMatch(line -> line.contains("I C I N G")));
    }

    @Test
    void tickerRunsBesideTheEntryWhoseFilesAreOnTheirWayDown() {
        browser = new Browser(new DemozooClient(http, cache), Runnable::run, new ReleaseArt() {

            @Override
            public Optional<List<String>> of(int productionId) {
                return Optional.empty();
            }

            @Override
            public boolean fetching(int productionId) {
                return productionId == 12;
            }
        }, PartyArt.NONE);
        openParty();
        press(Key.Special.ENTER);
        browser.tick();

        final List<String> lines = render();

        assertTrue(lines.stream().anyMatch(line -> line.contains("Second") && ticks(line)), "the entry being fetched ticks");
        assertFalse(lines.stream().anyMatch(line -> line.contains("First") && ticks(line)), "the others do not");
    }

    @Test
    void tickerRunsOnTheCompetitionWhileItsLogoIsFetched() {
        browser = new Browser(new DemozooClient(http, cache), Runnable::run, ReleaseArt.NONE, partyArt(List.of(), true));
        openParty();
        press(Key.Special.ENTER);
        browser.tick();

        assertTrue(render().stream().anyMatch(line -> line.contains("Multichannel Music") && ticks(line)),
                "the competition ticks while its logo is on its way");
    }

    private static boolean ticks(String line) {
        return TICKER_FRAMES.chars().anyMatch(frame -> line.indexOf(frame) >= 0);
    }

    private static PartyArt partyArt(List<String> logo, boolean fetching) {
        return new PartyArt() {

            @Override
            public Optional<List<String>> of(int partyId) {
                return logo.isEmpty() ? Optional.empty() : Optional.of(logo);
            }

            @Override
            public boolean fetching(int partyId) {
                return fetching;
            }
        };
    }

    @Test
    void startsWithTheCuratedSeries() {
        final List<String> lines = render();
        assertTrue(lines.get(0).contains("Paula Escobar") && lines.get(0).contains("browse"), "title bar");
        assertTrue(lines.get(1).contains("Parties"), "the box is titled with the breadcrumb");
        assertTrue(lines.get(2).startsWith("│> Abduction"), "listed by name, so Abduction leads");
        assertTrue(lines.get(3).startsWith("│  Alternative Party"));
        assertTrue(lines.get(4).contains("Árok"), "an accent sorts among the A's, not after the Z's");
        assertTrue(lines.get(2).trim().split(" {2,}").length > 1, "and the list flows into columns");
        assertTrue(lines.get(HEIGHT - 1).contains("quit"), "key bar");
        assertTrue(browser.render(WIDTH, HEIGHT).stream().allMatch(line -> line.columnLength() == WIDTH));
        assertTrue(browser.atRoot());
    }

    /**
     * Fifty-two series down one column is three screens of scrolling for a list that fits on one.
     */
    @Test
    void flowsTheLongListsIntoColumns() {
        final List<String> lines = render();

        final int perRow = lines.get(2).replaceAll("[│>]", "").strip().split(" {2,}").length;
        assertTrue(perRow > 1, "the series go several across: " + lines.get(2));
        final int rows = (int) lines.stream().filter(l -> l.startsWith("│") && !l.contains("─")).count();
        assertTrue(rows * perRow > rows, "more series are on screen than there are rows for them");
    }

    /**
     * Walking down runs to the foot of a column and on to the head of the next, so the cursor keys mean the
     * same thing they always did whatever the list is laid out as.
     */
    @Test
    void theCursorWalksDownAColumnAndOnToTheNext() {
        final int rows = (int) render().stream().filter(l -> l.startsWith("│") && !l.contains("─")).count();
        for (int i = 0; i < rows; i++) {
            press(Key.Special.DOWN);
        }

        final List<String> lines = render();
        assertTrue(lines.get(2).contains("> "), "the cursor is back at the top row");
        assertFalse(lines.get(2).startsWith("│> "), "but in the second column now: " + lines.get(2));
    }

    @Test
    void listsThePartySeriesByName() {
        final List<String> names = render().stream().filter(l -> l.startsWith("│") && !l.contains("─"))
                .map(l -> l.replaceAll("[│>]", "").strip().split(" {2,}")[0]).toList();

        assertEquals(CuratedSeries.ALL.stream().sorted(CuratedSeries.BY_NAME).map(CuratedSeries::name)
                .limit(names.size()).toList(), names, "the first column reads by name: " + names);
    }

    @Test
    void enterOnASeriesLoadsItsPartiesOldestFirst() {
        http.put(SERIES_URL, SERIES);
        cursorToTheParty();
        press(Key.Special.ENTER);
        browser.tick();

        final List<String> lines = render();
        assertTrue(lines.get(1).contains("Parties › The Party"));
        assertTrue(lines.get(2).startsWith("│> The Party 1994"));
        assertTrue(lines.get(3).startsWith("│  The Party 1995"));
        assertFalse(browser.atRoot());
    }

    @Test
    void enterOnAPartyListsMusicCompetitionsWithEntryCounts() {
        openParty();
        final String compo = render().get(2);
        assertTrue(compo.startsWith("│> Multichannel Music"), compo);
        assertTrue(compo.contains("Tracked Music"), "the competition says what it was run in: " + compo);
        assertTrue(compo.stripTrailing().endsWith("4│"), "and how many entries it drew: " + compo);
    }

    @Test
    void enterOnACompetitionListsRankedEntriesAndDimsUnplayableOnes() {
        openCompo();
        final List<AttributedString> lines = browser.render(WIDTH, HEIGHT);
        assertTrue(lines.get(2).toString().startsWith("│>   1  First"), lines.get(2).toString());
        assertTrue(lines.get(2).toString().contains("A"), "the author has a column of its own");
        assertTrue(lines.get(4).toString().startsWith("│    3  Exe"), lines.get(4).toString());
        assertEquals(Palette.DIMMED, lines.get(4).styleAt(8), "dimmed title");
        assertEquals(Palette.VALUE, lines.get(3).styleAt(8));
        assertEquals(Palette.SILVER, lines.get(3).styleAt(5), "second place is silver");
        assertEquals(Palette.BRONZE, lines.get(4).styleAt(5), "third place is bronze");
        assertEquals(Palette.SELECTED, lines.get(2).styleAt(1), "the cursor row is highlighted from edge to edge");
        assertEquals(Palette.SELECTED, lines.get(2).styleAt(WIDTH - 2));
    }

    @Test
    void enterOnAnEntryQueuesItAndThePlayableRest() {
        openCompo();
        press(Key.Special.DOWN);
        press(Key.Special.ENTER);

        final Playlist playlist = browser.takeSelection().orElseThrow();
        assertEquals(2, playlist.size());
        assertEquals("The Party 1995 · Multichannel Music  #2 Second by B", playlist.current().label());
        playlist.next();
        assertEquals("Fourth", ((DemozooTrack) playlist.current()).entry().title());
        assertEquals(Optional.empty(), browser.takeSelection(), "a selection is handed over once");
    }

    @Test
    void enterOnADimmedEntryStillQueuesThatEntry() {
        openCompo();
        press(Key.Special.DOWN);
        press(Key.Special.DOWN);
        press(Key.Special.ENTER);

        final Playlist playlist = browser.takeSelection().orElseThrow();
        assertEquals("Exe", ((DemozooTrack) playlist.current()).entry().title());
        assertEquals(2, playlist.size());
    }

    @Test
    void backspaceLeftAndEscapeGoUpOneLevel() {
        openParty();
        press(Key.Special.BACKSPACE);
        assertTrue(render().stream().anyMatch(line -> line.startsWith("│> The Party 1995")));
        press(Key.Special.LEFT);
        assertTrue(browser.atRoot());
        assertFalse(browser.consumes(Key.of(Key.Special.ESCAPE)), "escape at the root is left to the player");
    }

    @Test
    void cursorClampsAndPagesWithinTheList() {
        openCompo();
        press(Key.Special.UP);
        assertTrue(render().get(2).startsWith("│>   1"));
        press(Key.Special.END);
        assertTrue(render().stream().anyMatch(line -> line.startsWith("│>   4")));
        press(Key.Special.DOWN);
        assertTrue(render().stream().anyMatch(line -> line.startsWith("│>   4")));
        press(Key.Special.HOME);
        press(Key.Special.PAGE_DOWN);
        assertTrue(render().stream().anyMatch(line -> line.startsWith("│>   4")), "a page is the visible rows");
        press(Key.Special.PAGE_UP);
        assertTrue(render().get(2).startsWith("│>   1"));
    }

    @Test
    void scrollsTheWindowToKeepTheCursorVisible() {
        openCompo();
        final List<String> tall = browser.render(WIDTH, 8).stream().map(AttributedString::toString).toList();
        assertEquals(2, tall.stream().filter(line -> line.matches("│[> ] +\\d.*")).count(), "eight rows leave two for the list");
        press(Key.Special.END);
        assertTrue(browser.render(WIDTH, 8).stream().map(AttributedString::toString).anyMatch(line -> line.startsWith("│>   4")));
    }

    @Test
    void showsAnErrorAndStaysWhenTheFetchFails() {
        http.goOffline();
        press(Key.Special.ENTER);
        browser.tick();

        assertTrue(render().stream().anyMatch(line -> line.contains("offline")));
        assertTrue(browser.atRoot());

        http.goOnline();
        http.put(SERIES_URL, SERIES);
        cursorToTheParty();
        press(Key.Special.ENTER);
        browser.tick();
        assertFalse(browser.atRoot());
        assertFalse(render().stream().anyMatch(line -> line.contains("offline")));
    }

    @Test
    void showsAnEmptyLevelForPartiesWithoutMusic() {
        http.put(SERIES_URL, SERIES);
        http.put(PARTY_URL, EMPTY_PARTY);
        cursorToTheParty();
        press(Key.Special.ENTER);
        browser.tick();
        press(Key.Special.DOWN);
        press(Key.Special.ENTER);
        browser.tick();

        assertTrue(render().stream().anyMatch(line -> line.contains("No music competitions")));
        press(Key.Special.ENTER);
        assertEquals(Optional.empty(), browser.takeSelection());
    }

    @Test
    void dropsAFetchThatLandsAfterBackingOut(@TempDir Path dir) {
        final Deque<Runnable> queued = new ArrayDeque<>();
        final Browser deferred = new Browser(new DemozooClient(http, new CacheDirectory(dir)), queued::add);
        http.put(SERIES_URL, SERIES);
        http.put(PARTY_URL, PARTY);
        deferred.handle(Key.of(Key.Special.ENTER));
        queued.pop().run();
        deferred.tick();
        deferred.handle(Key.of(Key.Special.DOWN));
        deferred.handle(Key.of(Key.Special.ENTER));
        deferred.handle(Key.of(Key.Special.BACKSPACE));
        queued.pop().run();
        deferred.tick();

        assertTrue(deferred.atRoot());
        assertFalse(deferred.render(WIDTH, HEIGHT).stream().map(AttributedString::toString).anyMatch(line -> line.contains("Loading")));
    }

    @Test
    void showsUnexpectedAnswersAndClearsErrorsOnBack() {
        http.put(SERIES_URL, "{\"id\":19,\"name\":\"The Party\",\"parties\":[{\"name\":\"no id\"}]}");
        cursorToTheParty();
        press(Key.Special.ENTER);
        browser.tick();
        assertTrue(render().stream().anyMatch(line -> line.contains("Demozoo response")));

        http.put(SERIES_URL, SERIES);
        press(Key.Special.ENTER);
        browser.tick();
        press(Key.Special.BACKSPACE);
        assertFalse(render().stream().anyMatch(line -> line.contains("Demozoo response")));
    }

    @Test
    void showsMessagesReportedByThePlayerUntilTheNextMove() {
        browser.report("No playable file in x.zip for Funkyeeh");
        assertTrue(render().stream().anyMatch(line -> line.contains("No playable file")));
        press(Key.Special.DOWN);
        assertTrue(render().stream().anyMatch(line -> line.contains("No playable file")), "moving the cursor keeps the message");
        press(Key.Special.LEFT);
        assertFalse(render().stream().anyMatch(line -> line.contains("No playable file")), "leaving a level clears it");
    }

    @Test
    void startsThePlaylistAtTheChosenLineEvenWhenEntriesRepeat() {
        http.put(SERIES_URL, SERIES);
        http.put(PARTY_URL, PARTY.replace("\"position\":4", "\"position\":4,\"ranking\":\"4\",\"production\":{\"id\":14,\"title\":\"Fourth\",\"author_nicks\":[{\"name\":\"D\"}],\"types\":[{\"id\":29}]}},{\"position\":4"));
        cursorToTheParty();
        press(Key.Special.ENTER);
        browser.tick();
        press(Key.Special.DOWN);
        press(Key.Special.ENTER);
        browser.tick();
        press(Key.Special.ENTER);
        press(Key.Special.END);
        press(Key.Special.ENTER);

        assertEquals(1, browser.takeSelection().orElseThrow().size());
    }

    @Test
    void marksEntriesWithoutAnyDownloadOnceTheirDetailsArrive() {
        http.put(productionUrl(11), PRODUCTION_WITH_DOWNLOAD);
        http.put(productionUrl(12), PRODUCTION_WITHOUT_DOWNLOAD);
        http.put(productionUrl(14), PRODUCTION_WITH_DOWNLOAD);
        openCompo();
        browser.tick();

        final List<AttributedString> lines = browser.render(WIDTH, HEIGHT);
        assertTrue(lines.get(3).toString().startsWith("│    2  Second"), lines.get(3).toString());
        assertTrue(lines.get(3).toString().stripTrailing().endsWith("(no download)│"), lines.get(3).toString());
        assertEquals(Palette.DIMMED, lines.get(3).styleAt(8));
        assertTrue(lines.get(2).toString().startsWith("│>   1  First"), "known downloads are not annotated");
        assertTrue(lines.get(4).toString().startsWith("│    3  Exe"), "an entry Demozoo cannot describe is left alone");

        press(Key.Special.ENTER);
        final Playlist playlist = browser.takeSelection().orElseThrow();
        assertEquals(2, playlist.size(), "entries without a download are not queued");
    }

    /**
     * An Amiga disk image is a container Paula has no reader for, so the entry says so and is left out of what
     * playing one queues up.
     */
    @Test
    void marksEntriesWhoseOnlyDownloadCannotBeOpened() {
        http.put(productionUrl(11), PRODUCTION_WITH_DOWNLOAD);
        http.put(productionUrl(12), PRODUCTION_AS_DISK_IMAGE);
        http.put(productionUrl(14), PRODUCTION_WITH_DOWNLOAD);
        openCompo();
        browser.tick();

        final List<AttributedString> lines = browser.render(WIDTH, HEIGHT);
        assertTrue(lines.get(3).toString().startsWith("│    2  Second"), lines.get(3).toString());
        assertTrue(lines.get(3).toString().stripTrailing().endsWith("(no reader)│"), lines.get(3).toString());
        assertEquals(Palette.DIMMED, lines.get(3).styleAt(8), "and it is greyed out");
        assertTrue(lines.get(2).toString().startsWith("│>   1  First"), "a zip is left alone");

        press(Key.Special.ENTER);
        assertEquals(2, browser.takeSelection().orElseThrow().size(), "the disk image is not queued");
    }

    @Test
    void showsWhatIsPlayingWithASpectrumStripAboveTheKeyBar() {
        assertFalse(render().get(HEIGHT - 2).contains("♪"), "nothing playing, no now-playing line");

        browser.nowPlaying("Assembly 1995 · Music  #1 Funkyeeh by Theseus", new double[] {1.0, 0.5, 0.25, 0});

        final String nowPlaying = render().get(HEIGHT - 2);
        assertTrue(nowPlaying.contains("Funkyeeh"), nowPlaying);
        assertTrue(nowPlaying.contains("█"), "the spectrum strip shows the loud band");
    }

    @Test
    void ignoresEnterWhileLoading(@TempDir Path dir) {
        final Browser stalled = new Browser(new DemozooClient(http, new CacheDirectory(dir)), runnable -> { });
        http.put(SERIES_URL, SERIES);
        final String first = CuratedSeries.ALL.stream().sorted(CuratedSeries.BY_NAME).findFirst().orElseThrow().name();
        stalled.handle(Key.of(Key.Special.ENTER));
        stalled.handle(Key.of(Key.Special.ENTER));
        assertTrue(stalled.render(WIDTH, HEIGHT).stream().map(AttributedString::toString)
                .anyMatch(line -> line.contains("Loading " + first)), "it says which series it is fetching");
        assertEquals(0, http.requests(), "the stalled executor never ran");
    }

    private void openParty() {
        http.put(SERIES_URL, SERIES);
        http.put(PARTY_URL, PARTY);
        cursorToTheParty();
        press(Key.Special.ENTER);
        browser.tick();
        press(Key.Special.DOWN);
        press(Key.Special.ENTER);
        browser.tick();
    }

    /**
     * The series are listed by name, so the one the fake answers for is found by counting to it rather than
     * assuming it sits at the top.
     */
    private void cursorToTheParty() {
        final List<CuratedSeries> sorted = CuratedSeries.ALL.stream().sorted(CuratedSeries.BY_NAME).toList();
        for (int i = 0; i < sorted.size() && sorted.get(i).id() != THE_PARTY_SERIES; i++) {
            press(Key.Special.DOWN);
        }
    }

    private void openCompo() {
        openParty();
        press(Key.Special.ENTER);
    }

    private void press(char character) {
        final Key key = Key.of(character);
        assertTrue(browser.consumes(key), "the browser takes " + character);
        browser.handle(key);
    }

    private void press(Key.Special special) {
        final Key key = Key.of(special);
        assertTrue(browser.consumes(key));
        browser.handle(key);
    }

    private List<String> render() {
        return browser.render(WIDTH, HEIGHT).stream().map(AttributedString::toString).map(String::stripTrailing).toList();
    }
}

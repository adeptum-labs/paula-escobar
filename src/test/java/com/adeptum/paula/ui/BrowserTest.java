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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.demozoo.DemozooClient;
import com.adeptum.paula.demozoo.FakeHttp;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.Playlist;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserTest {

    private static final int WIDTH = 80;
    private static final int HEIGHT = 8;
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
                {"position":3,"ranking":"3","production":{"id":13,"title":"Stream","author_nicks":[{"name":"C"}],"types":[{"id":30}]}},
                {"position":4,"ranking":"4","production":{"id":14,"title":"Fourth","author_nicks":[{"name":"D"}],"types":[{"id":29}]}}]}]}
            """;
    private static final String EMPTY_PARTY = "{\"id\":5,\"name\":\"The Party 1995\",\"competitions\":[]}";

    private final FakeHttp http = new FakeHttp();
    private Browser browser;

    @BeforeEach
    void createBrowser(@TempDir Path dir) {
        browser = new Browser(new DemozooClient(http, new CacheDirectory(dir)), Runnable::run);
    }

    @Test
    void startsWithTheCuratedSeries() {
        final List<String> lines = render();
        assertTrue(lines.get(0).contains("Parties"));
        assertTrue(lines.contains("> The Party"));
        assertTrue(lines.contains("  Assembly"));
        assertTrue(lines.contains("  Mekka & Symposium"));
        assertTrue(browser.atRoot());
    }

    @Test
    void enterOnASeriesLoadsItsPartiesOldestFirst() {
        http.put(SERIES_URL, SERIES);
        press(Key.Special.ENTER);
        browser.tick();

        final List<String> lines = render();
        assertTrue(lines.get(0).contains("The Party"));
        assertEquals(List.of("> The Party 1994", "  The Party 1995"), lines.subList(2, 4));
        assertFalse(browser.atRoot());
    }

    @Test
    void enterOnAPartyListsMusicCompetitionsWithEntryCounts() {
        openParty();
        assertTrue(render().contains("> Multichannel Music (4)"));
    }

    @Test
    void enterOnACompetitionListsRankedEntriesAndDimsUnplayableOnes() {
        openCompo();
        final List<AttributedString> lines = browser.render(WIDTH, HEIGHT);
        assertTrue(lines.get(2).toString().startsWith(">   1  First  A"));
        assertTrue(lines.get(4).toString().startsWith("    3  Stream  C"));
        assertEquals(Theme.DIMMED, lines.get(4).styleAt(4));
        assertEquals(Theme.VALUE, lines.get(3).styleAt(4));
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
        assertEquals("Stream", ((DemozooTrack) playlist.current()).entry().title());
        assertEquals(2, playlist.size());
    }

    @Test
    void backspaceLeftAndEscapeGoUpOneLevel() {
        openParty();
        press(Key.Special.BACKSPACE);
        assertTrue(render().contains("> The Party 1995"));
        press(Key.Special.LEFT);
        assertTrue(browser.atRoot());
        assertFalse(browser.consumes(Key.of(Key.Special.ESCAPE)), "escape at the root is left to the player");
    }

    @Test
    void cursorClampsAndPagesWithinTheList() {
        openCompo();
        press(Key.Special.UP);
        assertTrue(render().get(2).startsWith(">   1"));
        press(Key.Special.END);
        assertTrue(render().stream().anyMatch(line -> line.startsWith(">   4")));
        press(Key.Special.DOWN);
        assertTrue(render().stream().anyMatch(line -> line.startsWith(">   4")));
        press(Key.Special.HOME);
        press(Key.Special.PAGE_DOWN);
        assertTrue(render().stream().anyMatch(line -> line.startsWith(">   4")), "a page is the visible rows");
        press(Key.Special.PAGE_UP);
        assertTrue(render().get(2).startsWith(">   1"));
    }

    @Test
    void scrollsTheWindowToKeepTheCursorVisible() {
        openCompo();
        final List<String> tall = browser.render(WIDTH, 6).stream().map(AttributedString::toString).toList();
        assertEquals(2, tall.stream().filter(line -> line.matches("[> ] +\\d.*")).count(), "six rows leave two for the list");
        press(Key.Special.END);
        assertTrue(browser.render(WIDTH, 6).stream().map(AttributedString::toString).anyMatch(line -> line.startsWith(">   4")));
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
        press(Key.Special.ENTER);
        browser.tick();
        assertFalse(browser.atRoot());
        assertFalse(render().stream().anyMatch(line -> line.contains("offline")));
    }

    @Test
    void showsAnEmptyLevelForPartiesWithoutMusic() {
        http.put(SERIES_URL, SERIES);
        http.put(PARTY_URL, EMPTY_PARTY);
        press(Key.Special.ENTER);
        browser.tick();
        press(Key.Special.DOWN);
        press(Key.Special.ENTER);
        browser.tick();

        assertTrue(render().contains("No music competitions"));
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
    void ignoresEnterWhileLoading(@TempDir Path dir) {
        final Browser stalled = new Browser(new DemozooClient(http, new CacheDirectory(dir)), runnable -> { });
        http.put(SERIES_URL, SERIES);
        stalled.handle(Key.of(Key.Special.ENTER));
        stalled.handle(Key.of(Key.Special.ENTER));
        assertTrue(stalled.render(WIDTH, HEIGHT).stream().map(AttributedString::toString).anyMatch(line -> line.contains("Loading The Party")));
        assertEquals(0, http.requests(), "the stalled executor never ran");
    }

    private void openParty() {
        http.put(SERIES_URL, SERIES);
        http.put(PARTY_URL, PARTY);
        press(Key.Special.ENTER);
        browser.tick();
        press(Key.Special.DOWN);
        press(Key.Special.ENTER);
        browser.tick();
    }

    private void openCompo() {
        openParty();
        press(Key.Special.ENTER);
    }

    private void press(Key.Special special) {
        final Key key = Key.of(special);
        assertTrue(browser.consumes(key));
        browser.handle(key);
    }

    private List<String> render() {
        return browser.render(WIDTH, HEIGHT).stream().map(AttributedString::toString).toList();
    }
}

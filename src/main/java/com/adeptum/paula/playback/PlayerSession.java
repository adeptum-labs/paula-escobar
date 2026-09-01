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

package com.adeptum.paula.playback;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.playlist.Playlist;
import com.adeptum.paula.playlist.Track;
import com.adeptum.paula.ui.Action;
import com.adeptum.paula.ui.Browser;
import com.adeptum.paula.ui.Key;
import com.adeptum.paula.ui.PlayerView;
import com.adeptum.paula.ui.Screen;
import com.adeptum.paula.ui.TerminalUi;

/**
 * Drives one interactive session: reacts to keys, hands tracks to the loader, plays them as they arrive and
 * switches between the player and the party browser.
 */
@Slf4j
public final class PlayerSession {

    private static final long REDRAW_INTERVAL_MILLIS = 100;
    private static final long DRAIN_TIMEOUT_MILLIS = 1;
    private static final Duration SEEK_STEP = Duration.ofSeconds(5);
    private static final String LOADING = "Loading ";
    private static final String NOTHING_LOADED = "None of the playlist entries could be loaded, see paula.log";

    private final ModuleLoaderRegistry loaders;
    private final PlaybackEngine engine;
    private final TerminalUi ui;
    private final TrackLoader loader;
    private final TrackLoader.Resolver resolver;
    private final Browser browser;
    private final boolean exitWhenDone;
    private Playlist playlist;
    private boolean browsing;
    private boolean everBrowsed;
    private Module module;
    private String status;
    private boolean playedAnything;

    /**
     * A session started without a playlist opens on the browser; one started with files behaves like a plain
     * player and exits when the last file ends, unless the browser was opened along the way.
     */
    public PlayerSession(Optional<Playlist> playlist, ModuleLoaderRegistry loaders, PlaybackEngine engine, TerminalUi ui,
            TrackLoader loader, TrackLoader.Resolver resolver, Browser browser) {
        this.playlist = playlist.orElse(null);
        this.exitWhenDone = playlist.isPresent();
        this.browsing = playlist.isEmpty();
        this.loaders = loaders;
        this.engine = engine;
        this.ui = ui;
        this.loader = loader;
        this.resolver = resolver;
        this.browser = browser;
    }

    public void run() throws IOException {
        if (playlist != null) {
            requestCurrent();
        }
        while (true) {
            Key key = ui.poll(REDRAW_INTERVAL_MILLIS);
            while (!key.is(Key.Special.TIMEOUT)) {
                if (browsing && browser.consumes(key)) {
                    browser.handle(key);
                } else if (!handle(Action.of(key))) {
                    return;
                }
                key = ui.poll(DRAIN_TIMEOUT_MILLIS);
            }
            browser.tick();
            browser.takeSelection().ifPresent(this::startPlaylist);
            final Optional<TrackLoader.Result> result = loader.poll();
            if (result.isPresent() && !apply(result.get())) {
                return;
            }
            if (finished() && !advance(true) && !returnToBrowser()) {
                return;
            }
            ui.draw(browsing ? browser.render(ui.width(), ui.height()) : Screen.render(view(), ui.width(), ui.height()));
        }
    }

    private boolean handle(Action action) {
        switch (action) {
            case QUIT -> {
                return false;
            }
            case TOGGLE_PAUSE -> engine.togglePause();
            case NEXT -> advance(true);
            case PREVIOUS -> advance(false);
            case SEEK_FORWARD -> engine.seek(SEEK_STEP);
            case SEEK_BACKWARD -> engine.seek(SEEK_STEP.negated());
            case BROWSE -> {
                browsing = !browsing;
                everBrowsed = true;
            }
            case NONE -> {
            }
        }
        return true;
    }

    private void startPlaylist(Playlist selected) {
        playlist = selected;
        browsing = false;
        requestCurrent();
    }

    /**
     * Stopping first keeps a finished song from advancing the playlist again while the next one downloads.
     */
    private void requestCurrent() {
        engine.stop();
        status = LOADING + playlist.current().label();
        loader.request(playlist.current(), resolver);
    }

    private boolean finished() {
        return playlist != null && engine.state() == PlaybackState.FINISHED && !loader.loading();
    }

    /**
     * Returns false once the playlist is exhausted and there is nowhere to go.
     */
    private boolean apply(TrackLoader.Result result) throws IOException {
        return switch (result) {
            case TrackLoader.Loaded loaded -> play(loaded);
            case TrackLoader.Failed failed -> skip(failed.track(), failed.error());
        };
    }

    private boolean play(TrackLoader.Loaded loaded) throws IOException {
        try {
            module = loaders.load(loaded.path());
        } catch (IOException e) {
            return skip(loaded.track(), e);
        }
        engine.play(module.createRenderer(engine.sampleRate()));
        status = null;
        playedAnything = true;
        return true;
    }

    private boolean skip(Track track, IOException error) throws IOException {
        log.warn("Skipping {}: {}", track.label(), error.getMessage());
        status = error.getMessage();
        if (advance(true) || returnToBrowser()) {
            return true;
        }
        if (!playedAnything) {
            throw new IOException(NOTHING_LOADED);
        }
        return false;
    }

    private boolean advance(boolean forward) {
        if (playlist == null) {
            return false;
        }
        final boolean moved = forward ? playlist.next() : playlist.previous();
        if (moved) {
            requestCurrent();
        }
        return moved;
    }

    private boolean returnToBrowser() {
        if (exitWhenDone && !everBrowsed) {
            return false;
        }
        engine.stop();
        browsing = true;
        return true;
    }

    private PlayerView view() {
        return PlayerView.builder()
                .module(module)
                .trackLabel(playlist == null ? null : playlist.current().label())
                .state(engine.state())
                .position(engine.position())
                .track(playlist == null ? 0 : playlist.position())
                .trackCount(playlist == null ? 0 : playlist.size())
                .status(status)
                .build();
    }
}

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
import com.adeptum.paula.ui.Key;
import com.adeptum.paula.ui.PlayerView;
import com.adeptum.paula.ui.Screen;
import com.adeptum.paula.ui.TerminalUi;

/**
 * Drives one interactive session: reacts to keys, hands tracks to the loader and plays them as they arrive.
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
    private final Playlist playlist;
    private Module module;
    private String status;
    private boolean playedAnything;

    public PlayerSession(Playlist playlist, ModuleLoaderRegistry loaders, PlaybackEngine engine, TerminalUi ui,
            TrackLoader loader, TrackLoader.Resolver resolver) {
        this.playlist = playlist;
        this.loaders = loaders;
        this.engine = engine;
        this.ui = ui;
        this.loader = loader;
        this.resolver = resolver;
    }

    public void run() throws IOException {
        requestCurrent();
        while (true) {
            Key key = ui.poll(REDRAW_INTERVAL_MILLIS);
            while (!key.is(Key.Special.TIMEOUT)) {
                if (!handle(Action.of(key))) {
                    return;
                }
                key = ui.poll(DRAIN_TIMEOUT_MILLIS);
            }
            final Optional<TrackLoader.Result> result = loader.poll();
            if (result.isPresent() && !apply(result.get())) {
                return;
            }
            if (engine.state() == PlaybackState.FINISHED && !loader.loading() && !advance(true)) {
                return;
            }
            ui.draw(Screen.render(view(), ui.width(), ui.height()));
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
            case NONE -> {
            }
        }
        return true;
    }

    /**
     * Stopping first keeps a finished song from advancing the playlist again while the next one downloads.
     */
    private void requestCurrent() {
        engine.stop();
        status = LOADING + playlist.current().label();
        loader.request(playlist.current(), resolver);
    }

    /**
     * Returns false once the playlist is exhausted.
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
        if (advance(true)) {
            return true;
        }
        if (!playedAnything) {
            throw new IOException(NOTHING_LOADED);
        }
        return false;
    }

    private boolean advance(boolean forward) {
        final boolean moved = forward ? playlist.next() : playlist.previous();
        if (moved) {
            requestCurrent();
        }
        return moved;
    }

    private PlayerView view() {
        return PlayerView.builder()
                .module(module)
                .trackLabel(playlist.current().label())
                .state(engine.state())
                .position(engine.position())
                .track(playlist.position())
                .trackCount(playlist.size())
                .status(status)
                .build();
    }
}

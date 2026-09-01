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
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.playlist.Playlist;
import com.adeptum.paula.ui.Action;
import com.adeptum.paula.ui.PlayerView;
import com.adeptum.paula.ui.TerminalUi;

/**
 * Drives one interactive session: loads playlist entries, reacts to keys and advances when a song ends.
 */
@Slf4j
public final class PlayerSession {

    private static final long REDRAW_INTERVAL_MILLIS = 100;

    private final Playlist playlist;
    private final ModuleLoaderRegistry loaders;
    private final PlaybackEngine engine;
    private final TerminalUi ui;
    private Module module;

    public PlayerSession(Playlist playlist, ModuleLoaderRegistry loaders, PlaybackEngine engine, TerminalUi ui) {
        this.playlist = playlist;
        this.loaders = loaders;
        this.engine = engine;
        this.ui = ui;
    }

    public void run() throws IOException {
        if (!loadCurrentOrSkip(true)) {
            throw new IOException("None of the playlist entries could be loaded, see paula.log");
        }
        while (handle(ui.poll(REDRAW_INTERVAL_MILLIS))) {
            if (engine.state() == PlaybackState.FINISHED && !advance(true)) {
                return;
            }
            ui.draw(view());
        }
    }

    private boolean handle(Action action) throws IOException {
        return switch (action) {
            case QUIT -> false;
            case TOGGLE_PAUSE -> {
                engine.togglePause();
                yield true;
            }
            case NEXT -> {
                advance(true);
                yield true;
            }
            case PREVIOUS -> {
                advance(false);
                yield true;
            }
            case NONE -> true;
        };
    }

    private boolean advance(boolean forward) throws IOException {
        final boolean moved = forward ? playlist.next() : playlist.previous();
        return moved && loadCurrentOrSkip(forward);
    }

    private boolean loadCurrentOrSkip(boolean forward) throws IOException {
        while (!loadCurrent()) {
            final boolean moved = forward ? playlist.next() : playlist.previous();
            if (!moved) {
                return false;
            }
        }
        return true;
    }

    private boolean loadCurrent() throws IOException {
        final Path path = playlist.current();
        try {
            module = loaders.load(path);
        } catch (IOException e) {
            log.warn("Skipping {}: {}", path, e.getMessage());
            return false;
        }
        engine.play(module.createRenderer(engine.sampleRate()));
        ui.draw(view());
        return true;
    }

    private PlayerView view() {
        return PlayerView.builder()
                .module(module)
                .state(engine.state())
                .position(engine.position())
                .track(playlist.position())
                .trackCount(playlist.size())
                .build();
    }
}

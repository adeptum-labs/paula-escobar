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

package com.adeptum.paula.playback;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import com.adeptum.paula.audio.AudioException;
import com.adeptum.paula.cast.CastDevice;
import com.adeptum.paula.cast.CastDiscovery;
import com.adeptum.paula.cast.CastSink;
import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.playlist.Playlist;
import com.adeptum.paula.playlist.Track;
import com.adeptum.paula.ui.Action;
import com.adeptum.paula.ui.Browser;
import com.adeptum.paula.ui.CastPopup;
import com.adeptum.paula.ui.Key;
import com.adeptum.paula.ui.Mouse;
import com.adeptum.paula.ui.PlayerView;
import com.adeptum.paula.ui.Screen;
import com.adeptum.paula.ui.Shortcuts;
import com.adeptum.paula.ui.TerminalUi;
import com.adeptum.paula.ui.Visual;
import com.adeptum.paula.ui.visual.Waterfall;
import com.adeptum.paula.ui.visual.Spectrum;
import com.adeptum.paula.ui.visual.Vu;
import org.jline.utils.AttributedString;

/**
 * Drives one interactive session: reacts to keys, hands tracks to the loader, plays them as they arrive and
 * switches between the player and the party browser.
 */
@Slf4j
public final class PlayerSession {

    private static final long REDRAW_INTERVAL_MILLIS = 33;
    private static final char KEYS = '?';
    private static final long DRAIN_TIMEOUT_MILLIS = 1;
    private static final int SPECTRUM_BANDS = 32;
    private static final int ANALYSIS_FRAMES = 2048;
    private static final int SCOPE_FRAMES = 256;
    private static final int VECTOR_FRAMES = 1024;
    private static final int WATERFALL_DEPTH = 64;
    private static final Duration SEEK_STEP = Duration.ofSeconds(5);
    private static final String LOADING = "Loading ";
    private static final int DELAY_SECONDS = 32;
    private static final String CASTING = "Casting to ";
    private static final String NOTHING_LOADED = "None of the playlist entries could be loaded, see paula.log";
    private static final short[] NO_AUDIO = new short[0];

    private final ModuleLoaderRegistry loaders;
    private final PlaybackEngine engine;
    private final TerminalUi ui;
    private final TrackLoader loader;
    private final TrackLoader.Resolver resolver;
    private final Browser browser;
    private final Deadline deadline;
    private boolean showingKeys;
    private final boolean exitWhenDone;
    private final Spectrum spectrum;
    private final Vu vu = new Vu();
    private final ChannelMuting muting = new ChannelMuting();
    private final Waterfall waterfall = new Waterfall(SPECTRUM_BANDS, WATERFALL_DEPTH);
    private Visual visual = Visual.SPECTRUM;
    private Playlist playlist;
    private boolean browsing;
    private boolean everBrowsed;
    private Module module;
    private Renderer renderer;
    private String status;
    private boolean playedAnything;
    private final CastDiscovery discovery;
    private final Outputs outputs;
    private final CastPopup castPopup = new CastPopup();
    private final PlaybackDelay delay;
    private PlaybackDelay.Moment heard;

    /**
     * A session started without a playlist opens on the browser; one started with files behaves like a plain
     * player and exits when the last file ends, unless the browser was opened along the way.
     */
    public PlayerSession(Optional<Playlist> playlist, ModuleLoaderRegistry loaders, PlaybackEngine engine, TerminalUi ui,
            TrackLoader loader, TrackLoader.Resolver resolver, Browser browser, CastDiscovery discovery,
            Outputs outputs, Deadline deadline) {
        this.playlist = playlist.orElse(null);
        this.exitWhenDone = playlist.isPresent();
        this.browsing = playlist.isEmpty();
        this.loaders = loaders;
        this.engine = engine;
        this.ui = ui;
        this.loader = loader;
        this.resolver = resolver;
        this.browser = browser;
        this.discovery = discovery;
        this.outputs = outputs;
        this.deadline = deadline;
        this.delay = new PlaybackDelay((long) engine.sampleRate() * DELAY_SECONDS);
        this.spectrum = new Spectrum(SPECTRUM_BANDS, engine.sampleRate());
    }

    public void run() throws IOException {
        if (playlist != null) {
            requestCurrent();
        }
        while (!deadline.passed()) {
            Key key = ui.poll(REDRAW_INTERVAL_MILLIS);
            while (!key.is(Key.Special.TIMEOUT)) {
                if (castPopup.isOpen() && !browsing) {
                    castPopup.handle(key).ifPresent(this::chose);
                } else if (showingKeys) {
                    showingKeys = false;
                } else if (key.mouse() != null) {
                    click(key.mouse());
                } else if (key.character() == KEYS) {
                    showingKeys = true;
                } else if (browsing && browser.consumes(key)) {
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
            final short[] audio = engine.state() == PlaybackState.PLAYING ? heardAudio() : new short[ANALYSIS_FRAMES * 2];
            spectrum.feed(audio);
            vu.feed(audio);
            waterfall.feed(spectrum.levels());
            browser.nowPlaying(module == null || playlist == null ? null : playlist.current(), spectrum.levels());
            ui.draw(withKeys(browsing ? browser.render(ui.width(), ui.height()) : player(audio)));
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
            case CYCLE_VISUAL -> visual = visual.next();
            case BROWSE -> {
                browsing = !browsing;
                everBrowsed = true;
            }
            case CAST -> {
                castPopup.open();
                if (discovery.devices().isEmpty() && !discovery.isScanning()) {
                    discovery.scan();
                }
            }
            case NONE -> {
            }
        }
        return true;
    }

    /**
     * A click lands on the screen as it was last drawn, which is the one the loop is about to draw again.
     */
    private void click(Mouse mouse) {
        if (browsing || renderer == null) {
            return;
        }
        final PlayerView shown = view(NO_AUDIO);
        if (Screen.onVisual(shown, ui.width(), ui.height(), mouse.column(), mouse.row())) {
            visual = visual.next();
            return;
        }
        Screen.channelAt(shown, ui.width(), ui.height(), mouse.column(), mouse.row())
                .ifPresent(channel -> muting.click(renderer, channel, mouse.shift()));
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
        module = null;
        loader.request(playlist.current(), resolver);
    }

    /**
     * The keys of the screen in view are laid over it while they are asked for, so they can be read without
     * losing sight of what is playing.
     */
    private List<AttributedString> withKeys(List<AttributedString> screen) {
        return showingKeys
                ? Shortcuts.over(screen, browsing ? Browser.keys() : Screen.keys(), ui.width(), ui.height())
                : screen;
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

    /**
     * What a screen beside the sound says the song is: the tracker or format it is in and how many channels.
     */
    private static String describe(Module module) {
        final String format = module.metadata().format().name();
        return module.metadata().channels() > 0 ? format + ", " + module.metadata().channels() + " channels" : format;
    }

    private boolean play(TrackLoader.Loaded loaded) throws IOException {
        try {
            module = loaders.load(loaded.path());
            renderer = module.createRenderer(engine.sampleRate());
            engine.play(renderer, module.metadata().title(), describe(module));
        } catch (IOException e) {
            return skip(loaded.track(), e);
        } catch (AudioException e) {
            log.error("The output would not take the song", e);
            return skip(loaded.track(), new IOException(e.getMessage(), e));
        } catch (RuntimeException e) {
            log.error("The decoder failed to start", e);
            return skip(loaded.track(), new IOException("Decoder failed: " + e, e));
        }
        status = null;
        playedAnything = true;
        return true;
    }

    /**
     * The player screen, with the cast panel over it while that is up.
     */
    private List<AttributedString> player(short[] audio) {
        final List<AttributedString> screen = Screen.render(view(audio), ui.width(), ui.height());
        return castPopup.isOpen()
                ? castPopup.draw(screen, discovery.devices(), discovery.isScanning(), castingTo(), ui.width(), ui.height())
                : screen;
    }

    /**
     * The sound being heard, which on a device that plays late is what was written some seconds ago; the
     * scopes and the position are looked up for the same moment.
     */
    private short[] heardAudio() {
        final long written = engine.tap().written();
        if (module != null && renderer != null) {
            delay.record(written, renderer.channels(), engine.position());
        }
        final long heardAt = written - lagFrames();
        heard = castingTo() == null ? null : delay.at(heardAt);
        return engine.tap().snapshot(ANALYSIS_FRAMES, heardAt);
    }

    private long lagFrames() {
        return engine.output() instanceof CastSink cast
                ? cast.lag().map(lag -> lag.toMillis() * engine.sampleRate() / 1000).orElse(0L) : 0;
    }

    private void chose(CastPopup.Choice choice) {
        try {
            switch (choice) {
                case CastPopup.Local local -> playHere();
                case CastPopup.Device device -> playOn(device.device());
                case CastPopup.Rescan rescan -> discovery.scan();
            }
        } catch (AudioException e) {
            log.warn("Moving the sound failed", e);
            status = e.getMessage();
            castPopup.close();
        }
    }

    /**
     * The device the sound is going to, which is the output itself rather than anything remembered beside
     * it, so casting asked for on the command line counts the same as casting chosen from the popup.
     */
    private CastDevice castingTo() {
        return engine.output() instanceof CastSink cast ? cast.device() : null;
    }

    private void playHere() throws AudioException {
        if (castingTo() != null) {
            engine.switchOutput(outputs.local().open());
            delay.clear();
        }
        castPopup.close();
    }

    private void playOn(CastDevice device) throws AudioException {
        if (!device.equals(castingTo())) {
            engine.switchOutput(outputs.cast().open(device));
            delay.clear();
            status = null;
        }
        castPopup.close();
    }

    /**
     * The failure stays on the status line while the next track loads, and follows the user into the browser.
     */
    private boolean skip(Track track, IOException error) throws IOException {
        log.warn("Skipping {}: {}", track.label(), error.getMessage());
        status = error.getMessage();
        if (advance(true)) {
            return true;
        }
        if (returnToBrowser()) {
            browser.report(status);
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

    private PlayerView view(short[] audio) {
        final boolean sounding = module != null && renderer != null;
        return PlayerView.builder()
                .module(module)
                .trackLabel(playlist == null ? null : playlist.current().label())
                .state(engine.state())
                .position(heard == null ? engine.position() : heard.position())
                .length(sounding ? renderer.length().orElse(null) : null)
                .track(playlist == null ? 0 : playlist.position())
                .trackCount(playlist == null ? 0 : playlist.size())
                .status(statusLine())
                .spectrum(spectrum.levels())
                .peaks(spectrum.peaks())
                .vuLeft(vu.left())
                .vuRight(vu.right())
                .channels(sounding ? heard == null ? renderer.channels() : heard.channels() : List.of())
                .mixed(mono(audio, SCOPE_FRAMES))
                .stereo(stereo(audio, VECTOR_FRAMES))
                .progress(loader.loading() ? loader.progress().step().orElse(null) : null)
                .visual(visual)
                .waterfall(waterfall)
                .canCast(castingTo() != null || !discovery.devices().isEmpty())
                .build();
    }

    /**
     * The newest frames as they were mixed, both channels kept apart, which is what a scope plotting one
     * against the other needs.
     */
    private static double[] stereo(short[] interleaved, int frames) {
        final int available = interleaved.length / 2;
        final int used = Math.min(frames, available);
        final double[] both = new double[used * 2];
        for (int i = 0; i < used; i++) {
            final int frame = available - used + i;
            both[i * 2] = (double) interleaved[frame * 2] / Short.MAX_VALUE;
            both[i * 2 + 1] = (double) interleaved[frame * 2 + 1] / Short.MAX_VALUE;
        }
        return both;
    }

    private static double[] mono(short[] interleaved, int frames) {
        final int available = interleaved.length / 2;
        final int used = Math.min(frames, available);
        final double[] mono = new double[used];
        for (int i = 0; i < used; i++) {
            final int frame = available - used + i;
            mono[i] = (interleaved[frame * 2] + interleaved[frame * 2 + 1]) / (2.0 * Short.MAX_VALUE);
        }
        return mono;
    }

    private String statusLine() {
        if (status != null) {
            return status;
        }
        if (engine.output() instanceof CastSink cast) {
            return CASTING + cast.device().name()
                    + cast.lag().map(lag -> String.format(", %.1f s behind", lag.toMillis() / 1000.0)).orElse("");
        }
        if (!loader.loading() || loader.progress().step().isPresent()) {
            return null;
        }
        return LOADING + playlist.current().label();
    }
}

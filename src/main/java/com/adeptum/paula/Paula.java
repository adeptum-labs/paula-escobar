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

package com.adeptum.paula;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;
import com.adeptum.paula.audio.AudioBackend;
import com.adeptum.paula.audio.AudioException;
import com.adeptum.paula.audio.AudioSink;
import com.adeptum.paula.audio.WaveRecorder;
import com.adeptum.paula.cast.CastDevice;
import com.adeptum.paula.cast.CastDiscovery;
import com.adeptum.paula.cast.CastSink;
import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.cli.BuildInfo;
import com.adeptum.paula.cli.FormatsCommand;
import com.adeptum.paula.cli.InfoCommand;
import com.adeptum.paula.demozoo.DemozooClient;
import com.adeptum.paula.demozoo.HttpFetcher;
import com.adeptum.paula.demozoo.JdkHttpFetcher;
import com.adeptum.paula.demozoo.CachedReleaseArt;
import com.adeptum.paula.demozoo.PartyArt;
import com.adeptum.paula.demozoo.ReleaseArt;
import com.adeptum.paula.demozoo.FetchingReleaseArt;
import com.adeptum.paula.demozoo.SceneOrgPartyArt;
import com.adeptum.paula.demozoo.TrackResolver;
import com.adeptum.paula.modarchive.ModArchiveClient;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.sid.SidLoader;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.playback.DaemonExecutors;
import com.adeptum.paula.playback.Deadline;
import com.adeptum.paula.playback.Outputs;
import com.adeptum.paula.playback.PlaybackEngine;
import com.adeptum.paula.playback.PlayerSession;
import com.adeptum.paula.playback.TrackLoader;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.LocalTrack;
import com.adeptum.paula.playlist.ModArchiveTrack;
import com.adeptum.paula.playlist.MusicianTrack;
import com.adeptum.paula.playlist.Playlist;
import com.adeptum.paula.playlist.Track;
import com.adeptum.paula.ui.Browser;
import com.adeptum.paula.ui.TerminalUi;
import com.adeptum.paula.ui.Theme;

@Slf4j
@Command(name = "paula",
        mixinStandardHelpOptions = true,
        versionProvider = BuildInfo.class,
        description = "Paula Escobar, a terminal music player for demoscene and chip music. Without files it opens the party browser.",
        subcommands = {InfoCommand.class, FormatsCommand.class})
public final class Paula implements Runnable {

    private static final Duration CAST_LOOKUP = Duration.ofSeconds(6);
    private static final Duration CAST_LOOKUP_STEP = Duration.ofMillis(100);
    private static final String BROWSER_THREAD = "paula-browser";
    private static final String ART_THREAD = "paula-art";

    @Spec
    private CommandSpec spec;

    @Option(names = {"-r", "--rate"}, paramLabel = "HZ", defaultValue = "48000", description = "Output sample rate (default: ${DEFAULT-VALUE}).")
    private int sampleRate;

    @Option(names = {"-b", "--buffer"}, paramLabel = "FRAMES", defaultValue = "2048", description = "Audio buffer size in frames (default: ${DEFAULT-VALUE}).")
    private int bufferFrames;

    @Option(names = {"-o", "--output"}, paramLabel = "BACKEND", defaultValue = "auto", description = "Audio backend: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private AudioBackend output;

    @Option(names = "--cast", paramLabel = "DEVICE", description = "Play on the Cast device of this name or address; implies --output cast.")
    private String cast;

    @Option(names = "--cast-port", paramLabel = "PORT", defaultValue = "7373", description = "Port the sound is served on for a Cast device to fetch (default: ${DEFAULT-VALUE}).")
    private int castPort;

    @Option(names = "--cast-lag", paramLabel = "SECONDS", defaultValue = "4", description = "How far behind a Cast device is left to run (default: ${DEFAULT-VALUE}); less than the device buffers of its own makes it stutter.")
    private double castLag;

    @Option(names = "--quit-after", paramLabel = "SECONDS", description = "Stop after this many seconds; no terminal is needed then.")
    private Integer quitAfterSeconds;

    @Option(names = "--record", paramLabel = "FILE", description = "Keep a copy of everything played in this wave file.")
    private Path record;

    @Parameters(paramLabel = "FILE", arity = "0..*", description = "Module files to play, in order.")
    private List<Path> files = List.of();

    public static void main(String[] args) {
        AnsiConsole.systemInstall();
        try {
            System.exit(commandLine().execute(args));
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    public static CommandLine commandLine() {
        return new CommandLine(new Paula())
                .setColorScheme(Theme.helpColors())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler(Paula::printError);
    }

    private static int printError(Exception error, CommandLine cmd, ParseResult parseResult) {
        cmd.getErr().println(cmd.getColorScheme().errorText("Error: " + error.getMessage()));
        return cmd.getCommandSpec().exitCodeOnExecutionException();
    }

    @Override
    public void run() {
        final Optional<Playlist> playlist = files.isEmpty() ? Optional.empty() : Optional.of(new Playlist(localTracks()));
        final TerminalUi ui;
        try {
            ui = new TerminalUi(quitAfterSeconds != null);
        } catch (IOException | IllegalStateException e) {
            if (playlist.isEmpty()) {
                spec.commandLine().usage(spec.commandLine().getOut());
                return;
            }
            throw new ExecutionException(spec.commandLine(), "Paula Escobar needs a terminal: " + e.getMessage(), e);
        }
        final ExecutorService browsing = DaemonExecutors.singleThread(BROWSER_THREAD);
        final ExecutorService fetchingArt = DaemonExecutors.singleThread(ART_THREAD);
        try (ui;
                CastDiscovery discovery = CastDiscovery.start();
                PlaybackEngine engine = new PlaybackEngine(outputSink(discovery), copies(), sampleRate, bufferFrames);
                TrackLoader loader = TrackLoader.background()) {
            final CacheDirectory cache = CacheDirectory.resolve();
            final HttpFetcher http = JdkHttpFetcher.paula();
            final DemozooClient demozoo = new DemozooClient(http, cache);
            final SongLengths sidLengths = new SongLengths(http, cache);
            final ModuleLoaderRegistry loaders = ModuleLoaderRegistry.withBuiltInLoaders(sidLengths);
            final TrackResolver resolver = new TrackResolver(demozoo, http, cache, loaders, loader.progress());
            // Art is fetched behind the browser's back and must not write over what the player is waiting for.
            final TrackResolver artResolver = new TrackResolver(demozoo, http, cache, loaders);
            final CachedReleaseArt releaseArt = new CachedReleaseArt(cache);
            final SceneOrgPartyArt partyArt = new SceneOrgPartyArt(demozoo, http, cache, fetchingArt);
            final Browser browser = new Browser(demozoo, new ModArchiveClient(http, cache), loaders, browsing,
                    new FetchingReleaseArt(releaseArt, artResolver, fetchingArt), partyArt);
            new PlayerSession(playlist, loaders, engine, ui, loader,
                    tracks(resolver, loaders, sidLengths, demozoo, releaseArt, partyArt),
                    browser, discovery, outputs(), deadline()).run();
        } catch (AudioException | IOException e) {
            throw new ExecutionException(spec.commandLine(), e.getMessage(), e);
        } finally {
            browsing.shutdownNow();
            fetchingArt.shutdownNow();
        }
    }

    /**
     * What the sound can be moved to from the player: this machine, on the backend asked for or the usual one
     * when casting was, and any device the popup offers.
     */
    private Outputs outputs() {
        final AudioBackend here = output == AudioBackend.CAST ? AudioBackend.AUTO : output;
        return new Outputs(() -> here.createSink(bufferFrames), device -> new CastSink(device, castPort, castLag()));
    }

    private AudioSink outputSink(CastDiscovery discovery) throws AudioException {
        if (output != AudioBackend.CAST && cast == null) {
            return output.createSink(bufferFrames);
        }
        return new CastSink(castDevice(discovery), castPort, castLag());
    }

    private Duration castLag() {
        return Duration.ofMillis(Math.round(castLag * 1000));
    }

    /**
     * The device asked for by name or address, or the first to answer when none was named, waited for while
     * the network is asked.
     */
    private CastDevice castDevice(CastDiscovery discovery) throws AudioException {
        final long until = System.currentTimeMillis() + CAST_LOOKUP.toMillis();
        while (System.currentTimeMillis() < until) {
            final Optional<CastDevice> found = cast == null
                    ? discovery.devices().stream().findFirst() : discovery.find(cast);
            if (found.isPresent()) {
                return found.get();
            }
            if (!discovery.isScanning()) {
                discovery.scan();
            }
            try {
                Thread.sleep(CAST_LOOKUP_STEP.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        final String answered = discovery.devices().stream().map(CastDevice::name).collect(Collectors.joining(", "));
        throw new AudioException(cast == null
                ? "No Cast device answered on the network"
                : "No Cast device called " + cast + " answered" + (answered.isEmpty() ? "" : "; found " + answered), null);
    }

    /**
     * Everything played goes to the output and, when asked for, into a wave file beside it.
     */
    private List<AudioSink> copies() {
        return record == null ? List.of() : List.of(new WaveRecorder(record));
    }

    private List<Track> localTracks() {
        return files.stream().<Track>map(LocalTrack::new).toList();
    }

    private Deadline deadline() {
        return quitAfterSeconds == null ? Deadline.never() : Deadline.after(Duration.ofSeconds(quitAfterSeconds));
    }

    /**
     * Runs on the loader thread, so the song length database is read there before a SID reaches the player.
     */
    /**
     * How the loader brings a track down and finds a picture of the release to go with it.
     */
    private static TrackLoader.Resolver tracks(TrackResolver resolver, ModuleLoaderRegistry loaders,
            SongLengths sidLengths, DemozooClient demozoo, ReleaseArt art, PartyArt parties) {
        return new TrackLoader.Resolver() {

            @Override
            public Path resolve(Track track) throws IOException {
                return Paula.resolve(track, resolver, loaders, sidLengths);
            }

            @Override
            public Optional<String> pictureOf(Track track) {
                return picture(track, demozoo);
            }

            @Override
            public List<String> artOf(Track track) {
                final List<String> release = art.of(productionOf(track)).orElse(List.of());
                return release.isEmpty() ? partyLogo(track, parties) : release;
            }
        };
    }

    /**
     * What Demozoo holds a picture of, which for music is seldom anything; the release is already cached by
     * the time it is asked for, so this costs nothing on a track that has been played before.
     */
    private static Optional<String> picture(Track track, DemozooClient demozoo) {
        final int production = productionOf(track);
        try {
            return production == 0 ? Optional.empty() : demozoo.production(production).pictured();
        } catch (IOException e) {
            log.debug("Looking for a picture of production {}", production, e);
            return Optional.empty();
        }
    }

    /**
     * The party's own logo, which is what a competition entry packed without art of its own can still show;
     * most of them were, and the logo is already there for the browser.
     */
    private static List<String> partyLogo(Track track, PartyArt parties) {
        return track instanceof DemozooTrack entry ? parties.of(entry.party().id()).orElse(List.of()) : List.of();
    }

    /**
     * The release a track came from, or nothing for one that came from somewhere Demozoo does not number.
     */
    private static int productionOf(Track track) {
        return switch (track) {
            case DemozooTrack remote -> remote.entry().productionId();
            case MusicianTrack work -> work.work().entry().productionId();
            case LocalTrack ignored -> 0;
            case ModArchiveTrack ignored -> 0;
        };
    }

    private static Path resolve(Track track, TrackResolver resolver, ModuleLoaderRegistry loaders, SongLengths sidLengths) throws IOException {
        final Path path = switch (track) {
            case LocalTrack local -> local.path();
            case DemozooTrack remote -> resolver.resolve(remote.entry());
            case ModArchiveTrack module -> resolver.resolve(module.entry().moduleId(), module.entry().title(),
                    module.entry().fileName());
            case MusicianTrack work -> resolver.resolve(work.work().entry());
        };
        loaders.loaderFor(path).filter(SidLoader.class::isInstance).ifPresent(sid -> sidLengths.prime());
        return path;
    }
}

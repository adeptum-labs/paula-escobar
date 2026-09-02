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

package com.adeptum.paula;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
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
import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.cli.BuildInfo;
import com.adeptum.paula.cli.FormatsCommand;
import com.adeptum.paula.cli.InfoCommand;
import com.adeptum.paula.demozoo.DemozooClient;
import com.adeptum.paula.demozoo.HttpFetcher;
import com.adeptum.paula.demozoo.JdkHttpFetcher;
import com.adeptum.paula.demozoo.CachedReleaseArt;
import com.adeptum.paula.demozoo.TrackResolver;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.sid.SidLoader;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.playback.DaemonExecutors;
import com.adeptum.paula.playback.PlaybackEngine;
import com.adeptum.paula.playback.PlayerSession;
import com.adeptum.paula.playback.TrackLoader;
import com.adeptum.paula.playlist.DemozooTrack;
import com.adeptum.paula.playlist.LocalTrack;
import com.adeptum.paula.playlist.Playlist;
import com.adeptum.paula.playlist.Track;
import com.adeptum.paula.ui.Browser;
import com.adeptum.paula.ui.TerminalUi;
import com.adeptum.paula.ui.Theme;

@Command(name = "paula",
        mixinStandardHelpOptions = true,
        versionProvider = BuildInfo.class,
        description = "Paula Escobar, a terminal music player for demoscene and chip music. Without files it opens the party browser.",
        subcommands = {InfoCommand.class, FormatsCommand.class})
public final class Paula implements Runnable {

    private static final String BROWSER_THREAD = "paula-browser";

    @Spec
    private CommandSpec spec;

    @Option(names = {"-r", "--rate"}, paramLabel = "HZ", defaultValue = "48000", description = "Output sample rate (default: ${DEFAULT-VALUE}).")
    private int sampleRate;

    @Option(names = {"-b", "--buffer"}, paramLabel = "FRAMES", defaultValue = "2048", description = "Audio buffer size in frames (default: ${DEFAULT-VALUE}).")
    private int bufferFrames;

    @Option(names = {"-o", "--output"}, paramLabel = "BACKEND", defaultValue = "auto", description = "Audio backend: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private AudioBackend output;

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
            ui = new TerminalUi();
        } catch (IOException | IllegalStateException e) {
            if (playlist.isEmpty()) {
                spec.commandLine().usage(spec.commandLine().getOut());
                return;
            }
            throw new ExecutionException(spec.commandLine(), "Paula Escobar needs a terminal: " + e.getMessage(), e);
        }
        final ExecutorService browsing = DaemonExecutors.singleThread(BROWSER_THREAD);
        try (ui;
                PlaybackEngine engine = new PlaybackEngine(output.createSink(), sampleRate, bufferFrames);
                TrackLoader loader = TrackLoader.background()) {
            final CacheDirectory cache = CacheDirectory.resolve();
            final HttpFetcher http = JdkHttpFetcher.paula();
            final DemozooClient demozoo = new DemozooClient(http, cache);
            final SongLengths sidLengths = new SongLengths(http, cache);
            final ModuleLoaderRegistry loaders = ModuleLoaderRegistry.withBuiltInLoaders(sidLengths);
            final TrackResolver resolver = new TrackResolver(demozoo, http, cache, loaders);
            final Browser browser = new Browser(demozoo, browsing, new CachedReleaseArt(cache));
            new PlayerSession(playlist, loaders, engine, ui, loader, track -> resolve(track, resolver, loaders, sidLengths), browser).run();
        } catch (AudioException | IOException e) {
            throw new ExecutionException(spec.commandLine(), e.getMessage(), e);
        } finally {
            browsing.shutdownNow();
        }
    }

    private List<Track> localTracks() {
        return files.stream().<Track>map(LocalTrack::new).toList();
    }

    /**
     * Runs on the loader thread, so the song length database is read there before a SID reaches the player.
     */
    private static Path resolve(Track track, TrackResolver resolver, ModuleLoaderRegistry loaders, SongLengths sidLengths) throws IOException {
        final Path path = switch (track) {
            case LocalTrack local -> local.path();
            case DemozooTrack remote -> resolver.resolve(remote.entry());
        };
        loaders.loaderFor(path).filter(SidLoader.class::isInstance).ifPresent(sid -> sidLengths.prime());
        return path;
    }
}

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
import java.time.Duration;
import java.util.List;
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
import com.adeptum.paula.cli.BuildInfo;
import com.adeptum.paula.cli.FormatsCommand;
import com.adeptum.paula.cli.InfoCommand;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.playback.PlaybackEngine;
import com.adeptum.paula.playback.PlayerSession;
import com.adeptum.paula.playback.SilenceRenderer;
import com.adeptum.paula.playlist.Playlist;
import com.adeptum.paula.ui.TerminalUi;
import com.adeptum.paula.ui.Theme;

@Command(name = "paula",
        mixinStandardHelpOptions = true,
        versionProvider = BuildInfo.class,
        description = "Terminal music player for demoscene and chip music.",
        subcommands = {InfoCommand.class, FormatsCommand.class})
public final class Paula implements Runnable {

    private static final Duration PLACEHOLDER_SONG_LENGTH = Duration.ofSeconds(30);

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
        if (files.isEmpty()) {
            spec.commandLine().usage(spec.commandLine().getOut());
            return;
        }
        try (PlaybackEngine engine = new PlaybackEngine(output.createSink(), sampleRate, bufferFrames);
                TerminalUi ui = new TerminalUi()) {
            new PlayerSession(new Playlist(files), ModuleLoaderRegistry.withBuiltInLoaders(),
                    (module, rate) -> new SilenceRenderer(PLACEHOLDER_SONG_LENGTH, rate), engine, ui).run();
        } catch (AudioException | IOException e) {
            throw new ExecutionException(spec.commandLine(), e.getMessage(), e);
        }
    }
}

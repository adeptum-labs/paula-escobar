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

package com.adeptum.paula.audio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public enum AudioBackend {

    AUTO(null),
    JAVASOUND(null),
    PULSE("pacat"),
    ALSA("aplay"),
    FFPLAY("ffplay"),
    SOX("play");

    /**
     * The sound servers of Linux first and the two players that carry raw sound anywhere after them, since
     * those are all a native executable has to reach for away from Linux.
     */
    private static final List<AudioBackend> COMMAND_BACKENDS = List.of(PULSE, ALSA, FFPLAY, SOX);
    private static final String WINDOWS_SUFFIX = ".exe";

    private final String executable;

    AudioBackend(String executable) {
        this.executable = executable;
    }

    public AudioSink createSink() throws AudioException {
        return switch (this) {
            case AUTO -> detect().createSink();
            case JAVASOUND -> new JavaSoundSink();
            case PULSE, ALSA, FFPLAY, SOX -> new CommandAudioSink(this::command);
        };
    }

    public List<String> command(int sampleRate) {
        final String rate = Integer.toString(sampleRate);
        return switch (this) {
            case PULSE -> List.of(executable, "--raw", "--format=s16le", "--channels=2", "--rate=" + rate);
            case ALSA -> List.of(executable, "-q", "-t", "raw", "-f", "S16_LE", "-c", "2", "-r", rate, "-");
            case FFPLAY -> List.of(executable, "-hide_banner", "-loglevel", "error", "-nodisp", "-autoexit",
                    "-f", "s16le", "-ar", rate, "-ac", "2", "-i", "-");
            case SOX -> List.of(executable, "-q", "-t", "raw", "-e", "signed", "-b", "16", "-c", "2", "-r", rate, "-");
            case AUTO, JAVASOUND -> throw new IllegalStateException(this + " has no playback command");
        };
    }

    public static AudioBackend detect() throws AudioException {
        if (runningOnJvm()) {
            return JAVASOUND;
        }
        return COMMAND_BACKENDS.stream()
                .filter(AudioBackend::isInstalled)
                .findFirst()
                .orElseThrow(() -> new AudioException("No audio backend found; install pacat, aplay, ffplay or sox", null));
    }

    private boolean isInstalled() {
        return Stream.of(Optional.ofNullable(System.getenv("PATH")).orElse("").split(File.pathSeparator))
                .flatMap(directory -> names().map(Path.of(directory)::resolve))
                .anyMatch(Files::isExecutable);
    }

    /**
     * Windows keeps its programs under a suffix, and a bare name would be looked for in vain there.
     */
    private Stream<String> names() {
        return System.getProperty("os.name", "").startsWith("Windows")
                ? Stream.of(executable + WINDOWS_SUFFIX, executable)
                : Stream.of(executable);
    }

    private static boolean runningOnJvm() {
        return Optional.ofNullable(System.getProperty("java.home")).map(Path::of).filter(Files::isDirectory).isPresent();
    }
}

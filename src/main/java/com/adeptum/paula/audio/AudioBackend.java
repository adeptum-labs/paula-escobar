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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Where sound goes: Java Sound on a JVM, one of miniaudio's backends in the native executable.
 */
public enum AudioBackend {

    AUTO(0),
    JAVASOUND(-1),
    PULSE(1),
    ALSA(2),
    JACK(3),
    COREAUDIO(4),
    WASAPI(5),
    NULL(6);

    private final int number;

    AudioBackend(int number) {
        this.number = number;
    }

    public AudioSink createSink(int bufferFrames) throws AudioException {
        if (runningOnJvm()) {
            if (this == AUTO || this == JAVASOUND) {
                return new JavaSoundSink();
            }
            throw new AudioException(this + " is only available in the native executable", null);
        }
        if (this == JAVASOUND) {
            throw new AudioException("Java Sound is only available on a JVM", null);
        }
        return new NativeAudioSink(this, bufferFrames);
    }

    /**
     * The number the C shim knows this backend by.
     */
    int number() {
        if (number < 0) {
            throw new IllegalStateException(this + " has no native backend");
        }
        return number;
    }

    private static boolean runningOnJvm() {
        return Optional.ofNullable(System.getProperty("java.home")).map(Path::of).filter(Files::isDirectory).isPresent();
    }
}

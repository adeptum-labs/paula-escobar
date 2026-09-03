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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class AudioBackendTest {

    @Test
    void autoDetectsJavaSoundOnAJvm() throws AudioException {
        assertEquals(AudioBackend.JAVASOUND, AudioBackend.detect());
        assertInstanceOf(JavaSoundSink.class, AudioBackend.AUTO.createSink());
    }

    @Test
    void commandBackendsBuildPlaybackCommandsForTheSampleRate() throws AudioException {
        assertEquals(List.of("aplay", "-q", "-t", "raw", "-f", "S16_LE", "-c", "2", "-r", "44100", "-"),
                AudioBackend.ALSA.command(44100));
        assertEquals(List.of("pacat", "--raw", "--format=s16le", "--channels=2", "--rate=48000"),
                AudioBackend.PULSE.command(48000));
        assertInstanceOf(CommandAudioSink.class, AudioBackend.ALSA.createSink());
    }

    @Test
    void javaSoundHasNoCommand() {
        assertThrows(IllegalStateException.class, () -> AudioBackend.JAVASOUND.command(48000));
    }
}

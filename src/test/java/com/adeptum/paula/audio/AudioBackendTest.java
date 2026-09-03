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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AudioBackendTest {

    private static final int BUFFER_FRAMES = 2048;

    @Test
    void aJvmPlaysThroughJavaSound() throws AudioException {
        assertInstanceOf(JavaSoundSink.class, AudioBackend.AUTO.createSink(BUFFER_FRAMES));
        assertInstanceOf(JavaSoundSink.class, AudioBackend.JAVASOUND.createSink(BUFFER_FRAMES));
    }

    @Test
    void theNativeBackendsAreRefusedOnAJvm() {
        final AudioException refusal = assertThrows(AudioException.class, () -> AudioBackend.PULSE.createSink(BUFFER_FRAMES));
        assertTrue(refusal.getMessage().contains("native executable"), refusal.getMessage());
        assertThrows(AudioException.class, () -> AudioBackend.NULL.createSink(BUFFER_FRAMES));
    }

    @Test
    void backendsAreNumberedAsTheShimNumbersThem() {
        assertEquals(0, AudioBackend.AUTO.number());
        assertEquals(1, AudioBackend.PULSE.number());
        assertEquals(2, AudioBackend.ALSA.number());
        assertEquals(3, AudioBackend.JACK.number());
        assertEquals(4, AudioBackend.COREAUDIO.number());
        assertEquals(5, AudioBackend.WASAPI.number());
        assertEquals(6, AudioBackend.NULL.number());
    }

    @Test
    void javaSoundHasNoShimNumber() {
        assertThrows(IllegalStateException.class, AudioBackend.JAVASOUND::number);
    }
}

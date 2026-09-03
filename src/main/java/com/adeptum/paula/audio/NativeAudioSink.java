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

import lombok.extern.slf4j.Slf4j;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;

/**
 * Plays through the miniaudio shim linked into the native executable. The samples are handed over as they
 * lie in memory, which is little-endian on every platform the executable is built for.
 */
@Slf4j
public final class NativeAudioSink implements AudioSink {

    private final AudioBackend backend;
    private final int bufferFrames;
    private boolean open;

    public NativeAudioSink(AudioBackend backend, int bufferFrames) {
        this.backend = backend;
        this.bufferFrames = bufferFrames;
    }

    @Override
    public void open(int sampleRate) throws AudioException {
        if (Shim.open(backend.number(), sampleRate, bufferFrames) != 0) {
            throw new AudioException(backend + ": " + lastError(), null);
        }
        open = true;
        log.info("Playing through {} at {} Hz", CTypeConversion.toJavaString(Shim.backend()), sampleRate);
    }

    @Override
    public void write(short[] interleavedStereo, int frames) {
        try (PinnedObject pinned = PinnedObject.create(interleavedStereo)) {
            if (Shim.write(pinned.addressOfArrayElement(0), frames) != 0) {
                throw new IllegalStateException(backend + " stopped playing: " + lastError());
            }
        }
    }

    @Override
    public void close() {
        if (open) {
            Shim.close();
            open = false;
        }
    }

    private static String lastError() {
        return CTypeConversion.toJavaString(Shim.error());
    }

    @CLibrary("paulaaudio")
    private static final class Shim {

        @CFunction("paula_audio_open")
        static native int open(int backend, int sampleRate, int bufferFrames);

        @CFunction("paula_audio_write")
        static native int write(PointerBase frames, int count);

        @CFunction("paula_audio_close")
        static native void close();

        @CFunction("paula_audio_backend")
        static native CCharPointer backend();

        @CFunction("paula_audio_error")
        static native CCharPointer error();
    }
}

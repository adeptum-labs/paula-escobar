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

package com.adeptum.paula.audio;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Streams raw PCM into the standard input of an external playback command such as aplay or pw-cat.
 */
public final class CommandAudioSink implements AudioSink {

    private final IntFunction<List<String>> commandForRate;
    private Process process;
    private OutputStream stdin;
    private byte[] bytes = new byte[0];

    public CommandAudioSink(IntFunction<List<String>> commandForRate) {
        this.commandForRate = commandForRate;
    }

    @Override
    public void open(int sampleRate) throws AudioException {
        final List<String> command = commandForRate.apply(sampleRate);
        try {
            process = new ProcessBuilder(command)
                    .redirectOutput(Redirect.DISCARD)
                    .redirectError(Redirect.DISCARD)
                    .start();
            stdin = new BufferedOutputStream(process.getOutputStream());
        } catch (IOException e) {
            throw new AudioException("Cannot start audio command " + command, e);
        }
    }

    @Override
    public void write(short[] interleavedStereo, int frames) {
        bytes = Pcm.toLittleEndian(interleavedStereo, frames, bytes);
        try {
            stdin.write(bytes, 0, frames * Pcm.BYTES_PER_FRAME);
        } catch (IOException e) {
            throw new UncheckedIOException("Audio command stopped accepting data", e);
        }
    }

    @Override
    public void close() {
        if (process == null) {
            return;
        }
        try {
            stdin.close();
            process.waitFor();
        } catch (IOException e) {
            process.destroy();
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
        }
    }
}

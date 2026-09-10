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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Keeps a copy of everything played in a wave file. The header carries the sizes, which
 * are only known at the end, so it is written last, over the room left for it at the start.
 *
 * <p>The file is held open as a {@link RandomAccessFile} rather than a channel. The pump
 * carrying the sound here is interrupted to let go of an output holding it back, and a
 * channel closes itself under a thread that is interrupted while writing, which would lose
 * the recording every time a song was changed or the player stopped.</p>
 */
public final class WaveRecorder implements AudioSink {

    static final int HEADER_BYTES = 44;

    private static final int PCM_FORMAT = 1;
    private static final int FORMAT_CHUNK_BYTES = 16;

    private final Path file;
    private RandomAccessFile open;
    private int sampleRate;
    private long dataBytes;
    private byte[] bytes = new byte[0];

    public WaveRecorder(Path file) {
        this.file = file;
    }

    @Override
    public void open(int rate) throws AudioException {
        try {
            open = new RandomAccessFile(file.toFile(), "rw");
            open.setLength(0);
            open.seek(HEADER_BYTES);
        } catch (IOException e) {
            throw new AudioException("Cannot record to " + file + ": " + e.getMessage(), e);
        }
        sampleRate = rate;
    }

    @Override
    public void write(short[] interleavedStereo, int frames) {
        bytes = Pcm.toLittleEndian(interleavedStereo, frames, bytes);
        try {
            open.write(bytes, 0, frames * Pcm.BYTES_PER_FRAME);
            dataBytes += frames * Pcm.BYTES_PER_FRAME;
        } catch (IOException e) {
            throw new IllegalStateException("Recording to " + file + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        finishFile();
    }

    private void finishFile() {
        if (open == null) {
            return;
        }
        try (RandomAccessFile finishing = open) {
            finishing.seek(0);
            finishing.write(header());
        } catch (IOException e) {
            throw new IllegalStateException("Recording to " + file + " could not be finished: " + e.getMessage(), e);
        } finally {
            open = null;
        }
    }

    private byte[] header() {
        final ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.put(ascii("RIFF")).putInt((int) (HEADER_BYTES - 8 + dataBytes)).put(ascii("WAVE"));
        header.put(ascii("fmt ")).putInt(FORMAT_CHUNK_BYTES).putShort((short) PCM_FORMAT).putShort((short) Pcm.CHANNELS);
        header.putInt(sampleRate).putInt(sampleRate * Pcm.BYTES_PER_FRAME);
        header.putShort((short) Pcm.BYTES_PER_FRAME).putShort((short) Short.SIZE);
        header.put(ascii("data")).putInt((int) dataBytes);
        return header.array();
    }

    private static byte[] ascii(String tag) {
        return tag.getBytes(StandardCharsets.US_ASCII);
    }
}

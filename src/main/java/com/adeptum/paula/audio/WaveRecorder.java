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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Hands the sound on to another sink and keeps a copy of it in a wave file. The header carries the sizes, which
 * are only known at the end, so it is written last, over the room left for it at the start.
 */
public final class WaveRecorder implements AudioSink {

    static final int HEADER_BYTES = 44;

    private static final int PCM_FORMAT = 1;
    private static final int FORMAT_CHUNK_BYTES = 16;

    private final AudioSink sink;
    private final Path file;
    private FileChannel channel;
    private int sampleRate;
    private long dataBytes;
    private byte[] bytes = new byte[0];

    public WaveRecorder(AudioSink sink, Path file) {
        this.sink = sink;
        this.file = file;
    }

    @Override
    public void open(int rate) throws AudioException {
        try {
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            channel.position(HEADER_BYTES);
        } catch (IOException e) {
            throw new AudioException("Cannot record to " + file + ": " + e.getMessage(), e);
        }
        sampleRate = rate;
        sink.open(rate);
    }

    @Override
    public void write(short[] interleavedStereo, int frames) {
        sink.write(interleavedStereo, frames);
        bytes = Pcm.toLittleEndian(interleavedStereo, frames, bytes);
        try {
            writeFully(ByteBuffer.wrap(bytes, 0, frames * Pcm.BYTES_PER_FRAME));
            dataBytes += frames * Pcm.BYTES_PER_FRAME;
        } catch (IOException e) {
            throw new IllegalStateException("Recording to " + file + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            sink.close();
        } finally {
            finishFile();
        }
    }

    private void finishFile() {
        if (channel == null) {
            return;
        }
        try (FileChannel open = channel) {
            open.position(0);
            writeFully(header());
        } catch (IOException e) {
            throw new IllegalStateException("Recording to " + file + " could not be finished: " + e.getMessage(), e);
        } finally {
            channel = null;
        }
    }

    private ByteBuffer header() {
        final ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.put(ascii("RIFF")).putInt((int) (HEADER_BYTES - 8 + dataBytes)).put(ascii("WAVE"));
        header.put(ascii("fmt ")).putInt(FORMAT_CHUNK_BYTES).putShort((short) PCM_FORMAT).putShort((short) Pcm.CHANNELS);
        header.putInt(sampleRate).putInt(sampleRate * Pcm.BYTES_PER_FRAME);
        header.putShort((short) Pcm.BYTES_PER_FRAME).putShort((short) Short.SIZE);
        header.put(ascii("data")).putInt((int) dataBytes);
        return header.flip();
    }

    private void writeFully(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static byte[] ascii(String tag) {
        return tag.getBytes(StandardCharsets.US_ASCII);
    }
}

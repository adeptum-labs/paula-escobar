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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaveRecorderTest {

    private static final int SAMPLE_RATE = 8000;

    private final CountingSink played = new CountingSink();

    @Test
    void writesWhatPassesThroughAsAWaveFile(@TempDir Path dir) throws Exception {
        final Path wave = dir.resolve("played.wav");
        try (WaveRecorder recorder = new WaveRecorder(played, wave)) {
            recorder.open(SAMPLE_RATE);
            recorder.write(new short[] {1, -1, 0x0102, (short) 0x8000, 9, 9}, 2);
            recorder.write(new short[] {3, 4}, 1);
        }

        try (AudioInputStream audio = AudioSystem.getAudioInputStream(wave.toFile())) {
            final AudioFormat format = audio.getFormat();
            assertEquals(SAMPLE_RATE, (int) format.getSampleRate());
            assertEquals(Pcm.CHANNELS, format.getChannels());
            assertEquals(Short.SIZE, format.getSampleSizeInBits());
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
            assertTrue(!format.isBigEndian());
            assertEquals(3, audio.getFrameLength());
            assertArrayEquals(new byte[] {1, 0, -1, -1, 0x02, 0x01, 0x00, (byte) 0x80, 3, 0, 4, 0}, audio.readAllBytes());
        }
    }

    @Test
    void theSoundStillReachesTheSinkItWraps(@TempDir Path dir) throws Exception {
        try (WaveRecorder recorder = new WaveRecorder(played, dir.resolve("played.wav"))) {
            recorder.open(SAMPLE_RATE);
            recorder.write(new short[4], 2);
        }

        assertEquals(SAMPLE_RATE, played.openedAt);
        assertEquals(2, played.frames);
        assertTrue(played.closed);
    }

    @Test
    void aFileThatCannotBeMadeRefusesToOpen(@TempDir Path dir) {
        final WaveRecorder recorder = new WaveRecorder(played, dir.resolve("missing").resolve("played.wav"));

        final AudioException refusal = assertThrows(AudioException.class, () -> recorder.open(SAMPLE_RATE));
        assertTrue(refusal.getMessage().contains("played.wav"), refusal.getMessage());
        assertEquals(0, played.openedAt, "the sink is not opened when the recording cannot start");
    }

    @Test
    void anEmptyRecordingIsStillAWaveFile(@TempDir Path dir) throws IOException, UnsupportedAudioFileException, AudioException {
        final Path wave = dir.resolve("played.wav");
        try (WaveRecorder recorder = new WaveRecorder(played, wave)) {
            recorder.open(SAMPLE_RATE);
        }

        assertEquals(WaveRecorder.HEADER_BYTES, Files.size(wave));
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(wave.toFile())) {
            assertEquals(0, audio.getFrameLength());
        }
    }

    private static final class CountingSink implements AudioSink {

        int openedAt;
        int frames;
        boolean closed;

        @Override
        public void open(int sampleRate) {
            openedAt = sampleRate;
        }

        @Override
        public void write(short[] interleavedStereo, int count) {
            frames += count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}

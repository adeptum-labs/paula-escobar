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

package com.adeptum.paula.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.audio.AudioException;
import com.adeptum.paula.audio.AudioSink;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaybackEngineTest {

    private static final int SAMPLE_RATE = 8000;

    private final RecordingSink sink = new RecordingSink();

    @Test
    void pumpsEveryFrameOfTheRendererIntoTheSink() throws AudioException {
        try (PlaybackEngine engine = new PlaybackEngine(sink, SAMPLE_RATE, 256)) {
            engine.play(new SilenceRenderer(Duration.ofMillis(100), SAMPLE_RATE));
            engine.awaitEnd();

            assertEquals(PlaybackState.FINISHED, engine.state());
            assertEquals(800, sink.frames);
            assertEquals(Duration.ofMillis(100), engine.position());
            assertEquals(800, engine.tap().written(), "everything written to the sink is also tapped");
        }
        assertTrue(sink.closed);
    }

    @Test
    void togglePauseFlipsBetweenPlayingAndPaused() throws AudioException {
        try (PlaybackEngine engine = new PlaybackEngine(sink, SAMPLE_RATE, 256)) {
            engine.play(new SilenceRenderer(Duration.ofSeconds(10), SAMPLE_RATE));
            engine.togglePause();
            assertEquals(PlaybackState.PAUSED, engine.state());
            engine.togglePause();
            assertEquals(PlaybackState.PLAYING, engine.state());
            engine.stop();
            assertEquals(PlaybackState.STOPPED, engine.state());
        }
    }

    @Test
    void seekMovesTheRendererRelativeToItsPosition() throws AudioException {
        final SeekRecordingRenderer renderer = new SeekRecordingRenderer(Duration.ofSeconds(4));
        try (PlaybackEngine engine = new PlaybackEngine(sink, SAMPLE_RATE, 256)) {
            engine.play(renderer);

            engine.seek(Duration.ofSeconds(5));
            assertEquals(Duration.ofSeconds(9), renderer.lastTarget);

            engine.seek(Duration.ofSeconds(-5));
            assertEquals(Duration.ofSeconds(-1), renderer.lastTarget);
        }
    }

    @Test
    void aRendererThatThrowsEndsTheSongInsteadOfTheThread() throws AudioException {
        try (PlaybackEngine engine = new PlaybackEngine(sink, SAMPLE_RATE, 256)) {
            engine.play(new Renderer() {
                @Override
                public int render(short[] interleavedStereo) {
                    throw new IllegalStateException("emulation broke");
                }

                @Override
                public Duration position() {
                    return Duration.ZERO;
                }

                @Override
                public void seek(Duration target) {
                }
            });
            engine.awaitEnd();

            assertEquals(PlaybackState.FINISHED, engine.state());
            assertEquals(0, sink.frames);
        }
    }

    @Test
    void copiesEveryFrameToTheSinksThatKeepACopy() throws AudioException {
        final RecordingSink copy = new RecordingSink();
        try (PlaybackEngine engine = new PlaybackEngine(sink, List.of(copy), SAMPLE_RATE, 256)) {
            engine.play(new SilenceRenderer(Duration.ofMillis(100), SAMPLE_RATE));
            engine.awaitEnd();

            assertEquals(sink.frames, copy.frames);
            assertEquals(SAMPLE_RATE, copy.openedAt);
        }
    }

    @Test
    void movesTheSoundToAnotherSinkWithoutStoppingTheSong() throws AudioException {
        final RecordingSink next = new RecordingSink();
        try (PlaybackEngine engine = new PlaybackEngine(sink, SAMPLE_RATE, 256)) {
            engine.play(new SeekRecordingRenderer(Duration.ZERO), "Song", "Tracker");
            engine.switchOutput(next);
            next.awaitFrames();

            assertTrue(sink.closed, "the old output is closed");
            assertEquals("Song", next.title, "and the new one told what plays");
            assertEquals(PlaybackState.PLAYING, engine.state());
        }
    }

    @Test
    void letsTheOutputDrainOnceTheSongIsRendered() throws AudioException {
        try (PlaybackEngine engine = new PlaybackEngine(sink, SAMPLE_RATE, 256)) {
            engine.play(new SilenceRenderer(Duration.ofMillis(50), SAMPLE_RATE));
            engine.awaitEnd();

            assertTrue(sink.drained);
            assertEquals(PlaybackState.FINISHED, engine.state());
        }
    }

    @Test
    void seekingWithoutASongIsIgnored() throws AudioException {
        try (PlaybackEngine engine = new PlaybackEngine(sink, SAMPLE_RATE, 256)) {
            engine.seek(Duration.ofSeconds(5));

            assertEquals(PlaybackState.STOPPED, engine.state());
        }
    }

    /**
     * Renders endlessly from a fixed position so the seek target does not depend on how far the pump has run.
     */
    private static final class SeekRecordingRenderer implements Renderer {

        private final Duration position;
        private volatile Duration lastTarget;

        private SeekRecordingRenderer(Duration position) {
            this.position = position;
        }

        @Override
        public int render(short[] interleavedStereo) {
            return interleavedStereo.length / 2;
        }

        @Override
        public Duration position() {
            return position;
        }

        @Override
        public void seek(Duration target) {
            lastTarget = target;
        }
    }

    private static final class RecordingSink implements AudioSink {

        private volatile int frames;
        private volatile int openedAt;
        private volatile String title;
        private volatile boolean drained;
        private volatile boolean closed;

        @Override
        public void open(int sampleRate) {
            assertEquals(SAMPLE_RATE, sampleRate);
            openedAt = sampleRate;
        }

        @Override
        public void begin(String newTitle, String subtitle) {
            title = newTitle;
        }

        @Override
        public void write(short[] interleavedStereo, int frameCount) {
            frames += frameCount;
        }

        @Override
        public void drain() {
            drained = true;
        }

        @Override
        public void close() {
            closed = true;
        }

        void awaitFrames() throws AudioException {
            for (int tries = 0; tries < 200 && frames == 0; tries++) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            assertTrue(frames > 0, "the sink was written to");
        }
    }
}

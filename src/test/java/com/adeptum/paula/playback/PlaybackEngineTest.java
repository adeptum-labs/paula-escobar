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

package com.adeptum.paula.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.audio.AudioException;
import com.adeptum.paula.audio.AudioSink;
import java.time.Duration;
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

    private static final class RecordingSink implements AudioSink {

        private int frames;
        private boolean closed;

        @Override
        public void open(int sampleRate) {
            assertEquals(SAMPLE_RATE, sampleRate);
        }

        @Override
        public void write(short[] interleavedStereo, int frameCount) {
            frames += frameCount;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}

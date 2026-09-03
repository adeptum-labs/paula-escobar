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

package com.adeptum.paula.module.digibooster;

import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Plays one DigiBooster module for Paula. The replayer cannot be wound back, so a seek backwards starts the
 * song again and then catches up in slices from the audio thread, playing silence meanwhile, rather than
 * holding up whoever asked for the seek.
 */
public final class DigiBoosterRenderer implements Renderer {

    private static final int STEREO = 2;
    private static final int MILLIS = 1000;
    private static final int WAVEFORM_SAMPLES = 128;
    private static final int GAIN_UNIT = 1 << 14;
    private static final int CATCH_UP_FRAMES_PER_BUFFER = 16384;
    private static final int SCRATCH_FRAMES = 4096;

    private static final long LONGEST_SONG_SECONDS = 60 * 60;

    private final DbmFile file;
    private final int sampleRate;
    private final OptionalLong songFrames;

    private DbmEngine engine;
    private long renderedFrames;
    private long pendingFrames;
    private boolean ended;
    private short[] scratch = new short[0];

    public DigiBoosterRenderer(DbmFile file, int sampleRate) {
        this.file = file;
        this.sampleRate = sampleRate;
        this.engine = new DbmEngine(file, sampleRate);
        this.songFrames = DbmEngine.songFrames(file, sampleRate, LONGEST_SONG_SECONDS * sampleRate);
    }

    @Override
    public int render(short[] interleavedStereo) {
        final int frames = interleavedStereo.length / STEREO;
        catchUp();
        if (ended) {
            return 0;
        }
        if (pendingFrames > 0) {
            Arrays.fill(interleavedStereo, 0, frames * STEREO, (short) 0);
            return frames;
        }
        final int mixed = engine.mix(interleavedStereo, frames);
        renderedFrames += mixed;
        ended = mixed < frames;
        return mixed;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis((renderedFrames + pendingFrames) * MILLIS / sampleRate);
    }

    @Override
    public Optional<Duration> length() {
        return songFrames.isPresent() ? Optional.of(Duration.ofMillis(songFrames.getAsLong() * MILLIS / sampleRate)) : Optional.empty();
    }

    @Override
    public void seek(Duration target) {
        final long targetFrames = Math.max(0, target.toMillis() * sampleRate / MILLIS);
        if (targetFrames < renderedFrames) {
            engine = new DbmEngine(file, sampleRate);
            renderedFrames = 0;
            ended = false;
        }
        pendingFrames = Math.max(0, targetFrames - renderedFrames);
    }

    /**
     * The state of every track as it stands: the pump thread may move on meanwhile, which only ever shifts a
     * scope by a few samples.
     */
    @Override
    public List<ChannelState> channels() {
        final List<ChannelState> channels = new ArrayList<>(engine.tracks());
        for (int number = 0; number < engine.tracks(); number++) {
            channels.add(state(number, engine.track(number)));
        }
        return channels;
    }

    private ChannelState state(int number, DbmTrack track) {
        final double volume = Math.min(1.0, (double) (track.gainLeft + track.gainRight) / (2 * GAIN_UNIT));
        final double[] waveform = new double[WAVEFORM_SAMPLES];
        if (!track.playing || track.wavetable == null || volume <= 0) {
            return new ChannelState(number + 1, 0, 0, waveform);
        }
        final short[] sample = track.wavetable.sample();
        final int start = track.wavetable.position();
        for (int at = 0; at < WAVEFORM_SAMPLES && start + at < sample.length; at++) {
            waveform[at] = (double) sample[start + at] / Short.MAX_VALUE * volume;
        }
        return new ChannelState(number + 1, track.instrument, volume, waveform);
    }

    private void catchUp() {
        int budget = CATCH_UP_FRAMES_PER_BUFFER;
        while (pendingFrames > 0 && budget > 0 && !ended) {
            final int frames = (int) Math.min(SCRATCH_FRAMES, Math.min(pendingFrames, budget));
            if (scratch.length < frames * STEREO) {
                scratch = new short[frames * STEREO];
            }
            final int mixed = engine.mix(scratch, frames);
            renderedFrames += mixed;
            pendingFrames -= frames;
            budget -= frames;
            ended = mixed < frames;
        }
        pendingFrames = Math.max(0, pendingFrames);
    }
}

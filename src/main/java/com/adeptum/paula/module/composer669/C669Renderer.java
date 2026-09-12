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

package com.adeptum.paula.module.composer669;

import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Plays one Composer 669 module for Paula. The sequencer cannot be wound back, so a seek backwards starts the
 * song again and then catches up in slices from the audio thread, playing silence meanwhile, rather than
 * holding up whoever asked for the seek.
 */
public final class C669Renderer implements Renderer {

    private static final int STEREO = 2;
    private static final int MILLIS = 1000;
    private static final int WAVEFORM_SAMPLES = 128;
    private static final double SAMPLE_SCALE = Short.MAX_VALUE;
    private static final int CATCH_UP_FRAMES_PER_BUFFER = 16384;
    private static final int SCRATCH_FRAMES = 4096;
    private static final long LONGEST_SONG_SECONDS = 60 * 60;

    private final C669File file;
    private final int sampleRate;
    private final OptionalLong songFrames;
    private final boolean[] muted = new boolean[C669Engine.VOICES];
    private C669Engine engine;
    private long renderedFrames;
    private long pendingFrames;
    private boolean ended;
    private short[] scratch = new short[0];

    C669Renderer(C669File file, int sampleRate) {
        this.file = file;
        this.sampleRate = sampleRate;
        this.engine = new C669Engine(file, sampleRate);
        this.songFrames = C669Engine.songFrames(file, sampleRate, LONGEST_SONG_SECONDS * sampleRate);
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
        return songFrames.isPresent()
                ? Optional.of(Duration.ofMillis(songFrames.getAsLong() * MILLIS / sampleRate)) : Optional.empty();
    }

    @Override
    public void seek(Duration target) {
        final long targetFrames = Math.max(0, target.toMillis() * sampleRate / MILLIS);
        if (targetFrames < renderedFrames) {
            engine = new C669Engine(file, sampleRate);
            applyMutes();
            renderedFrames = 0;
            ended = false;
        }
        pendingFrames = Math.max(0, targetFrames - renderedFrames);
    }

    @Override
    public List<ChannelState> channels() {
        final List<ChannelState> channels = new ArrayList<>(C669Engine.VOICES);
        for (int number = 0; number < C669Engine.VOICES; number++) {
            channels.add(state(number, engine.voice(number)));
        }
        return channels;
    }

    /**
     * A seek backwards starts the song over on a fresh engine, so what the listener silenced is kept here and
     * put back on the new voices.
     */
    @Override
    public void mute(int number, boolean silenced) {
        if (number >= 1 && number <= muted.length) {
            muted[number - 1] = silenced;
            applyMutes();
        }
    }

    private void applyMutes() {
        for (int number = 0; number < muted.length; number++) {
            engine.voice(number).muted = muted[number];
        }
    }

    /**
     * What one voice is doing, read from where it stands in its sample so the scope draws the wave that is
     * actually sounding.
     */
    private static ChannelState state(int number, C669Voice voice) {
        final double volume = Math.clamp((double) voice.volume / C669Voice.FULL_VOLUME, 0, 1);
        final double[] waveform = new double[WAVEFORM_SAMPLES];
        if (voice.muted || !voice.sounding || voice.sample == null || volume <= 0) {
            return new ChannelState(number + 1, 0, 0, waveform, voice.muted);
        }
        final int start = (int) voice.position;
        for (int at = 0; at < WAVEFORM_SAMPLES; at++) {
            waveform[at] = voice.sample[(start + at) % voice.sample.length] / SAMPLE_SCALE * volume;
        }
        return new ChannelState(number + 1, voice.instrument, volume, waveform, voice.muted);
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

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

package com.adeptum.paula.module.hively;

import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Plays one AHX or HivelyTracker tune for Paula. The replayer cannot be wound back, so a seek backwards
 * starts the tune again and then catches up in slices from the audio thread, playing silence meanwhile,
 * rather than holding up whoever asked for the seek.
 */
public final class HivelyRenderer implements Renderer {

    private static final int STEREO = 2;
    private static final int MILLIS = 1000;
    private static final int WAVEFORM_SAMPLES = 128;
    private static final int FULL_VOLUME = 0x40;
    private static final double SAMPLE_SCALE = 128.0;
    private static final int BYTE = 0xff;
    private static final int FIXED_POINT = 16;
    private static final int FIRST_SUBSONG = 0;
    private static final int CATCH_UP_FRAMES_PER_BUFFER = 16384;
    private static final int SCRATCH_FRAMES = 4096;

    private static final long LONGEST_SONG_SECONDS = 60 * 60;

    private final HvlTune tune;
    private final int sampleRate;
    private final OptionalLong songFrames;

    private final boolean[] muted;

    private HvlEngine engine;
    private long renderedFrames;
    private long pendingFrames;
    private boolean ended;
    private short[] scratch = new short[0];

    public HivelyRenderer(HvlTune tune, int sampleRate) {
        this.tune = tune;
        this.sampleRate = sampleRate;
        this.engine = new HvlEngine(tune, sampleRate, FIRST_SUBSONG);
        this.muted = new boolean[engine.voices()];
        this.songFrames = HvlEngine.songFrames(tune, sampleRate, LONGEST_SONG_SECONDS * sampleRate);
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
            engine = new HvlEngine(tune, sampleRate, FIRST_SUBSONG);
            applyMutes();
            renderedFrames = 0;
            ended = false;
        }
        pendingFrames = Math.max(0, targetFrames - renderedFrames);
    }

    /**
     * The state of every voice as it stands: the pump thread may move on meanwhile, which only ever shifts a
     * scope by a few samples.
     */
    @Override
    public List<ChannelState> channels() {
        final List<ChannelState> channels = new ArrayList<>(engine.voices());
        for (int number = 0; number < engine.voices(); number++) {
            channels.add(state(number, engine.voice(number)));
        }
        return channels;
    }

    /**
     * A seek backwards starts the tune over on a fresh engine, so what the listener silenced is kept here and
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
     * The voice buffer holds the waveform repeated for as long as the mixer plays before it wraps, so the
     * scope reads on from where the voice has reached and wraps with it.
     */
    private ChannelState state(int number, HvlVoice voice) {
        final double volume = Math.clamp((double) (voice.audioVolume & BYTE) / FULL_VOLUME, 0, 1);
        final double[] waveform = new double[WAVEFORM_SAMPLES];
        if (voice.muted || voice.mixSource == null || volume <= 0) {
            return new ChannelState(number + 1, 0, 0, waveform, voice.muted);
        }
        final int start = voice.samplePos >> FIXED_POINT;
        for (int at = 0; at < WAVEFORM_SAMPLES; at++) {
            final int sample = voice.mixSource[voice.mixOffset + (start + at) % HvlVoice.WAVE_SAMPLES];
            waveform[at] = sample / SAMPLE_SCALE * volume;
        }
        return new ChannelState(number + 1, instrument(voice), volume, waveform, voice.muted);
    }

    private int instrument(HvlVoice voice) {
        return Math.max(0, tune.instruments().indexOf(voice.instrument));
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

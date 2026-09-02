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

package com.adeptum.paula.playback.javamod;

import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import de.quippy.javamod.multimedia.mod.mixer.ChannelMemory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pulls 32-bit stereo audio from JavaMod's mixer and reduces it to Paula's 16-bit interleaved frames.
 */
public final class JavaModRenderer implements Renderer {

    private static final int OUTPUT_CHANNELS = 2;
    private static final int BITS_TO_DROP = 32 - Short.SIZE;
    private static final long ROUNDING = 1L << (BITS_TO_DROP - 1);
    private static final int MAX_NNA_CHANNELS = 200;
    private static final int WAVEFORM_SAMPLES = 128;
    private static final double FULL_CHANNEL_VOLUME = 2.0 * ModConstants.MAXCHANNELVOLUME;

    private final BasicModMixer mixer;
    private final int sampleRate;
    private final int channelCount;
    private final Duration length;
    private final Map<Sample, Double> samplePeaks = new HashMap<>();
    private long[] left = new long[0];
    private long[] right = new long[0];
    private long renderedFrames;

    public JavaModRenderer(Module tracker, int sampleRate) {
        this.sampleRate = sampleRate;
        this.mixer = tracker.getModMixer(sampleRate, ModConstants.INTERPOLATION_WINDOWSFIR,
                ModConstants.AMIGAEMULATION_NONE, ModConstants.PLAYER_LOOP_FADEOUT, MAX_NNA_CHANNELS);
        mixer.setFireUpdates(false);
        this.channelCount = tracker.getNChannels();
        this.length = Duration.ofMillis(mixer.getLengthInMilliseconds());
    }

    @Override
    public int render(short[] interleavedStereo) {
        final int frames = interleavedStereo.length / OUTPUT_CHANNELS;
        ensureBuffers(frames);
        final int mixed = mixer.mixIntoBuffer(left, right, frames);
        if (mixed <= 0) {
            return 0;
        }
        for (int i = 0; i < mixed; i++) {
            interleavedStereo[i * OUTPUT_CHANNELS] = toPcm16(left[i]);
            interleavedStereo[i * OUTPUT_CHANNELS + 1] = toPcm16(right[i]);
            left[i] = 0;
            right[i] = 0;
        }
        renderedFrames += mixed;
        return mixed;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis(renderedFrames * 1000 / sampleRate);
    }

    /**
     * JavaMod replays the song from the start without mixing and reports how far it actually got, which is the
     * first tick at or past the target.
     */
    @Override
    public void seek(Duration target) {
        renderedFrames = mixer.seek(target.toMillis());
    }

    @Override
    public Optional<Duration> length() {
        return length.isPositive() ? Optional.of(length) : Optional.empty();
    }

    /**
     * Reads the mixer's channel state as it stands; the pump thread may move on meanwhile, which only ever
     * shifts a scope by a few samples.
     */
    @Override
    public List<ChannelState> channels() {
        final ChannelMemory[] memory = ChannelPeek.channels(mixer);
        final int used = Math.min(channelCount, memory.length);
        final List<ChannelState> states = new ArrayList<>(used);
        for (int i = 0; i < used; i++) {
            states.add(state(i + 1, memory[i]));
        }
        return states;
    }

    static short toPcm16(long sample) {
        return (short) Math.clamp((sample + ROUNDING) >> BITS_TO_DROP, Short.MIN_VALUE, Short.MAX_VALUE);
    }

    private ChannelState state(int number, ChannelMemory channel) {
        final Sample sample = channel.currentSample;
        final double volume = Math.min(1.0, (channel.actVolumeLeft + channel.actVolumeRight) / FULL_CHANNEL_VOLUME);
        final double[] waveform = new double[WAVEFORM_SAMPLES];
        if (sample == null || sample.sampleL == null || sample.sampleL.length == 0 || channel.instrumentFinished || channel.muted || volume <= 0) {
            return new ChannelState(number, 0, 0, waveform);
        }
        final long[] data = sample.sampleL;
        final double peak = samplePeaks.computeIfAbsent(sample, JavaModRenderer::peakOf);
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            final int index = wrap(channel.currentSamplePos + i, sample);
            waveform[i] = index < 0 ? 0 : data[index] / peak * volume;
        }
        return new ChannelState(number, channel.currentAssignedInstrumentIndex, volume, waveform);
    }

    private static int wrap(int index, Sample sample) {
        if (index < sample.sampleL.length) {
            return index;
        }
        final boolean loops = sample.loopLength > 0 && sample.loopStop > sample.loopStart;
        return loops ? sample.loopStart + (index - sample.loopStart) % (sample.loopStop - sample.loopStart) : -1;
    }

    private static double peakOf(Sample sample) {
        long peak = 1;
        for (final long value : sample.sampleL) {
            peak = Math.max(peak, Math.abs(value));
        }
        return peak;
    }

    private void ensureBuffers(int frames) {
        if (left.length < frames) {
            left = new long[frames];
            right = new long[frames];
        }
    }
}

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

package com.adeptum.paula.module.sap;

import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.sf.asap.ASAP;
import net.sf.asap.ASAPArgumentException;
import net.sf.asap.ASAPFormatException;
import net.sf.asap.ASAPSampleFormat;

/**
 * Pulls 16-bit PCM out of ASAP's POKEY emulation. ASAP mixes its channels itself and only tells how loud each
 * one is, so a channel's scope shows the mix at that channel's loudness, and muting goes through ASAP's mask.
 */
public final class SapRenderer implements Renderer {

    private static final int OUTPUT_CHANNELS = 2;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int CHANNELS_PER_POKEY = 4;
    private static final int LOUDEST = 15;
    private static final int WAVEFORM_SAMPLES = 128;

    private final ASAP asap = new ASAP();
    private final Duration length;
    private final int samplesPerFrame;
    private final int pokeyChannels;
    private final double[] recent = new double[WAVEFORM_SAMPLES];
    private byte[] raw = new byte[0];
    private int muteMask;

    public SapRenderer(Path source, byte[] file, int song, Duration length, int sampleRate) {
        this.length = length;
        try {
            asap.setSampleRate(sampleRate);
            asap.load(SapLoader.nameForAsap(source.getFileName().toString()), file, file.length);
            asap.playSong(song, (int) length.toMillis());
        } catch (ASAPFormatException | ASAPArgumentException e) {
            throw new IllegalStateException("ASAP cannot play " + source + ": " + e.getMessage(), e);
        }
        samplesPerFrame = asap.getInfo().getChannels();
        pokeyChannels = CHANNELS_PER_POKEY * samplesPerFrame;
    }

    @Override
    public int render(short[] interleavedStereo) {
        final int frames = interleavedStereo.length / OUTPUT_CHANNELS;
        final int bytesPerFrame = samplesPerFrame * BYTES_PER_SAMPLE;
        if (raw.length < frames * bytesPerFrame) {
            raw = new byte[frames * bytesPerFrame];
        }
        final int rendered = asap.generate(raw, frames * bytesPerFrame, ASAPSampleFormat.S16_L_E) / bytesPerFrame;
        for (int frame = 0; frame < rendered; frame++) {
            final short left = sample(frame * samplesPerFrame);
            interleavedStereo[frame * OUTPUT_CHANNELS] = left;
            interleavedStereo[frame * OUTPUT_CHANNELS + 1] = samplesPerFrame == 1 ? left : sample(frame * samplesPerFrame + 1);
        }
        remember(interleavedStereo, rendered);
        return rendered;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis(asap.getPosition());
    }

    @Override
    public Optional<Duration> length() {
        return Optional.of(length);
    }

    /**
     * ASAP starts the song over itself to reach a point behind it and carries the mask across; it is put
     * back here all the same, as the other renderers do after a restart.
     */
    @Override
    public void seek(Duration target) {
        final long millis = Math.clamp(target.toMillis(), 0, length.toMillis());
        try {
            asap.seek((int) millis);
        } catch (ASAPFormatException e) {
            throw new IllegalStateException("ASAP cannot seek: " + e.getMessage(), e);
        }
        asap.mutePokeyChannels(muteMask);
    }

    /**
     * Reads the mixer's last buffer as it stands; the pump thread may move on meanwhile, which only ever
     * shifts a scope by a few samples.
     */
    @Override
    public List<ChannelState> channels() {
        final List<ChannelState> channels = new ArrayList<>(pokeyChannels);
        for (int channel = 0; channel < pokeyChannels; channel++) {
            channels.add(state(channel));
        }
        return channels;
    }

    @Override
    public void mute(int number, boolean silenced) {
        if (number < 1 || number > pokeyChannels) {
            return;
        }
        final int bit = 1 << (number - 1);
        muteMask = silenced ? muteMask | bit : muteMask & ~bit;
        asap.mutePokeyChannels(muteMask);
    }

    private short sample(int index) {
        return (short) ((raw[index * BYTES_PER_SAMPLE] & 0xFF) | (raw[index * BYTES_PER_SAMPLE + 1] << 8));
    }

    private void remember(short[] interleavedStereo, int frames) {
        final int kept = Math.min(WAVEFORM_SAMPLES, frames);
        Arrays.fill(recent, kept, WAVEFORM_SAMPLES, 0);
        for (int at = 0; at < kept; at++) {
            final int frame = frames - kept + at;
            recent[at] = (interleavedStereo[frame * OUTPUT_CHANNELS] + interleavedStereo[frame * OUTPUT_CHANNELS + 1])
                    / (2.0 * Short.MAX_VALUE);
        }
    }

    private ChannelState state(int channel) {
        final boolean muted = (muteMask & (1 << channel)) != 0;
        final double volume = muted ? 0 : (double) asap.getPokeyChannelVolume(channel) / LOUDEST;
        final double[] waveform = new double[WAVEFORM_SAMPLES];
        for (int at = 0; at < WAVEFORM_SAMPLES; at++) {
            waveform[at] = recent[at] * volume;
        }
        return new ChannelState(channel + 1, 0, volume, waveform, muted);
    }
}

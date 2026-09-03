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

package com.adeptum.paula.module.flac;

import com.adeptum.paula.module.pcm.PcmRenderer;
import de.quippy.jflac.FLACDecoder;
import de.quippy.jflac.frame.Frame;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Decodes one FLAC file frame by frame. A frame stands on its own, so a seek starts the file again and decodes
 * the frames ahead of the target away; the decoder's own seeking is left alone because it leans on a seek table
 * the format does not require and runs on past the end of a stream that has none.
 */
@Slf4j
public final class FlacRenderer extends PcmRenderer {

    private static final int SHORT_BITS = 16;

    private final byte[] file;
    private final FlacAudio audio;
    private final int shift;

    private FLACDecoder decoder;
    private Frame landed;

    public FlacRenderer(byte[] file, FlacAudio audio, int outputRate) {
        super(audio.rate(), audio.channels(), outputRate);
        this.file = file;
        this.audio = audio;
        this.shift = audio.bits() - SHORT_BITS;
        open(0);
    }

    @Override
    public Optional<Duration> length() {
        return audio.length();
    }

    @Override
    protected void rewind(Duration target) {
        open(audio.sampleAt(target));
    }

    @Override
    protected short[] decode() {
        try {
            final Frame frame = nextFrame();
            return frame == null ? null : samples(frame.header.blockSize);
        } catch (IOException | RuntimeException e) {
            log.debug("The FLAC stream ends in the middle of a frame", e);
            return null;
        }
    }

    private void open(long sample) {
        try {
            decoder = FlacAudio.open(file);
            landed = sample > 0 ? skipTo(sample) : null;
        } catch (IOException | RuntimeException e) {
            log.debug("The FLAC stream cannot be read from {} samples in", sample, e);
            decoder = null;
        }
    }

    /**
     * The frame the moment falls in, which is as near as a seek can land, and which is handed out whole rather
     * than dropped for having been decoded on the way there.
     */
    private Frame skipTo(long sample) throws IOException {
        long decoded = 0;
        for (Frame frame = decoder.readNextFrame(); frame != null; frame = decoder.readNextFrame()) {
            decoded += frame.header.blockSize;
            if (decoded > sample) {
                return frame;
            }
        }
        return null;
    }

    private Frame nextFrame() throws IOException {
        if (decoder == null) {
            return null;
        }
        if (landed == null) {
            return decoder.readNextFrame();
        }
        final Frame frame = landed;
        landed = null;
        return frame;
    }

    private short[] samples(int frames) {
        final int channels = audio.channels();
        final short[] decoded = new short[frames * channels];
        for (int channel = 0; channel < channels; channel++) {
            final int[] output = decoder.getChannelData()[channel].getOutput();
            for (int frame = 0; frame < frames; frame++) {
                decoded[frame * channels + channel] = scaled(output[frame]);
            }
        }
        return decoded;
    }

    private short scaled(int sample) {
        return (short) (shift >= 0 ? sample >> shift : sample << -shift);
    }
}

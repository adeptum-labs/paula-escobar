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

package com.adeptum.paula.module.mp3;

import com.adeptum.paula.playback.Renderer;
import de.quippy.mp3.decoder.Bitstream;
import de.quippy.mp3.decoder.BitstreamException;
import de.quippy.mp3.decoder.Decoder;
import de.quippy.mp3.decoder.DecoderException;
import de.quippy.mp3.decoder.Header;
import de.quippy.mp3.decoder.SampleBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Decodes one MPEG audio file frame by frame and hands the samples on at the rate the engine mixes at,
 * interpolating between the two source frames the fractional position falls between when the two rates differ.
 * A file already at the output rate passes through untouched.
 */
@Slf4j
public final class Mp3Renderer implements Renderer {

    private static final int STEREO = 2;
    private static final int MILLIS = 1000;

    private final byte[] file;
    private final int from;
    private final int outputRate;
    private final Mp3Audio audio;
    private final double step;

    private Bitstream stream;
    private Decoder decoder;
    private short[] source = new short[0];
    private int sourceFrames;
    private int at;
    private double fraction;
    private boolean ended;
    private long renderedFrames;

    public Mp3Renderer(byte[] file, int from, Mp3Audio audio, int outputRate) {
        this.file = file;
        this.from = from;
        this.audio = audio;
        this.outputRate = outputRate;
        this.step = (double) audio.rate() / outputRate;
        rewind(0);
    }

    @Override
    public int render(short[] interleavedStereo) {
        final int frames = interleavedStereo.length / STEREO;
        int produced = 0;
        while (produced < frames && hasPair()) {
            final int base = at * STEREO;
            interleavedStereo[produced * STEREO] = between(source[base], source[base + STEREO]);
            interleavedStereo[produced * STEREO + 1] = between(source[base + 1], source[base + STEREO + 1]);
            advance();
            produced++;
        }
        renderedFrames += produced;
        return produced;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis(renderedFrames * MILLIS / outputRate);
    }

    @Override
    public Optional<Duration> length() {
        return Optional.of(audio.length());
    }

    /**
     * Frames carry no state from the ones before them beyond the decoder's own overlap, so a seek starts the
     * file again and steps over the frames ahead of the target without decoding any of them.
     */
    @Override
    public void seek(Duration target) {
        final Duration clamped = target.isNegative() ? Duration.ZERO : target;
        rewind(audio.frameAt(clamped));
        renderedFrames = Math.min(clamped.toMillis(), audio.length().toMillis()) * outputRate / MILLIS;
    }

    private void rewind(int frame) {
        stream = Mp3Audio.open(file, from);
        decoder = new Decoder();
        sourceFrames = 0;
        at = 0;
        fraction = 0;
        ended = false;
        renderedFrames = 0;
        for (int skipped = 0; skipped < frame && !ended; skipped++) {
            try {
                ended = stream.readFrame() == null;
                stream.closeFrame();
            } catch (BitstreamException e) {
                ended = true;
            }
        }
    }

    private boolean hasPair() {
        while (at + 1 >= sourceFrames) {
            if (!decodeOneFrame()) {
                return false;
            }
        }
        return true;
    }

    private void advance() {
        fraction += step;
        final int whole = (int) fraction;
        at += whole;
        fraction -= whole;
    }

    private short between(short lower, short upper) {
        return (short) Math.round(lower + (upper - lower) * fraction);
    }

    /**
     * What has already been played is dropped before the next frame is added, so the buffer stays the length of
     * a frame or two however long the song is.
     */
    private boolean decodeOneFrame() {
        if (ended) {
            return false;
        }
        final short[] decoded = decode();
        if (decoded == null) {
            ended = true;
            return false;
        }
        compact();
        append(decoded);
        return true;
    }

    private short[] decode() {
        try {
            final Header header = stream.readFrame();
            if (header == null) {
                return null;
            }
            final SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, stream);
            final short[] decoded = Arrays.copyOf(samples.getBuffer(), samples.getBufferLength());
            stream.closeFrame();
            return decoded;
        } catch (BitstreamException | DecoderException e) {
            log.debug("The MPEG audio stream ends at frame {}", stream.getCurrentFrameNumber(), e);
            return null;
        }
    }

    private void compact() {
        final int dropped = Math.min(at, sourceFrames);
        final int kept = sourceFrames - dropped;
        System.arraycopy(source, dropped * STEREO, source, 0, kept * STEREO);
        sourceFrames = kept;
        at -= dropped;
    }

    private void append(short[] decoded) {
        final int channels = audio.channels();
        final int frames = decoded.length / channels;
        if (source.length < (sourceFrames + frames) * STEREO) {
            source = Arrays.copyOf(source, (sourceFrames + frames) * STEREO);
        }
        for (int frame = 0; frame < frames; frame++) {
            final short left = decoded[frame * channels];
            source[(sourceFrames + frame) * STEREO] = left;
            source[(sourceFrames + frame) * STEREO + 1] = channels == 1 ? left : decoded[frame * channels + 1];
        }
        sourceFrames += frames;
    }
}

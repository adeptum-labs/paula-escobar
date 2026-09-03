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

import com.adeptum.paula.module.pcm.PcmRenderer;
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
 * Decodes one MPEG audio file frame by frame. Frames carry no state from the ones before them beyond the
 * decoder's own overlap, so a seek starts the file again and steps over the frames ahead of the target without
 * decoding any of them.
 */
@Slf4j
public final class Mp3Renderer extends PcmRenderer {

    private final byte[] file;
    private final int from;
    private final Mp3Audio audio;

    private Bitstream stream;
    private Decoder decoder;

    public Mp3Renderer(byte[] file, int from, Mp3Audio audio, int outputRate) {
        super(audio.rate(), audio.channels(), outputRate);
        this.file = file;
        this.from = from;
        this.audio = audio;
        open(0);
    }

    @Override
    public Optional<Duration> length() {
        return Optional.of(audio.length());
    }

    @Override
    protected void rewind(Duration target) {
        open(audio.frameAt(target));
    }

    @Override
    protected short[] decode() {
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

    private void open(int frame) {
        stream = Mp3Audio.open(file, from);
        decoder = new Decoder();
        for (int skipped = 0; skipped < frame; skipped++) {
            try {
                if (stream.readFrame() == null) {
                    return;
                }
                stream.closeFrame();
            } catch (BitstreamException e) {
                return;
            }
        }
    }
}

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

package com.adeptum.paula.module.ape;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import com.adeptum.paula.module.pcm.PcmEncoding;
import com.adeptum.paula.module.pcm.PcmRenderer;
import de.quippy.jmac.decoder.IAPEDecompress;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Decodes one Monkey's Audio file a quarter of a second at a time. The decoder seeks by itself, so a seek is
 * handed to it rather than worked around as the frame-at-a-time formats have to.
 */
@Slf4j
public final class ApeRenderer extends PcmRenderer {

    private static final int MILLIS_PER_BLOCK = 250;
    private static final int MILLIS = 1000;

    private final byte[] file;
    private final String name;
    private final ApeAudio audio;
    private final PcmEncoding encoding;
    private final byte[] decoded;
    private final int blocks;

    private IAPEDecompress decoder;

    public ApeRenderer(byte[] file, String name, ApeAudio audio, int outputRate) {
        super(audio.rate(), audio.channels(), outputRate);
        this.file = file;
        this.name = name;
        this.audio = audio;
        this.encoding = new PcmEncoding(audio.bits(), LITTLE_ENDIAN,
                audio.bits() == Byte.SIZE ? PcmEncoding.Kind.UNSIGNED : PcmEncoding.Kind.SIGNED);
        this.blocks = MILLIS_PER_BLOCK * audio.rate() / MILLIS;
        this.decoded = new byte[blocks * audio.channels() * encoding.bytes()];
        open(0);
    }

    @Override
    public Optional<Duration> length() {
        return audio.length();
    }

    @Override
    protected void rewind(Duration target) {
        open(audio.blockAt(target));
    }

    @Override
    protected short[] decode() {
        if (decoder == null) {
            return null;
        }
        try {
            final int read = decoder.GetData(decoded, blocks);
            return read <= 0 ? null : samples(read);
        } catch (IOException | RuntimeException e) {
            log.debug("The Monkey's Audio stream {} ends in the middle of a frame", name, e);
            return null;
        }
    }

    private short[] samples(int read) {
        final short[] samples = new short[read * audio.channels()];
        for (int sample = 0; sample < samples.length; sample++) {
            samples[sample] = encoding.sampleAt(decoded, sample * encoding.bytes());
        }
        return samples;
    }

    private void open(int block) {
        try {
            decoder = ApeAudio.open(file, name);
            if (block > 0) {
                decoder.Seek(block);
            }
        } catch (IOException | RuntimeException e) {
            log.debug("The Monkey's Audio stream {} cannot be read from block {}", name, block, e);
            decoder = null;
        }
    }
}

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

import de.quippy.jflac.FLACDecoder;
import de.quippy.jflac.metadata.StreamInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * What the STREAMINFO block at the head of a FLAC file says about the stream. A file that leaves the sample
 * count at zero, which is how a stream written as it was recorded ends up, plays without a known length rather
 * than not at all.
 */
public record FlacAudio(int rate, int channels, int bits, long totalSamples) {

    /**
     * The whole depth range the format allows, since anything but sixteen bits is a shift away from it.
     */
    private static final int MIN_BITS = 4;
    private static final int MAX_BITS = 32;

    private static final int MILLIS = 1000;

    /**
     * Reads past the metadata blocks so the decoder stands at the first frame and knows its stream info, Vorbis
     * comment and seek table.
     */
    static FLACDecoder open(byte[] file) throws IOException {
        final FLACDecoder decoder = new FLACDecoder(new ByteArrayInputStream(file));
        decoder.readMetadata();
        return decoder;
    }

    static FlacAudio of(StreamInfo info) {
        return new FlacAudio(info.getSampleRate(), info.getChannels(), info.getBitsPerSample(),
                info.getTotalSamples());
    }

    boolean isPlayable() {
        return rate > 0 && channels > 0 && bits >= MIN_BITS && bits <= MAX_BITS;
    }

    public Optional<Duration> length() {
        return totalSamples == 0 ? Optional.empty() : Optional.of(Duration.ofMillis(totalSamples * MILLIS / rate));
    }

    public int seconds() {
        return (int) (totalSamples / rate);
    }

    public long sampleAt(Duration position) {
        return position.isNegative() ? 0 : position.toMillis() * rate / MILLIS;
    }

    public String describe() {
        return rate + " Hz, " + bits + " bit, " + channelling();
    }

    private String channelling() {
        return switch (channels) {
            case 1 -> "mono";
            case 2 -> "stereo";
            default -> channels + " channels";
        };
    }
}

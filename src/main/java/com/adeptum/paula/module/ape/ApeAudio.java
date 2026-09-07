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

import de.quippy.jmac.decoder.IAPEDecompress;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * What the header of a Monkey's Audio file says about the stream it holds.
 */
public record ApeAudio(int rate, int channels, int bits, int totalBlocks) {

    private static final int MIN_BITS = 8;
    private static final int MAX_BITS = 32;
    private static final int MILLIS = 1000;

    static IAPEDecompress open(byte[] file, String name) throws IOException {
        return IAPEDecompress.CreateIAPEDecompress(new ApeBytes(file, name));
    }

    static ApeAudio of(IAPEDecompress decoder) {
        return new ApeAudio(decoder.getApeInfoSampleRate(), decoder.getApeInfoChannels(),
                decoder.getApeInfoBitsPerSample(), decoder.getApeInfoTotalBlocks());
    }

    boolean isPlayable() {
        return rate > 0 && channels > 0 && bits >= MIN_BITS && bits <= MAX_BITS && bits % Byte.SIZE == 0;
    }

    public Optional<Duration> length() {
        return totalBlocks == 0 ? Optional.empty()
                : Optional.of(Duration.ofMillis((long) totalBlocks * MILLIS / rate));
    }

    public int seconds() {
        return totalBlocks / rate;
    }

    public int blockAt(Duration position) {
        return position.isNegative() ? 0 : (int) Math.min(totalBlocks, position.toMillis() * rate / MILLIS);
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

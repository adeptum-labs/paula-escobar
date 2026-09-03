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

package com.adeptum.paula.module.ogg;

import de.quippy.ogg.jorbis.Info;
import de.quippy.ogg.jorbis.VorbisFile;
import java.time.Duration;

/**
 * What the identification header of one Vorbis stream says it is, together with the sample its last page ends
 * on, which gives an exact length without decoding anything.
 */
public record OggAudio(int rate, int channels, int bitrate, long frames) {

    private static final int MILLIS = 1000;
    private static final int BITS_PER_KILOBIT = 1000;

    static OggAudio of(VorbisFile vorbis) {
        final Info info = vorbis.getInfo()[0];
        return new OggAudio(info.rate, info.channels, vorbis.bitrate(-1) / BITS_PER_KILOBIT, vorbis.pcm_total(-1));
    }

    boolean isPlayable() {
        return rate > 0 && channels > 0 && frames > 0;
    }

    public Duration length() {
        return Duration.ofMillis(frames * MILLIS / rate);
    }

    public int seconds() {
        return (int) (frames / rate);
    }

    /**
     * The sample a moment of the song falls on, clamped to the samples there are.
     */
    public long frameAt(Duration position) {
        return position.isNegative() ? 0 : Math.min(frames, position.toMillis() * rate / MILLIS);
    }

    public String describe() {
        return bitrate + " kbit/s, " + rate + " Hz, " + channelling();
    }

    private String channelling() {
        return switch (channels) {
            case 1 -> "mono";
            case 2 -> "stereo";
            default -> channels + " channels";
        };
    }
}

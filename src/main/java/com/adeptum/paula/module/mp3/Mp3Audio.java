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

import de.quippy.mp3.decoder.Bitstream;
import de.quippy.mp3.decoder.BitstreamException;
import de.quippy.mp3.decoder.Header;
import java.io.ByteArrayInputStream;
import java.time.Duration;

/**
 * What the frames of one MPEG audio file add up to. The whole file is walked once for this, reading the header
 * of every frame and stepping over its data, so that a stream whose bitrate varies is still measured exactly.
 */
public record Mp3Audio(int rate, int channels, double frameMillis, int frames, int bitrate, Duration length) {

    private static final int MILLIS = 1000;
    private static final int BITS_PER_KILOBIT = 1000;

    static Mp3Audio of(byte[] file, int from) throws BitstreamException {
        final Bitstream stream = open(file, from);
        final Header first = stream.readFrame();
        if (first == null) {
            return null;
        }
        final int rate = first.frequency();
        final int channels = first.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
        final double frameMillis = first.ms_per_frame();
        double millis = 0;
        long bits = 0;
        int frames = 0;
        for (Header header = first; header != null; header = stream.readFrame()) {
            millis += header.ms_per_frame();
            bits += header.bitrate();
            frames++;
            stream.closeFrame();
        }
        return new Mp3Audio(rate, channels, frameMillis, frames, (int) (bits / frames / BITS_PER_KILOBIT),
                Duration.ofMillis(Math.round(millis)));
    }

    static Bitstream open(byte[] file, int from) {
        return new Bitstream(new ByteArrayInputStream(file, from, file.length - from));
    }

    public int seconds() {
        return (int) (length.toMillis() / MILLIS);
    }

    /**
     * The frame a moment of the song falls in, which is as near as a seek can land without decoding.
     */
    public int frameAt(Duration position) {
        return (int) Math.min(frames, position.toMillis() / frameMillis);
    }

    public String describe() {
        return bitrate + " kbit/s, " + rate + " Hz, " + (channels == 1 ? "mono" : "stereo");
    }
}

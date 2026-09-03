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

package com.adeptum.paula.module.wav;

import java.time.Duration;

/**
 * Where the samples of one wave file are and what they sound like: the run of bytes holding them, how they are
 * encoded, how many channels they are woven into and how fast they run.
 */
public record WavAudio(int rate, int channels, WavEncoding encoding, int from, int bytes) {

    private static final int MILLIS = 1000;

    public int frames() {
        return bytes / (channels * encoding.bytes());
    }

    public Duration length() {
        return Duration.ofMillis((long) frames() * MILLIS / rate);
    }

    public int seconds() {
        return (int) (length().toMillis() / MILLIS);
    }

    /**
     * The frame a moment of the song falls in, which a seek can land on exactly since every frame is the same
     * width.
     */
    public int frameAt(Duration position) {
        return (int) Math.min(frames(), position.toMillis() * rate / MILLIS);
    }

    public short sampleAt(byte[] file, int sample) {
        return encoding.sampleAt(file, from + sample * encoding.bytes());
    }

    public String describe() {
        return encoding.describe() + ", " + rate + " Hz, " + voices();
    }

    private String voices() {
        return switch (channels) {
            case 1 -> "mono";
            case 2 -> "stereo";
            default -> channels + " channels";
        };
    }
}

/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

package com.adeptum.paula.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public final class JavaSoundSink implements AudioSink {

    private static final int CHANNELS = 2;
    private static final int BYTES_PER_FRAME = CHANNELS * Short.BYTES;

    private SourceDataLine line;
    private byte[] bytes = new byte[0];

    @Override
    public void open(int sampleRate) throws AudioException {
        final AudioFormat format = new AudioFormat(sampleRate, Short.SIZE, CHANNELS, true, false);
        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new AudioException("No audio output available for " + format, e);
        }
    }

    @Override
    public void write(short[] interleavedStereo, int frames) {
        final int byteCount = frames * BYTES_PER_FRAME;
        if (bytes.length < byteCount) {
            bytes = new byte[byteCount];
        }
        for (int i = 0; i < frames * CHANNELS; i++) {
            bytes[i * 2] = (byte) interleavedStereo[i];
            bytes[i * 2 + 1] = (byte) (interleavedStereo[i] >> 8);
        }
        line.write(bytes, 0, byteCount);
    }

    @Override
    public void close() {
        if (line != null) {
            line.drain();
            line.close();
        }
    }
}

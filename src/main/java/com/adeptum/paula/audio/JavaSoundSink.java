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

/**
 * Plays through Java Sound. Only usable on a JVM; the native executable has no Java Sound providers.
 */
public final class JavaSoundSink implements AudioSink {

    private SourceDataLine line;
    private byte[] bytes = new byte[0];

    @Override
    public void open(int sampleRate) throws AudioException {
        final AudioFormat format = new AudioFormat(sampleRate, Short.SIZE, Pcm.CHANNELS, true, false);
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
        bytes = Pcm.toLittleEndian(interleavedStereo, frames, bytes);
        line.write(bytes, 0, frames * Pcm.BYTES_PER_FRAME);
    }

    @Override
    public void close() {
        if (line != null) {
            line.drain();
            line.close();
        }
    }
}

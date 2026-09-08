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
 *
 * How the encoder delay and the information frame are handled follows
 * Load_mo3.cpp of OpenMPT, Copyright © 2004-2026 the OpenMPT project
 * developers and Copyright © 1997-2003 Olivier Lapicque, licensed under the
 * three-clause BSD licence and used here under the GNU General Public
 * License.
 */

package com.adeptum.paula.module.mo3;

import de.quippy.mp3.decoder.Bitstream;
import de.quippy.mp3.decoder.Decoder;
import de.quippy.mp3.decoder.Header;
import de.quippy.mp3.decoder.SampleBuffer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Unpacks a waveform an MO3 kept as MPEG audio, which is what it does with the samples it may lose a little
 * of to save a lot of room.
 *
 * <p>The encoder leaves silence on the front of what it writes, and the sample header says how much of it to
 * drop. Some encoders also left an information frame at the head of the stream: it sounds nothing but still
 * counts towards that silence, so where one is found it is dropped and the silence measured from after it.</p>
 */
final class Mo3Mpeg {

    /**
     * The tags an information frame is known by, which sit within the first frame of a stream that has one.
     */
    private static final String[] INFORMATION_TAGS = {"Xing", "Info", "VBRI"};

    /**
     * How far into the stream an information tag can sit, which is past the longest header and side
     * information a frame may carry.
     */
    private static final int TAG_WITHIN = 200;

    private static final int BYTES_PER_SAMPLE = 2;

    private Mo3Mpeg() {
    }

    /**
     * Decodes the stream into the waveform, filling as much of it as the stream reaches.
     */
    static void unpack(byte[] file, int at, int length, int[][] waveform, int encoderDelay) {
        final Bitstream stream = new Bitstream(new ByteArrayInputStream(file, at, length));
        final Decoder decoder = new Decoder();
        int skip = encoderDelay / BYTES_PER_SAMPLE;
        boolean first = hasInformationFrame(file, at, length);
        int written = 0;

        try {
            Header header;
            while (written < waveform[0].length && (header = stream.readFrame()) != null) {
                final SampleBuffer decoded = (SampleBuffer) decoder.decodeFrame(header, stream);
                final short[] samples = decoded.getBuffer();
                final int channels = decoded.getChannelCount();
                final int frames = decoded.getBufferLength() / channels;
                stream.closeFrame();

                if (first) {
                    first = false;
                    skip = Math.max(0, skip - frames);
                    continue;
                }
                written = append(waveform, samples, channels, frames, skipped(skip, frames), written);
                skip = Math.max(0, skip - frames);
            }
        } catch (Exception endsHere) {
            // A stream that ends early or will not decode is played as far as it reached.
        }
    }

    private static int skipped(int skip, int frames) {
        return Math.min(skip, frames);
    }

    private static int append(int[][] waveform, short[] samples, int channels, int frames, int from, int written) {
        for (int frame = from; frame < frames && written < waveform[0].length; frame++, written++) {
            for (int channel = 0; channel < waveform.length; channel++) {
                waveform[channel][written] = samples[frame * channels + Math.min(channel, channels - 1)];
            }
        }
        return written;
    }

    private static boolean hasInformationFrame(byte[] file, int at, int length) {
        final String head = new String(file, at, Math.min(length, TAG_WITHIN), StandardCharsets.ISO_8859_1);
        for (final String tag : INFORMATION_TAGS) {
            if (head.contains(tag)) {
                return true;
            }
        }
        return false;
    }
}

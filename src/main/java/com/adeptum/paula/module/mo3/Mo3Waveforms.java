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
 * Where the waveforms sit and how they are packed follows Load_mo3.cpp of
 * OpenMPT, Copyright © 2004-2026 the OpenMPT project developers and
 * Copyright © 1997-2003 Olivier Lapicque, licensed under the three-clause
 * BSD licence and used here under the GNU General Public License.
 */

package com.adeptum.paula.module.mo3;

import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import java.util.List;

/**
 * Reads the waveforms out of the half of an MO3 the music chunk does not cover.
 *
 * <p>They follow one another in the order the samples were named, each taking the room its header says, so
 * finding one is a matter of stepping over everything before it. Each is packed one of several ways, or not at
 * all, and a sample may name one already read rather than carry a waveform of its own.</p>
 */
final class Mo3Waveforms {

    private static final int EIGHT_BIT_SHIFT = 24;
    private static final int SIXTEEN_BIT_SHIFT = 16;

    private Mo3Waveforms() {
    }

    static void read(InstrumentsContainer container, Mo3File file, int modType) {
        final List<Mo3Sample> samples = file.samples();
        int at = file.sampleData();
        for (int index = 0; index < samples.size(); index++) {
            final Mo3Sample mo3 = samples.get(index);
            final Sample sample = container.getSample(index);
            if (mo3.isDuplicate()) {
                copyWaveform(container, samples, index);
            } else if (sample.sampleLength > 0) {
                at = readWaveform(file, mo3, sample, at, modType);
            }
        }
    }

    /**
     * A waveform of no compression at all takes the room its samples need; anything packed takes the room its
     * header states, whether or not this reads the packing it was given.
     */
    private static int readWaveform(Mo3File file, Mo3Sample mo3, Sample sample, int at, int modType) {
        final int packed = mo3.compressedSize();
        final int length = packed > 0 ? packed : mo3.length() * bytesPerSample(mo3) * mo3.channels();
        if (at < 0 || length < 0 || at + length > file.file().length) {
            return at;
        }

        final boolean wide = mo3.has(Mo3Sample.SIXTEEN_BIT);
        final int[][] waveform = new int[mo3.channels()][mo3.length()];
        switch (mo3.compression()) {
            case 0 -> plain(file.file(), at, waveform, wide);
            case Mo3Sample.DELTA -> Mo3Delta.unpack(new Mo3Bits(file.file(), at, length), waveform, wide, false);
            case Mo3Sample.DELTA_PREDICTION ->
                    Mo3Delta.unpack(new Mo3Bits(file.file(), at, length), waveform, wide, true);
            case Mo3Sample.MPEG -> {
                Mo3Mpeg.unpack(file.file(), at, length, waveform, mo3.encoderDelay());
                keep(sample, waveform, true, modType);
                return at + length;
            }
            default -> {
                return at + length;
            }
        }
        keep(sample, waveform, wide, modType);
        return at + length;
    }

    private static void plain(byte[] file, int at, int[][] waveform, boolean wide) {
        for (final int[] channel : waveform) {
            for (int sample = 0; sample < channel.length; sample++) {
                channel[sample] = wide ? (short) (file[at] & 0xFF | file[at + 1] << Byte.SIZE) : file[at];
                at += wide ? Short.BYTES : 1;
            }
        }
    }

    /**
     * A sample the format says shares a waveform names one already read, counted back from itself.
     */
    private static void copyWaveform(InstrumentsContainer container, List<Mo3Sample> samples, int index) {
        final int from = index + samples.get(index).compressedSize();
        if (from < 0 || from >= index) {
            return;
        }
        final Sample sample = container.getSample(index);
        final Sample shared = container.getSample(from);
        sample.sampleL = shared.sampleL;
        sample.sampleR = shared.sampleR;
    }

    private static void keep(Sample sample, int[][] waveform, boolean wide, int modType) {
        sample.allocSampleData();
        final int shift = wide ? SIXTEEN_BIT_SHIFT : EIGHT_BIT_SHIFT;
        for (int at = 0; at < waveform[0].length; at++) {
            sample.sampleL[at] = (long) waveform[0][at] << shift;
        }
        if (sample.sampleR != null && waveform.length > 1) {
            for (int at = 0; at < waveform[1].length; at++) {
                sample.sampleR[at] = (long) waveform[1][at] << shift;
            }
        }
        sample.fixSampleLoops(modType);
    }

    private static int bytesPerSample(Mo3Sample mo3) {
        return mo3.has(Mo3Sample.SIXTEEN_BIT) ? Short.BYTES : 1;
    }
}

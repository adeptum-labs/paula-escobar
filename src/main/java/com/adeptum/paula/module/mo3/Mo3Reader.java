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
 * The layout follows Load_mo3.cpp of OpenMPT, Copyright © 2004-2026 the
 * OpenMPT project developers and Copyright © 1997-2003 Olivier Lapicque,
 * licensed under the three-clause BSD licence and used here under the GNU
 * General Public License.
 */

package com.adeptum.paula.module.mo3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an MO3 module: the compressed half is unpacked and then walked through in the one order it can be, the
 * song, its orders, its tracks, its instruments and its samples each following the last with nothing saying
 * where any of them begins.
 *
 * <p>Anything the file states that the file cannot hold is refused rather than guessed at, so a truncated or
 * mangled module says what was wrong with it instead of playing as noise.</p>
 */
final class Mo3Reader {

    /**
     * Version five gave instruments and samples a file name of their own beside their name, and gave the
     * samples that share an Ogg header the number of the sample holding it.
     */
    static final int NEWEST_LAYOUT_FROM = 5;

    /**
     * A track can hold fifteen events per row over the longest pattern the format allows, which with the
     * status byte of each row leaves this much room for error.
     */
    private static final int LARGEST_TRACK = 0x20_0000;

    /**
     * The sixteen macros a module can bind to the letter Z and the hundred and twenty-eight fixed ones after
     * them, which nothing here sounds.
     */
    private static final int MACRO_BYTES = 16 + 128 * 2;

    private Mo3Reader() {
    }

    static Mo3File read(byte[] file) throws IOException {
        final Mo3Container container = Mo3Container.read(file);
        final Mo3Bytes music = new Mo3Bytes(container.music());

        final String name = music.textZ();
        final String message = music.textZ();
        final Mo3Song song = readSong(music);

        final int[] orders = readOrders(music, song);
        final List<Mo3Pattern> patterns = readPatterns(music, song);
        final List<byte[]> tracks = readTracks(music, song);
        final List<Mo3Instrument> instruments = readInstruments(music, container.version(), song);
        final List<Mo3Sample> samples = readSamples(music, container.version(), song);

        return new Mo3File(container.version(), name, message, song, orders, patterns, tracks, instruments,
                samples, file, container.sampleData());
    }

    private static Mo3Song readSong(Mo3Bytes music) throws IOException {
        final int channels = music.u8();
        final int orders = music.u16();
        final int restart = music.u16();
        final int patterns = music.u16();
        final int tracks = music.u16();
        final int instruments = music.u16();
        final int samples = music.u16();
        final int speed = music.u8();
        final int tempo = music.u8();
        final int flags = music.s32();
        final int globalVolume = music.u8();
        final int panSeparation = music.u8();
        final int sampleVolume = music.s8();
        final int[] channelVolume = channelBytes(music);
        final int[] channelPanning = channelBytes(music);
        music.skip(MACRO_BYTES);

        if (channels == 0 || channels > Mo3Song.CHANNELS_IN_HEADER) {
            throw new IOException("The module says it has " + channels + " channels, which no tracker wrote");
        }
        if (restart > orders) {
            throw new IOException("The module restarts at order " + restart + " of " + orders);
        }
        return new Mo3Song(channels, orders, restart, patterns, tracks, instruments, samples, speed, tempo,
                flags, globalVolume, panSeparation, sampleVolume, channelVolume, channelPanning);
    }

    private static int[] channelBytes(Mo3Bytes music) throws IOException {
        final int[] values = new int[Mo3Song.CHANNELS_IN_HEADER];
        for (int channel = 0; channel < values.length; channel++) {
            values[channel] = music.u8();
        }
        return values;
    }

    private static int[] readOrders(Mo3Bytes music, Mo3Song song) throws IOException {
        final int[] orders = new int[song.orders()];
        for (int order = 0; order < orders.length; order++) {
            orders[order] = music.u8();
        }
        return orders;
    }

    private static List<Mo3Pattern> readPatterns(Mo3Bytes music, Mo3Song song) throws IOException {
        final int assignments = song.patterns() * song.channels() * Short.BYTES;
        if (!music.has(assignments + song.patterns() * Short.BYTES)) {
            throw new IOException("The module says it has " + song.patterns() + " patterns it has no room for");
        }
        final int[][] trackFor = new int[song.patterns()][song.channels()];
        for (int[] pattern : trackFor) {
            for (int channel = 0; channel < pattern.length; channel++) {
                pattern[channel] = music.u16();
            }
        }
        final List<Mo3Pattern> patterns = new ArrayList<>(song.patterns());
        for (int[] pattern : trackFor) {
            patterns.add(new Mo3Pattern(music.u16(), pattern));
        }
        return patterns;
    }

    private static List<byte[]> readTracks(Mo3Bytes music, Mo3Song song) throws IOException {
        final List<byte[]> tracks = new ArrayList<>(song.tracks());
        for (int track = 0; track < song.tracks(); track++) {
            final int length = music.u32();
            if (length >= LARGEST_TRACK) {
                throw new IOException("A track of " + length + " bytes is longer than a pattern can be");
            }
            tracks.add(music.bytes(length));
        }
        return tracks;
    }

    private static List<Mo3Instrument> readInstruments(Mo3Bytes music, int version, Mo3Song song)
            throws IOException {
        final List<Mo3Instrument> instruments = new ArrayList<>(song.instruments());
        for (int instrument = 0; instrument < song.instruments(); instrument++) {
            final String name = music.textZ();
            final String fileName = version >= NEWEST_LAYOUT_FROM ? music.textZ() : "";
            instruments.add(Mo3Instrument.read(music, name, fileName));
        }
        return instruments;
    }

    private static List<Mo3Sample> readSamples(Mo3Bytes music, int version, Mo3Song song) throws IOException {
        final List<Mo3Sample> samples = new ArrayList<>(song.samples());
        for (int sample = 0; sample < song.samples(); sample++) {
            final String name = music.textZ();
            final String fileName = version >= NEWEST_LAYOUT_FROM ? music.textZ() : "";
            samples.add(Mo3Sample.read(music, version, name, fileName));
        }
        return samples;
    }
}

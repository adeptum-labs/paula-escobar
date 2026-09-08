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

/**
 * An instrument of an MO3: which sample every key plays and at what note, three envelopes, and the settings
 * that decide what happens when a note is cut short or another lands on top of it.
 *
 * <p>Fast Tracker keeps no note map and reaches its samples an octave up, so there the map is read for its
 * samples alone.</p>
 */
record Mo3Instrument(String name, String fileName, int flags, int[] sampleFor, int[] noteFor,
                     Mo3Envelope volume, Mo3Envelope panning, Mo3Envelope pitch, Mo3Vibrato vibrato,
                     int fadeOut, Mo3Midi midi, int globalVolume, int panningValue, int newNoteAction,
                     int pitchPanSeparation, int pitchPanCentre, int duplicateCheck, int duplicateAction,
                     int volumeSwing, int panningSwing, int cutoff, int resonance) {

    static final int PLAY_ON_MIDI = 0x01;
    static final int MUTE = 0x02;

    /**
     * A key map covers ten octaves; Fast Tracker uses the eight from the first octave up.
     */
    static final int KEYS = 120;
    static final int FAST_TRACKER_KEYS = 96;
    static final int FAST_TRACKER_FIRST_KEY = 12;

    /**
     * Panning, a filter cutoff and a filter resonance are only meant when the format says so, and say so by
     * exceeding what they otherwise hold.
     */
    static final int PANNING_UNSET = 0xFFFF;
    static final int LARGEST_PANNING = 256;
    static final int FILTER_ENABLED = 0x80;
    static final int FILTER_VALUE = 0x7F;

    static Mo3Instrument read(Mo3Bytes bytes, String name, String fileName) throws IOException {
        final int flags = bytes.s32();
        final int[] sampleFor = new int[KEYS];
        final int[] noteFor = new int[KEYS];
        for (int key = 0; key < KEYS; key++) {
            noteFor[key] = bytes.u16();
            sampleFor[key] = bytes.u16();
        }
        final Mo3Envelope volume = Mo3Envelope.read(bytes);
        final Mo3Envelope panning = Mo3Envelope.read(bytes);
        final Mo3Envelope pitch = Mo3Envelope.read(bytes);
        final Mo3Vibrato vibrato = Mo3Vibrato.read(bytes);
        final int fadeOut = bytes.u16();
        final Mo3Midi midi = Mo3Midi.read(bytes);
        final int globalVolume = bytes.u8();
        final int panningValue = bytes.u16();
        final int newNoteAction = bytes.u8();
        final int pitchPanSeparation = bytes.u8();
        final int pitchPanCentre = bytes.u8();
        final int duplicateCheck = bytes.u8();
        final int duplicateAction = bytes.u8();
        final int volumeSwing = bytes.u16();
        final int panningSwing = bytes.u16();
        final int cutoff = bytes.u8();
        final int resonance = bytes.u8();

        return new Mo3Instrument(name, fileName, flags, sampleFor, noteFor, volume, panning, pitch, vibrato,
                fadeOut, midi, globalVolume, panningValue, newNoteAction, pitchPanSeparation, pitchPanCentre,
                duplicateCheck, duplicateAction, volumeSwing, panningSwing, cutoff, resonance);
    }

    boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /**
     * How MIDI reaches the instrument. A channel past the sixteen the protocol has names a plugin slot rather
     * than a channel.
     */
    record Mo3Midi(int channel, int bank, int patch, int bend) {

        static final int CHANNELS = 16;
        static final int FIRST_PLUGIN_CHANNEL = 128;

        static Mo3Midi read(Mo3Bytes bytes) throws IOException {
            return new Mo3Midi(bytes.u8(), bytes.u8(), bytes.u8(), bytes.u8());
        }
    }
}

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
 * The replay follows the MED loaders and med_extras.c of libxmp,
 * Copyright © 1996-2026 Claudio Matsuoka and Hipolito Carraro Jr,
 * licensed under the MIT licence and used here under the GNU General
 * Public License.
 */

package com.adeptum.paula.module.med;

import java.util.List;

/**
 * An instrument: the samples it plays and how, or the machinery of a synthetic one, together with what the
 * player has to know before a note of it sounds.
 *
 * <p>A multi-octave instrument holds one sample per octave, each twice the length of the one below, and a
 * table saying which of them a given octave of the keyboard plays and how far it is then transposed.
 */
record MedInstrument(String name, List<MedLayer> layers, int octaves, int volume, int transpose, int finetune,
                     int hold, int decay, int midiChannel, MedSynthInstrument synth) {

    /**
     * Which of the octave samples each octave of the keyboard plays, and how far that sample is then moved,
     * for instruments of two to seven octaves.
     */
    private static final int[][] SAMPLE_OF_OCTAVE = {
        {1, 1, 1, 0, 0, 0, 0, 0, 0},
        {2, 2, 2, 2, 2, 2, 1, 1, 0},
        {3, 3, 3, 2, 2, 2, 1, 1, 0},
        {4, 4, 4, 3, 2, 2, 1, 1, 0},
        {5, 5, 5, 5, 4, 3, 2, 1, 0},
        {6, 6, 6, 6, 5, 4, 3, 2, 1}};

    private static final int[][] TRANSPOSE_OF_OCTAVE = {
        {12, 12, 12, 0, 0, 0, 0, 0, 0},
        {12, 12, 12, 12, 12, 12, 0, 0, -12},
        {12, 12, 12, 0, 0, 0, -12, -12, -24},
        {24, 24, 24, 12, 0, 0, -12, -24, -36},
        {12, 12, 12, 12, 0, -12, -24, -36, -48},
        {12, 12, 12, 12, 0, -12, -24, -36, -48}};

    private static final int FEWEST_OCTAVES = 2;
    private static final int KEYBOARD_OCTAVES = 9;

    MedInstrument {
        layers = List.copyOf(layers);
    }

    /**
     * A MIDI instrument names a sound on a keyboard rather than one in the file; it keeps its name and number
     * so the panel stays honest, and sounds nothing.
     */
    boolean isSilent() {
        return layers.isEmpty() && synth == null;
    }

    boolean isMultiOctave() {
        return octaves >= FEWEST_OCTAVES && layers.size() >= octaves;
    }

    MedLayer layerAt(int note) {
        if (layers.isEmpty()) {
            return null;
        }
        return isMultiOctave() ? layers.get(SAMPLE_OF_OCTAVE[row()][keyboardOctave(note)]) : layers.getFirst();
    }

    /**
     * A multi-octave instrument sits an octave below a plain one before its own table moves it, which is how
     * libxmp tunes the two apart.
     */
    int transposeAt(int note) {
        return isMultiOctave()
                ? TRANSPOSE_OF_OCTAVE[row()][keyboardOctave(note)] - MedTables.NOTES_PER_OCTAVE : 0;
    }

    private int row() {
        return Math.min(octaves, SAMPLE_OF_OCTAVE.length + 1) - FEWEST_OCTAVES;
    }

    /**
     * The table is written against the note an octave above the one OctaMED stores, which is where libxmp
     * puts a MED note before it looks anything up.
     */
    private static int keyboardOctave(int note) {
        return Math.clamp((note + MedTables.NOTES_PER_OCTAVE) / MedTables.NOTES_PER_OCTAVE, 0,
                KEYBOARD_OCTAVES - 1);
    }
}

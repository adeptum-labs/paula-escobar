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

package com.adeptum.paula.module.med;

/**
 * An instrument: the sample it plays and how, or the machinery of a synthetic one, together with what the
 * player has to know before a note of it sounds.
 */
record MedInstrument(String name, short[] sample, int loopStart, int loopLength, int volume, int transpose,
                     int finetune, int hold, int decay, int octaves, int midiChannel,
                     MedSynthInstrument synth) {

    /**
     * A MIDI instrument names an instrument on a keyboard rather than a sound in the file; it keeps its name
     * and number so the panel stays honest, and sounds nothing.
     */
    boolean isSilent() {
        return sample == null && synth == null;
    }

    boolean loops() {
        return loopLength > 1;
    }
}

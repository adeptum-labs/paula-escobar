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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MedInstrumentTest {

    private static final int OCTAVES = 5;
    private static final int LOWEST_OCTAVE = 1;
    private static final int FOURTH_OCTAVE = 37;
    private static final int SEVENTH_OCTAVE = 73;

    private static MedInstrument multiOctave() {
        final List<MedLayer> layers = new ArrayList<>();
        for (int octave = 0; octave < OCTAVES; octave++) {
            layers.add(new MedLayer(new short[1 << octave], 0, 0));
        }
        return new MedInstrument("octaves", layers, OCTAVES, 64, 0, 0, 0, 0, 0, null);
    }

    private static MedInstrument plain() {
        return new MedInstrument("square", List.of(new MedLayer(new short[64], 0, 32)), 1, 64, 0, 0, 0, 0, 0, null);
    }

    /**
     * A multi-octave instrument holds a sample for every octave and a table saying which of them each octave
     * of the keyboard reaches for, so the same note played high does not simply run its sample faster.
     */
    @Test
    void picksTheSampleTheOctaveOfTheNoteAsksFor() {
        final MedInstrument instrument = multiOctave();

        assertSame(instrument.layers().get(4), instrument.layerAt(LOWEST_OCTAVE), "the lowest plays the longest");
        assertSame(instrument.layers().get(2), instrument.layerAt(FOURTH_OCTAVE));
        assertSame(instrument.layers().get(1), instrument.layerAt(SEVENTH_OCTAVE));
    }

    @Test
    void transposesWhateverSampleItLandedOn() {
        final MedInstrument instrument = multiOctave();

        assertEquals(12, instrument.transposeAt(LOWEST_OCTAVE));
        assertEquals(-12, instrument.transposeAt(FOURTH_OCTAVE));
        assertEquals(-36, instrument.transposeAt(SEVENTH_OCTAVE));
    }

    @Test
    void givesAPlainInstrumentItsOneSampleWhateverIsPlayed() {
        final MedInstrument instrument = plain();

        assertFalse(instrument.isMultiOctave());
        assertSame(instrument.layerAt(LOWEST_OCTAVE), instrument.layerAt(SEVENTH_OCTAVE));
        assertEquals(0, instrument.transposeAt(SEVENTH_OCTAVE), "and moves it nowhere");
        assertTrue(instrument.layerAt(LOWEST_OCTAVE).loops());
    }

    @Test
    void soundsNothingWhereThereIsNoSampleAtAll() {
        final MedInstrument midi = new MedInstrument("keyboard", List.of(), 0, 64, 0, 0, 0, 0, 1, null);

        assertTrue(midi.isSilent());
        assertNull(midi.layerAt(LOWEST_OCTAVE));
    }
}

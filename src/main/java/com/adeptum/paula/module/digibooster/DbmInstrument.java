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

package com.adeptum.paula.module.digibooster;

/**
 * An instrument: which sample it plays, how it loops and how loud and where in the stereo field it starts.
 */
record DbmInstrument(String name, int sample, int volume, int panning, int c3Frequency, int loopStart,
                     int loopLength, int flags, int volumeEnvelope, int panningEnvelope) {

    static final int NO_LOOP = 0;
    static final int FORWARD_LOOP = 1;
    static final int PINGPONG_LOOP = 2;
    static final int LOOP_MASK = 3;

    int loopType() {
        return flags & LOOP_MASK;
    }

    DbmInstrument withSampleAndLoop(int sampleIndex, int start, int length, int looping) {
        return new DbmInstrument(name, sampleIndex, volume, panning, c3Frequency, start, length, looping,
                volumeEnvelope, panningEnvelope);
    }

    DbmInstrument withEnvelopes(int volumeEnvelopeIndex, int panningEnvelopeIndex) {
        return new DbmInstrument(name, sample, volume, panning, c3Frequency, loopStart, loopLength, flags,
                volumeEnvelopeIndex, panningEnvelopeIndex);
    }
}

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
 *
 * The interpolation follows player.c of libdigibooster3, Copyright © 2014
 * Grzegorz Kraszewski, licensed under the two-clause BSD licence and used
 * here under the GNU General Public License.
 */

package com.adeptum.paula.module.digibooster;

/**
 * Walks one instrument's envelope while it plays, a section at a time, holding at a sustain point until the
 * note is released and jumping back at the end of the loop.
 */
final class DbmEnvelopeState {

    private int envelope = DbmEnvelope.NONE;
    private int section;
    private int ticksLeft;
    private int sustainA = DbmEnvelope.NONE;
    private int sustainB = DbmEnvelope.NONE;
    private int loopEnd = DbmEnvelope.NONE;
    private short sectionTicks;
    private short sectionRise;
    private short sectionStart;

    int envelope() {
        return envelope;
    }

    boolean playing() {
        return envelope != DbmEnvelope.NONE;
    }

    void use(int index) {
        envelope = index;
    }

    void rewind() {
        section = 0;
        ticksLeft = 0;
    }

    void trigger(DbmEnvelope points) {
        rewind();
        sustainA = points.sustainA();
        sustainB = points.sustainB();
        loopEnd = points.loopLast();
    }

    /**
     * A key off releases the envelope one hold at a time: the loop first, then the sustain points, latest one
     * last, so a note let go inside a loop still plays what the envelope has after it.
     */
    void release() {
        if (loopEnd <= sustainA && loopEnd <= sustainB) {
            loopEnd = DbmEnvelope.NONE;
        } else if (sustainA <= sustainB) {
            sustainA = DbmEnvelope.NONE;
        } else {
            sustainB = DbmEnvelope.NONE;
        }
    }

    /**
     * The value for this tick, in the envelope's own scale: eight bits below the point values for volume and
     * seven for panning, which is signed.
     */
    short next(DbmEnvelope points, int scale) {
        if (ticksLeft == 0) {
            if (section == loopEnd) {
                section = points.loopFirst();
            }
            sectionStart = (short) (points.values()[section] << scale);
            if (section == sustainA || section == sustainB || section >= points.sections()) {
                return sectionStart;
            }
            sectionTicks = (short) (points.positions()[section + 1] - points.positions()[section]);
            ticksLeft = sectionTicks;
            sectionRise = (short) ((points.values()[section + 1] << scale) - sectionStart);
            section++;
        }
        if (sectionTicks == 0) {
            return sectionStart;
        }
        return (short) (sectionStart + sectionRise * (sectionTicks - ticksLeft--) / sectionTicks);
    }
}

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

package com.adeptum.paula.module.flextrax;

/**
 * The settings for the two effects the Falcon's DSP laid over a FlexTrax mix, as the module stores them: the
 * raw slider positions, each from none to {@link #LOUDEST}, which the tracker turned into coefficients on its
 * way to the chip. The reverb fades over its decay and sounds at its level; the delay fades its repeats over
 * its decay, spaces them by its time, sounds them at its level, and trails the right channel behind the left
 * by its ping-pong, up to half the time between repeats.
 */
public record FlexEffects(int reverbDecay, int reverbLevel, int delayDecay, int delayTime, int delayPingPong,
                          int delayLevel) {

    /**
     * The top of every slider, and the point at which the two levels reach unity.
     */
    public static final int LOUDEST = 64;

    public boolean silent() {
        return reverbLevel == 0 && delayLevel == 0;
    }
}

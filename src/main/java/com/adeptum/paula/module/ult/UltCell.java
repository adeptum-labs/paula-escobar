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
 * The conversion follows Load_ult.cpp and modcommand.cpp of OpenMPT, Copyright © 2004-2026 the
 * OpenMPT project developers and Copyright © 1997-2003 Olivier Lapicque,
 * which ports Storlek's reader from Schism Tracker; licensed under the
 * three-clause BSD licence and used here under the GNU General Public
 * License.
 */

package com.adeptum.paula.module.ult;

/**
 * An UltraTracker event as Impulse Tracker holds it: an effect letter counted from one, a volume-column
 * command, whether the event stops the sample's loop, and a command that governs the whole song but found no
 * room in this event, to be written elsewhere on the row.
 */
public record UltCell(int effect, int param, int volumeEffect, int volumeParam, boolean keyOff, int lostEffect,
        int lostParam) {

    public static final int NO_EFFECT = 0;
    public static final int COLUMN_NONE = 0;
    public static final int COLUMN_VOLUME = 0x01;
    public static final int COLUMN_SLIDE_DOWN = 0x02;
    public static final int COLUMN_SLIDE_UP = 0x03;
    public static final int COLUMN_FINE_DOWN = 0x04;
    public static final int COLUMN_FINE_UP = 0x05;
    public static final int COLUMN_VIBRATO_DEPTH = 0x07;
    public static final int COLUMN_PANNING = 0x08;
    public static final int COLUMN_TONE_PORTAMENTO = 0x0B;
    public static final int COLUMN_PITCH_DOWN = 0x0C;
    public static final int COLUMN_PITCH_UP = 0x0D;
}

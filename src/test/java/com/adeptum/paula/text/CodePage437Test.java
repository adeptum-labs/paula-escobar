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

package com.adeptum.paula.text;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CodePage437Test {

    private static final byte[] DOS_NAME = {'O', 'h', ' ', 'H', (byte) 0x93, 'r', 't', ',', ' ', 'd', 'u', ' ', 'm', (byte) 0x8C, 'n'};
    private static final int BYTES = 256;

    @Test
    void decodesTheAccentedLettersOfTheHighHalf() {
        assertEquals("Oh Hôrt, du mîn", new String(DOS_NAME, CodePage437.CHARSET));
    }

    @Test
    void decodesTheGlyphsBelowASpace() {
        assertEquals("♫ ☺ ─", new String(new byte[] {0x0E, ' ', 0x01, ' ', (byte) 0xC4}, CodePage437.CHARSET));
    }

    @Test
    void encodesWhatItDecodes() {
        assertArrayEquals(DOS_NAME, "Oh Hôrt, du mîn".getBytes(CodePage437.CHARSET));
    }

    @Test
    void roundTripsEveryByte() {
        final byte[] all = new byte[BYTES];
        for (int i = 0; i < BYTES; i++) {
            all[i] = (byte) i;
        }

        final String text = new String(all, CodePage437.CHARSET);
        assertEquals(BYTES, text.length());
        assertArrayEquals(all, text.getBytes(CodePage437.CHARSET));
    }

    @Test
    void replacesWhatThePcNeverHad() {
        assertArrayEquals("?".getBytes(), "€".getBytes(CodePage437.CHARSET));
    }
}

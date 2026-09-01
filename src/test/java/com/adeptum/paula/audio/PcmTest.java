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

package com.adeptum.paula.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class PcmTest {

    @Test
    void encodesLittleEndianAndReusesALargeEnoughBuffer() {
        final byte[] target = new byte[8];
        final byte[] bytes = Pcm.toLittleEndian(new short[] {0x0102, (short) 0x8000}, 1, target);

        assertSame(target, bytes);
        assertArrayEquals(new byte[] {0x02, 0x01, 0x00, (byte) 0x80, 0, 0, 0, 0}, bytes);
    }

    @Test
    void growsATooSmallBuffer() {
        assertArrayEquals(new byte[] {0x01, 0x00, 0x02, 0x00}, Pcm.toLittleEndian(new short[] {1, 2}, 1, new byte[0]));
    }
}

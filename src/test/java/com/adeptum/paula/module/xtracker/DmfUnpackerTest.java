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

package com.adeptum.paula.module.xtracker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.adeptum.paula.testing.TestModules;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DmfUnpackerTest {

    private static final int LONGER_THAN_THE_STREAM = 64;
    private static final byte[] ROOT_WITHOUT_BRANCHES = {0, 0, 0, 0, 0};

    @Test
    void sumsTheDeltasTheTreeSpellsOut() {
        assertArrayEquals(TestModules.DMF_PACKED,
                DmfUnpacker.unpack(TestModules.dmfPackedSample(), TestModules.DMF_PACKED.length));
    }

    @Test
    void leavesTheRestSilentWhenTheStreamEndsEarly() {
        final byte[] unpacked = DmfUnpacker.unpack(TestModules.dmfPackedSample(), LONGER_THAN_THE_STREAM);

        assertArrayEquals(TestModules.DMF_PACKED, Arrays.copyOf(unpacked, TestModules.DMF_PACKED.length));
        assertArrayEquals(new byte[LONGER_THAN_THE_STREAM - TestModules.DMF_PACKED.length],
                Arrays.copyOfRange(unpacked, TestModules.DMF_PACKED.length, LONGER_THAN_THE_STREAM));
    }

    @Test
    void unpacksARootWithoutBothBranchesToSilence() {
        assertArrayEquals(new byte[TestModules.DMF_PACKED.length],
                DmfUnpacker.unpack(ROOT_WITHOUT_BRANCHES, TestModules.DMF_PACKED.length));
    }
}

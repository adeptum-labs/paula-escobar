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

package com.adeptum.paula.module.hively;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Checks the generated tables against the numbers the C replayer produces. The spot values and the
 * hashes were taken from a build of hvl_tables.c itself, so a difference here is a difference in the
 * sound.
 */
class HvlTablesTest {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private static long hashOf(final byte[] values) {
        long hash = FNV_OFFSET;

        for (final byte value : values) {
            hash = (hash ^ (value & 0xff)) * FNV_PRIME;
        }
        return hash;
    }

    private static long hashOf(final int[] first, final int[] second) {
        long hash = FNV_OFFSET;

        for (final int value : first) {
            hash = (hash ^ value) * FNV_PRIME;
        }
        for (final int value : second) {
            hash = (hash ^ value) * FNV_PRIME;
        }
        return hash;
    }

    private static byte[] waves(final int offset, final int length) {
        return Arrays.copyOfRange(HvlTables.WAVES, offset, offset + length);
    }

    private static void assertWaves(final int offset, final int... expected) {
        final byte[] actual = waves(offset, expected.length);
        final int[] widened = new int[actual.length];

        for (int i = 0; i < actual.length; i++) {
            widened[i] = actual[i];
        }
        assertArrayEquals(expected, widened, "waves at " + offset);
    }

    @Test
    void laysTheWaveformsOutWhereTheReplayerExpectsThem() {
        assertEquals(1920, HvlTables.WHITE_NOISE_LENGTH);
        assertEquals(0, HvlTables.LOWPASSES);
        assertEquals(202120, HvlTables.TRIANGLE_04);
        assertEquals(202124, HvlTables.TRIANGLE_08);
        assertEquals(202132, HvlTables.TRIANGLE_10);
        assertEquals(202148, HvlTables.TRIANGLE_20);
        assertEquals(202180, HvlTables.TRIANGLE_40);
        assertEquals(202244, HvlTables.TRIANGLE_80);
        assertEquals(202372, HvlTables.SAWTOOTH_04);
        assertEquals(202376, HvlTables.SAWTOOTH_08);
        assertEquals(202384, HvlTables.SAWTOOTH_10);
        assertEquals(202400, HvlTables.SAWTOOTH_20);
        assertEquals(202432, HvlTables.SAWTOOTH_40);
        assertEquals(202496, HvlTables.SAWTOOTH_80);
        assertEquals(202624, HvlTables.SQUARES);
        assertEquals(206720, HvlTables.WHITE_NOISE);
        assertEquals(208640, HvlTables.HIGHPASSES);
        assertEquals(410760, HvlTables.WAVES_SIZE);
        assertEquals(HvlTables.WAVES_SIZE, HvlTables.WAVES.length);
    }

    @Test
    void generatesTheTriangles() {
        assertWaves(HvlTables.TRIANGLE_04, 0, 127, 0, -128);
        assertWaves(HvlTables.TRIANGLE_08, 0, 64, 127, 64, 0, -64, -128, -64);
        assertWaves(HvlTables.TRIANGLE_10, 0, 32, 64, 96, 127, 96, 64, 32, 0, -32, -64, -96, -128, -96, -64, -32);
        assertWaves(HvlTables.TRIANGLE_20, 0, 16, 32, 48, 64, 80, 96, 112, 127, 112, 96, 80, 64, 48, 32, 16,
                0, -16, -32, -48, -64, -80, -96, -112, -128, -112, -96, -80, -64, -48, -32, -16);
    }

    @Test
    void generatesTheSawtooths() {
        assertWaves(HvlTables.SAWTOOTH_04, -128, -43, 42, 127);
        assertWaves(HvlTables.SAWTOOTH_80, -128, -126, -124, -122);
        assertWaves(HvlTables.SAWTOOTH_80 + 0x7c, 120, 122, 124, 126);
    }

    @Test
    void generatesTheSquares() {
        assertWaves(HvlTables.SQUARES, -128, -128, -128, -128);
        assertWaves(HvlTables.SQUARES + 0x7c, -128, -128, 127, 127, -128, -128, -128, -128);
        assertWaves(HvlTables.WHITE_NOISE - 8, 127, 127, 127, 127, 127, 127, 127, 127);
    }

    @Test
    void generatesTheWhiteNoise() {
        assertWaves(HvlTables.WHITE_NOISE, 127, 127, -88, -30, 120, 62, 44, -110);
        assertWaves(HvlTables.WHITE_NOISE + HvlTables.WHITE_NOISE_LENGTH - 8, 127, 127, 127, -128, 19, -25, 44, 44);
    }

    @Test
    void generatesTheFilteredWaveforms() {
        assertWaves(HvlTables.LOWPASSES, 4, 4, 5, 5, 3, 2, 3, 4);
        assertWaves(20000, -8, -6, -4, -2, 0, 2, 4, 6);
        assertWaves(150000, 65, 49, 33, 17, 1, -15, -31, -47);
        assertWaves(HvlTables.TRIANGLE_04 - 8, 107, 111, 127, -21, 0, -37, 76, 18);
        assertWaves(HvlTables.HIGHPASSES, -1, 127, -13, -128, 12, 76, 127, 57);
        assertWaves(HvlTables.HIGHPASSES + 20000, -1, -1, -1, -1, -1, -1, -1, -1);
        assertWaves(HvlTables.HIGHPASSES + 150000, 0, 0, -1, -1, 0, 0, 0, 0);
        assertWaves(HvlTables.WAVES_SIZE - 8, 127, -108, 12, -128, 127, -44, 112, -128);
    }

    @Test
    void generatesEveryWaveformByteTheCDoes() {
        assertEquals(9212275871276582703L, hashOf(HvlTables.WAVES));
    }

    @Test
    void generatesThePanningTables() {
        assertEquals(256, HvlTables.PANNING_LEFT.length);
        assertEquals(256, HvlTables.PANNING_RIGHT.length);
        assertEquals(254, HvlTables.PANNING_LEFT[0]);
        assertEquals(235, HvlTables.PANNING_LEFT[64]);
        assertEquals(180, HvlTables.PANNING_LEFT[128]);
        assertEquals(3, HvlTables.PANNING_LEFT[254]);
        assertEquals(0, HvlTables.PANNING_LEFT[255]);
        assertEquals(0, HvlTables.PANNING_RIGHT[0]);
        assertEquals(97, HvlTables.PANNING_RIGHT[64]);
        assertEquals(180, HvlTables.PANNING_RIGHT[128]);
        assertEquals(254, HvlTables.PANNING_RIGHT[255]);
        assertEquals(-6796489264270974448L, hashOf(HvlTables.PANNING_LEFT, HvlTables.PANNING_RIGHT));
    }

    @Test
    void holdsTheConstantTables() {
        assertEquals(45, HvlTables.LENGTH.length);
        assertEquals(3, HvlTables.LENGTH[0]);
        assertEquals(0x7f, HvlTables.LENGTH[43]);
        assertEquals(0x280 * 3 - 1, HvlTables.LENGTH[44]);
        assertEquals(64, HvlTables.VIBRATO.length);
        assertEquals(0, HvlTables.VIBRATO[0]);
        assertEquals(255, HvlTables.VIBRATO[16]);
        assertEquals(-255, HvlTables.VIBRATO[48]);
        assertEquals(61, HvlTables.PERIOD.length);
        assertEquals(0, HvlTables.PERIOD[0]);
        assertEquals(3424, HvlTables.PERIOD[1]);
        assertEquals(0x71, HvlTables.PERIOD[60]);
        assertArrayEquals(new int[] {128, 96, 64, 32, 0}, HvlTables.STEREO_PAN_LEFT);
        assertArrayEquals(new int[] {128, 160, 193, 225, 255}, HvlTables.STEREO_PAN_RIGHT);
    }

    @Test
    void turnsAPeriodIntoTheFrequencyTheCComputes() {
        assertEquals(4724328311612768256L, Double.doubleToRawLongBits(HvlTables.periodToFrequency(3424)));
        assertEquals(4746414799754100736L, Double.doubleToRawLongBits(HvlTables.periodToFrequency(0x71)));
        assertEquals(67888232.0, HvlTables.periodToFrequency(HvlTables.PERIOD[1]));
    }
}

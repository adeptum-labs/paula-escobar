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

package com.adeptum.paula.module.mo3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * A module that has been cut short, mangled or made up is refused with something to say for itself. What must
 * never happen is a reader that walks off the end of an array or loops for ever on a length it read out of the
 * file, so every refusal is checked to be an {@link IOException} rather than whatever the arrays would throw.
 */
class Mo3HardeningTest {

    private static final String FIXTURE = "/mo3/paula-test.mo3";
    private static final int MANGLINGS = 400;
    private static final int SEED = 20260908;
    private static final int RUBBISH_LENGTH = 4096;

    @Test
    void readsTheModuleItIsGiven() throws IOException {
        assertEquals(TestModules.MO3_CHANNELS, Mo3Reader.read(TestModules.mo3()).song().channels());
    }

    @Test
    void refusesTheModuleCutShortAnywhere() {
        final byte[] file = TestModules.mo3();
        for (int length = 0; length < file.length; length++) {
            readOrRefuse(Arrays.copyOf(file, length), "cut to " + length + " bytes");
        }
    }

    @Test
    void refusesThePackedModuleCutShortAnywhere() throws IOException {
        final byte[] file = fixture();
        for (int length = 0; length < file.length; length++) {
            readOrRefuse(Arrays.copyOf(file, length), "cut to " + length + " bytes");
        }
    }

    @Test
    void survivesASingleByteChangedAnywhere() {
        final Random mangler = new Random(SEED);
        final byte[] file = TestModules.mo3();
        for (int mangling = 0; mangling < MANGLINGS; mangling++) {
            final byte[] mangled = file.clone();
            final int at = mangler.nextInt(mangled.length);
            mangled[at] = (byte) mangler.nextInt(0x100);
            readOrRefuse(mangled, "byte " + at + " set to " + (mangled[at] & 0xFF));
        }
    }

    @Test
    void survivesASingleByteChangedInAPackedModule() throws IOException {
        final Random mangler = new Random(SEED);
        final byte[] file = fixture();
        for (int mangling = 0; mangling < MANGLINGS; mangling++) {
            final byte[] mangled = file.clone();
            final int at = mangler.nextInt(mangled.length);
            mangled[at] = (byte) mangler.nextInt(0x100);
            readOrRefuse(mangled, "byte " + at + " set to " + (mangled[at] & 0xFF));
        }
    }

    @Test
    void refusesRubbishThatSaysItIsAnMo3() {
        final Random mangler = new Random(SEED);
        final byte[] rubbish = new byte[RUBBISH_LENGTH];
        mangler.nextBytes(rubbish);
        rubbish[0] = 'M';
        rubbish[1] = 'O';
        rubbish[2] = '3';

        for (int version = 0; version <= 5; version++) {
            rubbish[3] = (byte) version;
            readOrRefuse(rubbish.clone(), "rubbish at version " + version);
        }
    }

    @Test
    void refusesAChannelCountNoTrackerWrote() {
        readOrRefusedOnly(withMusicByte(TestModules.MO3_CHANNELS_AT, 0), "no channels");
        readOrRefusedOnly(withMusicByte(TestModules.MO3_CHANNELS_AT, 0xFF), "more channels than the format holds");
    }

    @Test
    void refusesARestartPastTheEndOfTheOrders() {
        readOrRefusedOnly(withMusicByte(TestModules.MO3_RESTART_AT, 0xFF), "a restart past the orders");
    }

    private static byte[] withMusicByte(int at, int value) {
        final byte[] music = TestModules.mo3Music();
        music[at] = (byte) value;
        return TestModules.mo3(music);
    }

    /**
     * The module must be refused; reading it is not an option, unlike the manglings that may happen to land on
     * something harmless.
     */
    private static void readOrRefusedOnly(byte[] file, String what) {
        try {
            Mo3Reader.read(file);
            fail("read a module with " + what);
        } catch (IOException refused) {
            assertNotNull(refused.getMessage(), what + " was refused without saying why");
        }
    }

    private static void readOrRefuse(byte[] file, String what) {
        try {
            Mo3Reader.read(file);
        } catch (IOException refused) {
            assertNotNull(refused.getMessage(), what + " was refused without saying why");
        } catch (RuntimeException thrown) {
            fail(what + " threw " + thrown);
        }
    }

    private static byte[] fixture() throws IOException {
        try (InputStream fixture = Mo3HardeningTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(fixture, FIXTURE);
            return fixture.readAllBytes();
        }
    }
}

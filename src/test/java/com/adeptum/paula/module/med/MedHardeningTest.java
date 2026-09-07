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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * A module off the party archives is as likely to be truncated, mangled or written by a tracker nobody has
 * heard of as it is to be sound. Whatever it holds, the reader has to say so rather than fall over.
 */
class MedHardeningTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 512;
    private static final int SEED = 20260907;
    private static final int MANGLED_COPIES = 400;
    private static final int TICKS = 200;

    private static void readOrRefuse(byte[] file) {
        try {
            final MedFile module = MedReader.read(file);
            final MedEngine engine = new MedEngine(module, SAMPLE_RATE);
            final short[] out = new short[FRAMES * 2];
            for (int tick = 0; tick < TICKS && !engine.hasEnded(); tick++) {
                engine.mix(out, FRAMES);
            }
        } catch (IOException e) {
            assertTrue(e.getMessage() != null && !e.getMessage().isBlank(), "a refusal says what was wrong");
        } catch (RuntimeException e) {
            fail("a mangled module should be refused, not " + e);
        }
    }

    @Test
    void refusesEveryTruncationOfAModule() {
        final byte[] whole = TestModules.medMmd0();

        for (int length = 0; length < whole.length; length++) {
            readOrRefuse(Arrays.copyOf(whole, length));
        }
    }

    /**
     * One byte changed anywhere can turn an offset into a place the file does not reach or a count into more
     * than the format allows, which is the shape most damaged modules arrive in.
     */
    @Test
    void refusesAModuleWithABytePutWrong() {
        final byte[] whole = TestModules.medMmd0();
        final Random random = new Random(SEED);

        for (int copy = 0; copy < MANGLED_COPIES; copy++) {
            final byte[] mangled = whole.clone();
            mangled[random.nextInt(mangled.length)] = (byte) random.nextInt(0x100);
            readOrRefuse(mangled);
        }
    }

    @Test
    void refusesRubbishThatIsNotAModuleAtAll() {
        final byte[] rubbish = new byte[4096];
        new Random(SEED).nextBytes(rubbish);

        assertDoesNotThrow(() -> readOrRefuse(rubbish));
        assertDoesNotThrow(() -> readOrRefuse(new byte[0]));
    }

    @Test
    void playsAModuleWhosePlaySequenceNamesBlocksThatAreNotThere() {
        final byte[] file = TestModules.medMmd0();
        final int playSeqAt = TestModules.MED_PLAY_SEQUENCE_AT;
        file[playSeqAt] = (byte) 0xFE;

        assertDoesNotThrow(() -> readOrRefuse(file));
    }
}

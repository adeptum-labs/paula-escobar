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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlexReaderTest {

    @Test
    void readsTheSixSettingsTheBlockCarries() {
        final FlexEffects effects = FlexReader.of(TestModules.flexTrax(TestModules.FLEX_REVERB_DECAY,
                TestModules.FLEX_REVERB_LEVEL, TestModules.FLEX_DELAY_DECAY, TestModules.FLEX_DELAY_TIME,
                TestModules.FLEX_DELAY_PING_PONG, TestModules.FLEX_DELAY_LEVEL)).orElseThrow();

        assertEquals(TestModules.FLEX_REVERB_DECAY, effects.reverbDecay());
        assertEquals(TestModules.FLEX_REVERB_LEVEL, effects.reverbLevel());
        assertEquals(TestModules.FLEX_DELAY_DECAY, effects.delayDecay());
        assertEquals(TestModules.FLEX_DELAY_TIME, effects.delayTime());
        assertEquals(TestModules.FLEX_DELAY_PING_PONG, effects.delayPingPong());
        assertEquals(TestModules.FLEX_DELAY_LEVEL, effects.delayLevel());
    }

    /**
     * A module saved with the effects off ends with its samples, so there is nothing to sound rather than
     * something to sound at zero.
     */
    @Test
    void findsNothingInAModuleSavedWithoutTheBlock() {
        assertEquals(Optional.empty(), FlexReader.of(TestModules.flexTraxWithoutEffects()));
    }

    /**
     * Two of the modules in the wild carry a value in the reverb decay that no slider of the tracker's could
     * have produced; only the byte the setting is written in is its own.
     */
    @Test
    void readsPastAValueTheTrackerCouldNotHaveWritten() {
        final FlexEffects effects = FlexReader.of(TestModules.flexTraxWithAStrayReverbDecay()).orElseThrow();

        assertEquals(0, effects.reverbDecay());
        assertEquals(TestModules.FLEX_REVERB_LEVEL, effects.reverbLevel(), "the rest of the block still reads");
    }

    /**
     * A sample the module never uses is a word long and stores nothing, so counting its two bytes would put
     * the block past where it is. Twelve of the modules in the wild are only findable once that is allowed
     * for, one of them carrying twenty-six such samples.
     */
    @Test
    void countsNoBytesForTheSamplesAModuleLeavesEmpty() {
        final FlexEffects effects = FlexReader.of(TestModules.flexTraxWithUnusedSamples(0, 0, 0,
                TestModules.FLEX_DELAY_TIME, 0, 0)).orElseThrow();

        assertEquals(TestModules.FLEX_DELAY_TIME, effects.delayTime());
    }

    @Test
    void findsNothingInAModuleThatIsNotOneOfFlexTrax(@TempDir Path dir) throws Exception {
        assertEquals(Optional.empty(), FlexReader.of(TestModules.proTracker()));
        assertEquals(Optional.empty(), FlexReader.of(new byte[10]), "nor in something far too short");

        final Path file = Files.write(dir.resolve("tune.flx"), TestModules.flexTrax(TestModules.FLEX_REVERB_DECAY,
                0, 0, 0, 0, 0));
        assertTrue(FlexReader.of(file).isPresent(), "and it reads the same module from a file");
    }
}

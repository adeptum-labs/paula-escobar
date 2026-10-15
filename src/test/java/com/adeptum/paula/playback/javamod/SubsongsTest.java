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

package com.adeptum.paula.playback.javamod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.adeptum.paula.module.javamod.JavaModLoader;
import com.adeptum.paula.module.javamod.JavaModModule;
import com.adeptum.paula.testing.TestModules;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The songs an Impulse Tracker, Scream Tracker or MO3 file keeps in one order list, told apart by the "---"
 * markers between them.
 */
class SubsongsTest {

    @Test
    void splitsAScreamTrackerModuleAtItsOrderSeparators(@TempDir Path dir) throws Exception {
        final Subsongs subsongs = module(TestModules.writeScreamTrackerWithACue(dir)).subsongs();

        assertEquals(List.of(new Subsongs.OrderRange(2, 3), new Subsongs.OrderRange(4, 5),
                new Subsongs.OrderRange(TestModules.S3M_CUE_ORDER, TestModules.S3M_CUE_ORDER + 1)), subsongs.ranges());
        assertEquals(3, subsongs.orders());
    }

    /**
     * A file of one song has no separator to split at, however much of the order list the loader trims away.
     */
    @Test
    void keepsAProTrackerModuleWhole(@TempDir Path dir) throws Exception {
        final JavaModModule module = module(TestModules.writeProTracker(dir));

        assertEquals(List.of(new Subsongs.OrderRange(0, module.tracker().getSongLength())), module.subsongs().ranges());
    }

    /**
     * The chosen song becomes the whole order list: the orders before it play as "+++", which the mixer steps
     * over, and the song ends at its own last order.
     */
    @Test
    void playsOnlyTheChosenSubsong(@TempDir Path dir) throws Exception {
        final JavaModModule module = module(TestModules.writeScreamTrackerWithSubsongs(dir));

        module.subsongs().select(1);

        final Module tracker = module.tracker();
        assertEquals(5, tracker.getSongLength());
        final int[] arrangement = tracker.getArrangement();
        assertEquals(5, arrangement.length);
        for (int order = 0; order < 4; order++) {
            assertEquals(ModConstants.IGNORE_PAT_INDEX, arrangement[order], "order " + order);
        }
        assertEquals(2, arrangement[4]);
    }

    /**
     * JavaMod measures a module once and keeps the length; every subsong is a song of its own length.
     */
    @Test
    void leavesTheChosenSubsongToBeMeasured(@TempDir Path dir) throws Exception {
        final JavaModModule module = module(TestModules.writeScreamTrackerWithSubsongs(dir));
        module.tracker().setLengthInMilliseconds(1);

        module.subsongs().select(1);

        assertEquals(-1, module.tracker().getLengthInMilliseconds());
    }

    /**
     * A cue jumps to the silent order the game waits in; inside its own subsong that jump goes back to the
     * cue's start instead, where JavaMod hears a loop and fades out.
     */
    @Test
    void turnsAJumpOutOfTheSubsongBackToItsStart(@TempDir Path dir) throws Exception {
        final JavaModModule module = module(TestModules.writeScreamTrackerWithACue(dir));

        module.subsongs().select(2);

        assertEquals(TestModules.S3M_CUE_ORDER, cueJump(module.tracker()));
        module.subsongs().select(0);
        assertEquals(2, cueJump(module.tracker()));
    }

    private static int cueJump(Module tracker) {
        return tracker.getPatternContainer().getPattern(TestModules.S3M_CUE_PATTERN)
                .getPatternRow(TestModules.S3M_CUE_JUMP_ROW).getPatternElement(0).getEffektOp();
    }

    private static JavaModModule module(Path file) throws IOException {
        return (JavaModModule) new JavaModLoader().load(file);
    }
}

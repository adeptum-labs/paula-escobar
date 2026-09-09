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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.javamod.JavaModLoader;
import com.adeptum.paula.testing.TestModules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlexModuleTest {

    private static final int RATE = 48000;
    private static final int FRAMES = 4096;
    private static final int LOUD = FlexEffects.LOUDEST;

    private final JavaModLoader loader = new JavaModLoader();

    @Test
    void playsAFlexTraxModuleWithItsEffectsOverIt(@TempDir Path dir) throws Exception {
        final Module module = loader.load(write(dir, "loud.flx",
                TestModules.flexTrax(LOUD, LOUD, LOUD / 2, 0, 0, LOUD)));

        assertInstanceOf(FlexModule.class, module, "the module carries its effects");
        assertFalse(Arrays.equals(rendered(module), rendered(plain(dir))), "and sounds unlike the bare module");
    }

    /**
     * A module whose two levels are both none sounds exactly as the tracker module it is, so there is nothing
     * to lay over it and nothing to spend the time on.
     */
    @Test
    void playsAModuleWhoseEffectsSoundForNothingAsItIs(@TempDir Path dir) throws Exception {
        final Module module = loader.load(write(dir, "quiet.flx", TestModules.flexTrax(LOUD, 0, LOUD, LOUD, LOUD, 0)));

        assertFalse(module instanceof FlexModule, "nothing is laid over it");
        assertArrayEqualsAsSound(rendered(plain(dir)), rendered(module));
    }

    @Test
    void playsAModuleWithoutABlockAsItIs(@TempDir Path dir) throws Exception {
        assertFalse(loader.load(write(dir, "none.flx", TestModules.flexTraxWithoutEffects())) instanceof FlexModule);
    }

    /**
     * A seek leaves a tail behind that belongs to a part of the song no longer playing, so the effects start
     * afresh and the first frames after it are the mix alone.
     */
    @Test
    void startsTheEffectsAfreshOverASeek(@TempDir Path dir) throws Exception {
        final Module module = loader.load(write(dir, "seek.flx",
                TestModules.flexTrax(LOUD, LOUD, LOUD, LOUD, LOUD, LOUD)));
        final var renderer = module.createRenderer(RATE);

        final short[] buffer = new short[FRAMES * 2];
        renderer.render(buffer);
        renderer.seek(java.time.Duration.ZERO);
        final short[] afresh = new short[FRAMES * 2];
        renderer.render(afresh);

        final var again = module.createRenderer(RATE);
        final short[] fromTheStart = new short[FRAMES * 2];
        again.render(fromTheStart);
        assertEquals(Arrays.toString(fromTheStart), Arrays.toString(afresh),
                "a seek to the start sounds as the start does");
    }

    private static Path write(Path dir, String name, byte[] module) throws Exception {
        return Files.write(dir.resolve(name), module);
    }

    private Module plain(Path dir) throws Exception {
        return loader.load(write(dir, "plain.flx", TestModules.flexTraxWithoutEffects()));
    }

    private static short[] rendered(Module module) {
        final short[] buffer = new short[FRAMES * 2];
        module.createRenderer(RATE).render(buffer);
        return buffer;
    }

    private static void assertArrayEqualsAsSound(short[] expected, short[] actual) {
        assertTrue(Arrays.equals(expected, actual), "the same sound came out");
    }
}

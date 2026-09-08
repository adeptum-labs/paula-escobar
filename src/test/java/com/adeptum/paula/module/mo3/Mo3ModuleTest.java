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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElementIT;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElementXM;
import de.quippy.javamod.multimedia.mod.mixer.ProTrackerMixer;
import de.quippy.javamod.multimedia.mod.mixer.ScreamTrackerMixer;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * The module handed to the player, built from the fixture the tests generate and from the packed one beside
 * it. What the module says about itself is what the tracker that wrote it would have said.
 */
class Mo3ModuleTest {

    private static final String FIXTURE = "/mo3/paula-test.mo3";
    private static final String NAME = "test.mo3";
    private static final int SAMPLE_RATE = 44100;
    private static final int MIDDLE_C = 0x31;
    private static final int ROWS = 64;

    @Test
    void saysWhatTheTrackerThatWroteItWouldSay() throws IOException {
        final Mo3Module module = generated();

        assertEquals("MO3", module.getModID());
        assertEquals("ProTracker", module.getTrackerName());
        assertEquals(TestModules.TITLE, module.getSongName());
        assertEquals(TestModules.MO3_CHANNELS, module.getNChannels());
        assertEquals(1, module.getNPattern());
        assertEquals(TestModules.MO3_SAMPLES, module.getNSamples());
        assertEquals(TestModules.MO3_SPEED, module.getTempo(), "the speed the song starts at");
        assertEquals(TestModules.MO3_TEMPO, module.getBPMSpeed());
    }

    @Test
    void playsTheOrdersTheSongNames() throws IOException {
        final Mo3Module module = generated();

        assertEquals(TestModules.MO3_ORDERS, module.getSongLength());
        assertEquals(0, module.getArrangement()[0]);
    }

    @Test
    void fillsThePatternFromTheTracksItIsMadeOf() throws IOException {
        final PatternElement element = generated().getPatternContainer().getPatternElement(0, 0, 0);

        assertEquals(MIDDLE_C, element.getNoteIndex());
        assertEquals(ModConstants.noteValues[MIDDLE_C - 1], element.getPeriod());
        assertEquals(1, element.getInstrument());
        assertEquals(ROWS, generated().getPatternContainer().getPattern(0).getRowCount());
    }

    @Test
    void writesTheRowsTheTrackSaysNothingAbout() throws IOException {
        final PatternElement element = generated().getPatternContainer().getPatternElement(0, 1, 0);

        assertNotNull(element);
        assertEquals(0, element.getNoteIndex());
        assertEquals(0, element.getInstrument());
    }

    @Test
    void handsAProTrackerModuleToTheProTrackerMixer() throws IOException {
        final Mo3Module module = generated();

        assertInstanceOf(ProTrackerMixer.class, mixerOf(module));
        assertInstanceOf(PatternElementXM.class, module.getPatternContainer().getPatternElement(0, 0, 0));
    }

    @Test
    void handsAnImpulseTrackerModuleToTheScreamTrackerMixer() throws IOException {
        final Mo3Module module = ofKind(TestModules.MO3_IS_IMPULSE_TRACKER);

        assertEquals("Impulse Tracker", module.getTrackerName());
        assertInstanceOf(ScreamTrackerMixer.class, mixerOf(module));
        assertInstanceOf(PatternElementIT.class, module.getPatternContainer().getPatternElement(0, 0, 0));
    }

    @Test
    void tunesAFourChannelProTrackerModuleTheWayTheAmigaDid() throws IOException {
        final Mo3Module module = generated();

        assertTrue(module.isAmigaLike());
        assertEquals(ModConstants.AMIGA_TABLE, module.getFrequencyTable());
        assertTrue((module.getSongFlags() & ModConstants.SONG_AMIGALIMITS) != 0);
    }

    @Test
    void tunesAFastTrackerModuleTheWayFastTrackerDid() throws IOException {
        final Mo3Module module = ofKind(TestModules.MO3_IS_FAST_TRACKER);

        assertEquals("Fast Tracker 2", module.getTrackerName());
        assertFalse(module.isAmigaLike());
        assertEquals(ModConstants.XM_AMIGA_TABLE, module.getFrequencyTable());
    }

    @Test
    void buildsTheModuleThatWasPackedByTheCompressor() throws IOException {
        final Mo3Module module = Mo3Module.of(NAME, fixture());

        assertEquals("ProTracker", module.getTrackerName());
        assertEquals(TestModules.TITLE, module.getSongName());
        assertEquals(TestModules.MO3_CHANNELS, module.getNChannels());
        assertEquals(MIDDLE_C, module.getPatternContainer().getPatternElement(0, 0, 0).getNoteIndex());
    }

    @Test
    void spreadsTheChannelsTheWayTheModuleAsks() throws IOException {
        final Mo3Module module = Mo3Module.of(NAME, fixture());

        assertEquals(TestModules.MO3_LEFT, module.getPanningValue(0));
        assertEquals(TestModules.MO3_RIGHT, module.getPanningValue(1));
    }

    private static Object mixerOf(Mo3Module module) {
        return module.getModMixer(SAMPLE_RATE, ModConstants.INTERPOLATION_LINEAR,
                ModConstants.AMIGAEMULATION_NONE, ModConstants.PLAYER_LOOP_FADEOUT, 0);
    }

    private static Mo3Module generated() throws IOException {
        return Mo3Module.of(NAME, TestModules.mo3());
    }

    private static Mo3Module ofKind(int flag) throws IOException {
        final byte[] music = TestModules.mo3Music();
        final int flags = flag | TestModules.MO3_ALWAYS_SET | TestModules.MO3_INSTRUMENT_MODE_FLAG;
        for (int at = 0; at < Integer.BYTES; at++) {
            music[TestModules.MO3_FLAGS_AT + at] = (byte) (flags >> (at * Byte.SIZE));
        }
        return Mo3Module.of(NAME, TestModules.mo3(music));
    }

    private static byte[] fixture() throws IOException {
        try (InputStream fixture = Mo3ModuleTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(fixture, FIXTURE);
            return fixture.readAllBytes();
        }
    }
}

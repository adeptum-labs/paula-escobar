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

package com.adeptum.paula.module.ult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestUlts;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElementIT;
import de.quippy.javamod.multimedia.mod.mixer.ScreamTrackerMixer;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class UltModuleTest {

    private static final String NAME = "test.ult";
    private static final int SAMPLE_RATE = 44100;
    private static final int VOLUME_SLIDE = 'D' - 'A' + 1;

    @Test
    void saysWhatUltraTrackerWouldSay() throws IOException {
        final UltModule module = UltModule.of(NAME, TestUlts.ult());

        assertEquals("UltraTracker 1.6", module.getTrackerName());
        assertEquals(TestUlts.TITLE, module.getSongName());
        assertEquals(TestUlts.MESSAGE, module.getSongMessage());
        assertEquals(TestUlts.CHANNELS, module.getNChannels());
        assertEquals(1, module.getSongLength());
        assertEquals(6, module.getTempo());
        assertEquals(125, module.getBPMSpeed());
    }

    /**
     * UltraTracker counts its notes from two octaves above where Impulse Tracker does.
     */
    @Test
    void writesTheEventAsAnImpulseTrackerOne() throws IOException {
        final PatternElement element = UltModule.of(NAME, TestUlts.ult()).getPatternContainer()
                .getPatternElement(0, 0, 0);

        assertInstanceOf(PatternElementIT.class, element);
        assertEquals(TestUlts.NOTE + 24, element.getNoteIndex());
        assertEquals(ModConstants.noteValues[TestUlts.NOTE + 23], element.getPeriod());
        assertEquals(TestUlts.INSTRUMENT, element.getInstrument());
        assertEquals(VOLUME_SLIDE, element.getEffekt());
        assertEquals(TestUlts.SECOND_PARAM, element.getEffektOp());
        assertEquals(UltCell.COLUMN_VOLUME, element.getVolumeEffekt());
        assertEquals(TestUlts.FIRST_PARAM / 4, element.getVolumeEffektOp());
    }

    /**
     * The loop is a sustain loop, so that a stopped loop lets the sample play out, and the finetune of minus
     * a thirty-second of a semitone takes the doubled speed down with it.
     */
    @Test
    void tunesTheSampleAndLoopsItUntilReleased() throws IOException {
        final Sample sample = UltModule.of(NAME, TestUlts.ult()).getInstrumentContainer().getSample(0);

        assertEquals(TestUlts.SAMPLE_LENGTH, sample.sampleLength);
        assertTrue((sample.loopType & ModConstants.LOOP_SUSTAIN_ON) != 0);
        assertEquals(0, sample.loopType & ModConstants.LOOP_ON);
        assertEquals(TestUlts.LOOP_END, sample.sustainLoopStop);
        assertEquals(TestUlts.VOLUME / 4, sample.volume);
        assertEquals(Math.round(TestUlts.SPEED * 2 * Math.pow(2, TestUlts.FINETUNE / (12.0 * 32768))),
                sample.baseFrequency);
    }

    @Test
    void spreadsTheChannelsWhereTheModuleSays() throws IOException {
        final UltModule module = UltModule.of(NAME, TestUlts.ult());

        assertEquals(TestUlts.FIRST_CHANNEL_PANNING << 4 | 8, module.getPanningValue(0));
        assertEquals(TestUlts.SECOND_CHANNEL_PANNING << 4 | 8, module.getPanningValue(1));
    }

    @Test
    void handsTheModuleToTheScreamTrackerMixer() throws IOException {
        final UltModule module = UltModule.of(NAME, TestUlts.ult());

        assertInstanceOf(ScreamTrackerMixer.class, module.getModMixer(SAMPLE_RATE, ModConstants.INTERPOLATION_LINEAR,
                ModConstants.AMIGAEMULATION_NONE, ModConstants.PLAYER_LOOP_FADEOUT, 0));
    }

    @Test
    void refusesAModuleThatPlaysNoPattern() {
        final byte[] bytes = TestUlts.ult();
        bytes[TestUlts.ORDERS_AT] = (byte) 0xff;

        assertThrows(IOException.class, () -> UltModule.of(NAME, bytes));
    }
}

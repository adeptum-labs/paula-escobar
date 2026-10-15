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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.javamod.JavaModLoader;
import com.adeptum.paula.module.ult.UltLoader;
import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import com.adeptum.paula.testing.TestModules;
import com.adeptum.paula.testing.TestUlts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaModRendererTest {

    private static final int SAMPLE_RATE = 8000;
    private static final int FRAMES = 1024;
    private static final int MAX_BUFFERS = SAMPLE_RATE * 120 / FRAMES;
    private static final int PLAYING_CHANNEL = 1;
    private static final int SET_VOLUME = 0x0c;
    private static final int SPECIAL = 0x0e;
    private static final int CUT_AT_TICK_THREE = 0xc3;
    private static final int RETRIG_WITHOUT_SPEED = 0x90;
    private static final int FIRST_SUBSONG_MILLIS = TestModules.S3M_FIRST_SUBSONG_ROWS * TestModules.S3M_ROW_MILLIS;
    private static final int SECOND_SUBSONG_MILLIS = TestModules.S3M_SECOND_SUBSONG_ROWS * TestModules.S3M_ROW_MILLIS;
    private static final int A_ROW_OR_TWO_MILLIS = 250;
    private static final Duration LONGER_THAN_ANY_FADE = Duration.ofMinutes(1);

    @Test
    void rendersAudioAndEventuallyFinishes(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeProTracker(dir)).createRenderer(SAMPLE_RATE);
        final short[] buffer = new short[FRAMES * 2];

        assertEquals(FRAMES, renderer.render(buffer));
        assertTrue(peak(buffer) > 1000, "first buffer should contain the square wave");
        assertEquals(Duration.ofMillis(FRAMES * 1000L / SAMPLE_RATE), renderer.position());

        int buffers = 1;
        while (renderer.render(buffer) > 0 && buffers < MAX_BUFFERS) {
            buffers++;
        }
        assertTrue(buffers < MAX_BUFFERS, "song should end once it loops and fades out");
    }

    /**
     * The interpolation looks ahead of where the old sample was, and the sample swapped in for it may be far
     * shorter than that.
     */
    @Test
    void keepsRenderingWhenAShorterSampleIsSwappedInAtTheLoopEnd(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeProTrackerSwappingSamples(dir)).createRenderer(SAMPLE_RATE);
        final short[] buffer = new short[FRAMES * 2];

        for (int i = 0; i < SAMPLE_RATE / FRAMES; i++) {
            assertEquals(FRAMES, renderer.render(buffer), "buffer " + i);
        }
    }

    /**
     * A sample named with a delayed note is swapped in at the loop end of the one playing before the note sounds,
     * and the ramp down of the note fades out the swapped-in sample at its own position.
     */
    @Test
    void keepsRenderingADelayedNoteAfterASampleSwap(@TempDir Path dir) throws Exception {
        assertRendersASecond(new JavaModLoader().load(TestModules.writeProTrackerDelayedNoteAfterSwap(dir)));
    }

    /**
     * A channel that was never given a sample has no frequency to tune a note to.
     */
    @Test
    void keepsRenderingANoteOnAChannelThatNeverHadASample(@TempDir Path dir) throws Exception {
        final int[][][] channels = {{{TestUlts.NOTE, 0, 0, 0, 0}}};

        assertRendersASecond(new UltLoader().load(Files.write(dir.resolve("unset.ult"), TestUlts.ult(channels, 32))));
    }

    /**
     * A silent short sample needs no ramp down when a long one replaces it, and a note cut later in that row
     * ramps down the long one from where it has got to, far past the end of the short one.
     */
    @Test
    void keepsRenderingANoteCutAfterASampleThatNeededNoRampDown(@TempDir Path dir) throws Exception {
        final int[][][] channels = {{
            {TestUlts.NOTE, 1, SET_VOLUME, 0, 0},
            {TestUlts.NOTE, 2, SPECIAL, CUT_AT_TICK_THREE, 0}
        }};

        assertRendersASecond(new UltLoader().load(Files.write(dir.resolve("cut.ult"), TestUlts.ult(channels, 32, 20000))));
    }

    /**
     * A module of more than four channels is tuned like a FastTracker one, whose finetune runs from -128 to 127
     * instead of ProTracker's -8 to 7, so its arpeggio and glissando must not look the finetune up as ProTracker's.
     */
    @Test
    void keepsRenderingAnEightChannelModWithAFinetunedArpeggioAndGlissando(@TempDir Path dir) throws Exception {
        assertRendersASecond(new JavaModLoader().load(TestModules.writeEightChannelFinetunedArpeggio(dir)));
    }

    /**
     * A tracker writes an instrument with no sample of its own and a MIDI channel for an outboard synth to
     * sound; JavaMod hands those notes to a MIDI mixer, and the player has to give it one to hand them to.
     */
    @Test
    void keepsRenderingAnInstrumentThatAsksForMidi(@TempDir Path dir) throws Exception {
        assertRendersASecond(new JavaModLoader().load(TestModules.writeXmWithMidiInstrument(dir)));
    }

    /**
     * A retrigger whose parameter names no speed repeats the note on every tick instead of dividing by it.
     */
    @Test
    void keepsRenderingARetriggerGivenNoSpeed(@TempDir Path dir) throws Exception {
        final int[][][] channels = {{{TestUlts.NOTE, 1, SPECIAL, RETRIG_WITHOUT_SPEED, 0}}};

        assertRendersASecond(new UltLoader().load(Files.write(dir.resolve("retrig.ult"), TestUlts.ult(channels, 32))));
    }

    private static void assertRendersASecond(Module module) {
        final Renderer renderer = module.createRenderer(SAMPLE_RATE);
        final short[] buffer = new short[FRAMES * 2];

        for (int i = 0; i < SAMPLE_RATE / FRAMES; i++) {
            assertEquals(FRAMES, renderer.render(buffer), "buffer " + i);
        }
    }

    @Test
    void seekSkipsAheadAndKeepsRendering(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeProTracker(dir)).createRenderer(SAMPLE_RATE);
        final short[] buffer = new short[FRAMES * 2];

        renderer.seek(Duration.ofSeconds(1));

        final Duration position = renderer.position();
        assertTrue(position.compareTo(Duration.ofSeconds(1)) >= 0, "seek should reach the requested second, was " + position);
        assertTrue(position.compareTo(Duration.ofMillis(1100)) < 0, "seek should stop at the first tick past it, was " + position);
        assertEquals(FRAMES, renderer.render(buffer));
    }

    @Test
    void seekingBeforeTheStartClampsToZero(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeProTracker(dir)).createRenderer(SAMPLE_RATE);

        renderer.seek(Duration.ofSeconds(-5));

        assertEquals(Duration.ZERO, renderer.position());
    }

    @Test
    void reportsEachChannelWithItsWaveformAndInstrument(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeProTracker(dir)).createRenderer(SAMPLE_RATE);
        renderer.render(new short[FRAMES * 2]);

        final List<ChannelState> channels = renderer.channels();
        assertEquals(4, channels.size());
        final ChannelState first = channels.get(0);
        assertEquals(1, first.number());
        assertEquals(1, first.instrument(), "the test module plays sample one on channel one");
        assertTrue(first.volume() > 0, "channel one is sounding");
        assertTrue(Arrays.stream(first.waveform()).anyMatch(v -> Math.abs(v) > 0.1), "a square wave is not flat");
        assertEquals(0, channels.get(1).volume(), "silent channels report no volume");
        assertEquals(0, channels.get(1).instrument());
    }

    @Test
    void silencesTheChannelThatWasMuted(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeProTracker(dir)).createRenderer(SAMPLE_RATE);
        final short[] buffer = new short[FRAMES * 2];

        renderer.render(buffer);
        assertTrue(peak(buffer) > 0, "the note in the pattern is heard");

        renderer.mute(PLAYING_CHANNEL, true);
        renderer.render(buffer);
        assertEquals(0, peak(buffer), "the only channel with a note on it went quiet");
        assertTrue(renderer.channels().get(PLAYING_CHANNEL - 1).muted(), "and says so");
        assertFalse(renderer.channels().get(PLAYING_CHANNEL).muted(), "while the others are left alone");

        renderer.mute(PLAYING_CHANNEL, false);
        renderer.render(buffer);
        assertTrue(peak(buffer) > 0, "and is heard again once it is let back in");
    }

    /**
     * A seek replays the song from the start on the mixer's own channels, which is where the flag lives, so
     * what the listener silenced has to be put back afterwards.
     */
    @Test
    void keepsTheMuteAcrossASeek(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeProTracker(dir)).createRenderer(SAMPLE_RATE);
        final short[] buffer = new short[FRAMES * 2];

        renderer.mute(PLAYING_CHANNEL, true);
        renderer.seek(Duration.ofSeconds(1));
        renderer.render(buffer);

        assertTrue(renderer.channels().get(PLAYING_CHANNEL - 1).muted());
        assertEquals(0, peak(buffer));
    }

    @Test
    void knowsTheSongLength(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeProTracker(dir)).createRenderer(SAMPLE_RATE);
        assertTrue(renderer.length().orElseThrow().toMillis() > 0);
    }

    /**
     * The first song of the Scream Tracker fixture holds no note at all, so the one behind the separator is
     * the song that sounds, and it sounds from the start.
     */
    @Test
    void skipsTheSongWithNoNotesBehindTheFirstSeparator(@TempDir Path dir) throws Exception {
        final Renderer renderer = createScreamTrackerRenderer(dir);
        final short[] buffer = new short[FRAMES * 2];

        assertEquals(FRAMES, renderer.render(buffer));
        assertTrue(peak(buffer) > 1000, "the song behind the separator should sound straight away");
    }

    /**
     * The songs of one file play back to back: once the first has run out the renderer moves on within the same
     * buffer, so the sound carries on past the first song's own length.
     */
    @Test
    void rendersPastTheFirstSubsongIntoTheSecond(@TempDir Path dir) throws Exception {
        final Renderer renderer = createScreamTrackerRenderer(dir);
        final short[] buffer = new short[FRAMES * 2];
        final int frames = (FIRST_SUBSONG_MILLIS + A_ROW_OR_TWO_MILLIS) * SAMPLE_RATE / 1000;

        for (int rendered = 0; rendered < frames; rendered += FRAMES) {
            assertEquals(FRAMES, renderer.render(buffer), rendered + " frames in");
        }
    }

    /**
     * JavaMod measures a module once and keeps that length, so subsongs of different lengths show whether each
     * was measured on its own.
     */
    @Test
    void measuresAFileAtItsSubsongsTogether(@TempDir Path dir) throws Exception {
        final Renderer renderer = createScreamTrackerRenderer(dir);

        assertEquals(FIRST_SUBSONG_MILLIS + SECOND_SUBSONG_MILLIS, renderer.length().orElseThrow().toMillis(), A_ROW_OR_TWO_MILLIS);
    }

    /**
     * A cue ends in a jump to the silent order the game waits in, which inside its own subsong would loop the
     * cue for as long as the mixer lets it; it fades out instead, as a song looping to its start does.
     */
    @Test
    void endsACueThatJumpsOutOfItsSubsong(@TempDir Path dir) throws Exception {
        final Renderer renderer = new JavaModLoader().load(TestModules.writeScreamTrackerWithACue(dir)).createRenderer(SAMPLE_RATE);
        final short[] buffer = new short[FRAMES * 2];
        final long frames = LONGER_THAN_ANY_FADE.toSeconds() * SAMPLE_RATE;

        long rendered = 0;
        for (int mixed = renderer.render(buffer); mixed > 0 && rendered < frames; mixed = renderer.render(buffer)) {
            rendered += mixed;
        }

        assertTrue(rendered < frames, "the cue should end, still playing after " + rendered + " frames");
        assertTrue(renderer.length().orElseThrow().compareTo(LONGER_THAN_ANY_FADE) < 0);
    }

    /**
     * A seek into the second song counts the position from the start of the file and keeps playing from there.
     */
    @Test
    void seeksIntoTheSecondSubsong(@TempDir Path dir) throws Exception {
        final Renderer renderer = createScreamTrackerRenderer(dir);
        final short[] buffer = new short[FRAMES * 2];
        final Duration target = renderer.length().orElseThrow().multipliedBy(3).dividedBy(4);

        renderer.seek(target);

        final Duration position = renderer.position();
        assertTrue(position.compareTo(target) >= 0, "seek should reach the requested moment, was " + position);
        assertEquals(FRAMES, renderer.render(buffer));
    }

    private static Renderer createScreamTrackerRenderer(Path dir) throws Exception {
        return new JavaModLoader().load(TestModules.writeScreamTrackerWithSubsongs(dir)).createRenderer(SAMPLE_RATE);
    }

    @Test
    void reducesThirtyTwoBitSamplesToSixteenBitWithClamping() {
        assertEquals(0, JavaModRenderer.toPcm16(0));
        assertEquals(1, JavaModRenderer.toPcm16(1 << 16));
        assertEquals(Short.MAX_VALUE, JavaModRenderer.toPcm16(Long.MAX_VALUE / 2));
        assertEquals(Short.MIN_VALUE, JavaModRenderer.toPcm16(Long.MIN_VALUE / 2));
    }

    private static int peak(short[] samples) {
        int peak = 0;
        for (short sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}

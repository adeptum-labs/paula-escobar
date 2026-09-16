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

package com.adeptum.paula.module.ay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AyRendererTest {

    private static final double SPECTRUM_CLOCK = 1773400;
    private static final int RATE = 44100;
    private static final int FRAMES_PER_SECOND = 50;
    private static final int BUFFER_FRAMES = 1024;
    private static final int AUDIBLE = 2000;

    private static final int TONE_A_FINE = 0;
    private static final int MIXER = 7;
    private static final int VOLUME_A = 8;
    private static final int TONE_A_ONLY = 0x3e;
    private static final int LOUD = 14;
    private static final int BY_ENVELOPE = 0x10;
    private static final int ENVELOPE_COARSE = 12;
    private static final int ENVELOPE_SHAPE = 13;
    private static final int SLIDE_DOWN = 8;

    @Test
    void soundsAToneFromARecording() {
        final AyRenderer renderer = renderer(tone(100));

        assertTrue(loudest(renderer) > AUDIBLE, "the tone should be heard");
    }

    @Test
    void lengthIsWhatTheFramesRunTo() {
        final AyRenderer renderer = renderer(tone(FRAMES_PER_SECOND * 3));

        assertEquals(Optional.of(Duration.ofSeconds(3)), renderer.length());
    }

    @Test
    void stopsOnceTheRecordingHasRunOut() {
        final AyRenderer renderer = renderer(tone(10));
        final short[] buffer = new short[BUFFER_FRAMES * 2];

        int rendered = 0;
        int written;
        do {
            written = renderer.render(buffer);
            rendered += written;
        } while (written > 0 && rendered < RATE);

        assertEquals(RATE * 10 / FRAMES_PER_SECOND, rendered);
        assertEquals(0, renderer.render(buffer));
    }

    @Test
    void positionFollowsWhatHasBeenRendered() {
        final AyRenderer renderer = renderer(tone(FRAMES_PER_SECOND * 5));
        final short[] buffer = new short[BUFFER_FRAMES * 2];

        for (int at = 0; at < 10; at++) {
            renderer.render(buffer);
        }

        assertEquals(BUFFER_FRAMES * 10L * 1000 / RATE, renderer.position().toMillis());
    }

    @Test
    void goesBackToWhereItIsSentAndSoundsAgain() {
        final AyRenderer renderer = renderer(tone(FRAMES_PER_SECOND * 5));
        final short[] buffer = new short[BUFFER_FRAMES * 2];
        renderer.render(buffer);

        renderer.seek(Duration.ofSeconds(4));

        assertEquals(4000, renderer.position().toMillis());
        assertTrue(loudest(renderer) > AUDIBLE, "the tone should still be heard after the seek");
    }

    /**
     * Writing the shape register is what starts an envelope over, so a recording says with a value of its own
     * that a frame left it alone. Were the standing shape written again every frame, a slow envelope would be
     * restarted before it had moved and the sound would never fade.
     */
    @Test
    void letsASlowEnvelopeRunRatherThanStartingItOverEveryFrame() {
        final AyRenderer renderer = renderer(envelopeSweep(FRAMES_PER_SECOND * 2));

        final int opening = loudest(renderer);
        for (int at = 0; at < 10; at++) {
            renderer.render(new short[BUFFER_FRAMES * 2]);
        }
        final int later = loudest(renderer);

        assertTrue(opening > AUDIBLE, "the envelope should open loud, was " + opening);
        assertTrue(later > AUDIBLE, "the envelope should still be sounding, was " + later);
        assertTrue(later < opening * 3 / 4,
                "the envelope should have slid down, was " + later + " against " + opening);
    }

    private static AyRenderer renderer(RegisterFrames frames) {
        return new AyRenderer(new RegisterStream(frames), AyChip.Voicing.AY, SPECTRUM_CLOCK,
                FRAMES_PER_SECOND, RATE);
    }

    /**
     * One steady tone on the first channel, loud enough to measure, for as many frames as asked.
     */
    private static RegisterFrames tone(int count) {
        final byte[] values = new byte[count * RegisterFrames.REGISTERS];
        for (int frame = 0; frame < count; frame++) {
            final int base = frame * RegisterFrames.REGISTERS;
            values[base + TONE_A_FINE] = (byte) 0xfd;
            values[base + MIXER] = (byte) TONE_A_ONLY;
            values[base + VOLUME_A] = LOUD;
            values[base + ENVELOPE_SHAPE] = (byte) RegisterFrames.SHAPE_UNTOUCHED;
        }
        return new RegisterFrames(values, count);
    }

    /**
     * One long slide down the envelope, started in the first frame and left alone after it.
     */
    private static RegisterFrames envelopeSweep(int count) {
        final byte[] values = new byte[count * RegisterFrames.REGISTERS];
        for (int frame = 0; frame < count; frame++) {
            final int base = frame * RegisterFrames.REGISTERS;
            values[base + TONE_A_FINE] = (byte) 0xfd;
            values[base + MIXER] = (byte) TONE_A_ONLY;
            values[base + VOLUME_A] = (byte) BY_ENVELOPE;
            values[base + ENVELOPE_COARSE] = 0x20;
            values[base + ENVELOPE_SHAPE] = (byte) (frame == 0 ? SLIDE_DOWN : RegisterFrames.SHAPE_UNTOUCHED);
        }
        return new RegisterFrames(values, count);
    }

    private static int loudest(AyRenderer renderer) {
        final short[] buffer = new short[BUFFER_FRAMES * 2];
        int loudest = 0;
        for (int at = 0; at < 8; at++) {
            final int frames = renderer.render(buffer);
            for (int sample = 0; sample < frames * 2; sample++) {
                loudest = Math.max(loudest, Math.abs(buffer[sample]));
            }
        }
        return loudest;
    }
}

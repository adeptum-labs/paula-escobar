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

package com.adeptum.paula.module.composer669;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class C669EngineTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAMES = 4096;
    private static final int AUDIBLE = 1000;
    private static final int C5 = 8363;
    private static final int C3 = 2090;
    private static final int C6 = 16726;
    private static final int TICKS_IN_FIVE_SECONDS = 156;
    private static final int SPEED = 4;
    private static final int FULL_VOLUME = 64;
    private static final int PORTAMENTO_UP = 0;
    private static final int PORTAMENTO_DOWN = 1;
    private static final int TONE_PORTAMENTO = 2;
    private static final int FINETUNE = 3;
    private static final int VIBRATO = 4;
    private static final int SET_SPEED = 5;
    private static final int PAN_SLIDE = 6;
    private static final int RETRIGGER = 7;
    private static final int UNKNOWN = 12;
    private static final int LONGER_THAN_THE_SONG = 100_000;

    /**
     * Ticks in the fixture: the first pattern's two rows at speed four, its remaining sixty-two rows at the
     * three the third row sets, then the second pattern's four rows at speed two before the break.
     */
    private static final int FIXTURE_TICKS = 2 * 4 + 62 * 3 + 4 * 2;

    private static C669Engine fixture() throws IOException {
        return new C669Engine(C669Reader.read(TestModules.composer669()), SAMPLE_RATE);
    }

    private static C669Engine engine(C669Cell... channelZero) {
        return new C669Engine(fileWith(false, channelZero), SAMPLE_RATE);
    }

    private static C669Engine extended(C669Cell... channelZero) {
        return new C669Engine(fileWith(true, channelZero), SAMPLE_RATE);
    }

    private static void ticks(C669Engine engine, int count) {
        for (int tick = 0; tick < count; tick++) {
            engine.nextTick();
        }
    }

    @Test
    void soundsTheNoteOnTheFirstRow() throws IOException {
        final C669Engine engine = fixture();
        engine.nextTick();

        final C669Voice voice = engine.voice(0);
        assertTrue(voice.sounding);
        assertEquals(1, voice.instrument);
        assertEquals(C5, voice.frequency, "note 24 is C-5, the rate every sample is recorded at");
        assertEquals(FULL_VOLUME, voice.volume);
        assertFalse(engine.voice(1).sounding, "and the other channels were left empty");
    }

    @Test
    void placesTheChannelsLeftAndRightInTurn() throws IOException {
        final C669Engine engine = fixture();

        assertEquals(0x30, engine.voice(0).panning);
        assertEquals(0xD0, engine.voice(1).panning);
        assertEquals(0x30, engine.voice(6).panning);
    }

    @Test
    void tunesNotesInEqualTemperamentFromC5() {
        assertEquals(C3, C669Engine.frequencyOf(0));
        assertEquals(C5, C669Engine.frequencyOf(24));
        assertEquals(C6, C669Engine.frequencyOf(36));
        assertEquals(8860, C669Engine.frequencyOf(25), "a semitone up, as OpenMPT's integer arithmetic has it");
    }

    @Test
    void setsAVolumeWithoutSoundingANote() throws IOException {
        final C669Engine engine = fixture();
        ticks(engine, SPEED + 1);

        final C669Voice voice = engine.voice(TestModules.C669_VOLUME_CHANNEL);
        assertFalse(voice.sounding);
        assertEquals((TestModules.C669_HALF_VOLUME * FULL_VOLUME + 8) / 15, voice.volume);
    }

    @Test
    void keepsSeventyEightBeatsAMinuteWhateverTheSong() throws IOException {
        final C669Engine engine = fixture();
        long frames = 0;
        for (int tick = 0; tick < TICKS_IN_FIVE_SECONDS; tick++) {
            engine.nextTick();
            frames += engine.tickFrames();
        }

        assertEquals(5L * SAMPLE_RATE, frames, "31.2 ticks a second, the remainder carried rather than lost");
    }

    @Test
    void takesTheSpeedFromThePatternAndThenFromEffectF() throws IOException {
        final C669Engine engine = fixture();
        assertEquals(TestModules.C669_SPEEDS[0], engine.speed());

        ticks(engine, SPEED);
        assertEquals(1, engine.row(), "four ticks a row to begin with");
        ticks(engine, SPEED);
        assertEquals(2, engine.row());
        ticks(engine, TestModules.C669_NEW_SPEED);
        assertEquals(3, engine.row(), "and three once the third row asked for it");
    }

    @Test
    void ignoresASpeedOfZero() {
        final C669Engine engine = engine(effect(SET_SPEED, 0));
        engine.nextTick();

        assertEquals(SPEED, engine.speed());
    }

    @Test
    void breaksThePatternAfterItsBreakRowAndEndsWithTheOrderList() throws IOException {
        final C669Engine engine = fixture();
        ticks(engine, FIXTURE_TICKS - 1);
        assertEquals(1, engine.order());
        assertEquals(TestModules.C669_BREAKS[1], engine.row(), "the second pattern stops at its break row");

        engine.nextTick();
        assertFalse(engine.hasEnded(), "the last tick still sounds");
        engine.nextTick();
        assertTrue(engine.hasEnded(), "and the song is over, since no order follows");
    }

    @Test
    void slidesUpByEightyHertzATickAndCarriesOnUntilANote() {
        final C669Engine engine = engine(noteWith(24, PORTAMENTO_UP, 2), C669Cell.EMPTY, note(24));
        engine.nextTick();
        assertEquals(C5 + 160, engine.voice(0).frequency, "the first tick slides as well");
        ticks(engine, SPEED - 1);
        assertEquals(C5 + 4 * 160, engine.voice(0).frequency);
        ticks(engine, SPEED);
        assertEquals(C5 + 8 * 160, engine.voice(0).frequency, "an empty row keeps the slide going");
        engine.nextTick();
        assertEquals(C5, engine.voice(0).frequency, "until a note lands");
    }

    @Test
    void slidesDownNoFurtherThanTheLowestFrequency() {
        final C669Engine engine = engine(noteWith(0, PORTAMENTO_DOWN, 15));
        ticks(engine, 2);

        assertEquals(C669Engine.LOWEST_FREQUENCY, engine.voice(0).frequency);
    }

    @Test
    void endsACarriedSlideWithAnyOtherEffect() {
        final C669Engine engine = engine(noteWith(24, PORTAMENTO_UP, 1), effect(FINETUNE, 0), C669Cell.EMPTY);
        ticks(engine, 2 * SPEED);

        assertEquals(C5 + 4 * 80, engine.voice(0).frequency, "the second row's effect stopped it");
    }

    @Test
    void slidesTowardsANoteByFortyHertzATickWithoutRestartingTheSample() {
        final C669Engine engine = engine(note(24), noteWith(36, TONE_PORTAMENTO, 4), C669Cell.EMPTY);
        ticks(engine, SPEED);
        engine.voice(0).position = 10;
        engine.nextTick();

        assertEquals(C5 + 160, engine.voice(0).frequency);
        assertEquals(10, engine.voice(0).position, "the sample plays on from where it was");
        ticks(engine, LONGER_THAN_THE_SONG / 1000);
        assertEquals(C6, engine.voice(0).frequency, "the slide carries on over the empty row and stops at the note");
    }

    @Test
    void reusesTheLastPortamentoSpeedForARowWithoutOne() {
        final C669Engine engine = engine(note(24), noteWith(36, TONE_PORTAMENTO, 1), effect(TONE_PORTAMENTO, 0),
                C669Cell.EMPTY);
        ticks(engine, 3 * SPEED);
        assertEquals(C5 + 8 * 40, engine.voice(0).frequency, "the third row slid at the second's speed");
        ticks(engine, SPEED);
        assertEquals(C5 + 8 * 40, engine.voice(0).frequency, "but did not carry it on");
    }

    @Test
    void finetunesByEightyHertzForTheRestOfTheNote() {
        final C669Engine engine = engine(noteWith(24, FINETUNE, 3), C669Cell.EMPTY, note(24));
        ticks(engine, 2 * SPEED);
        assertEquals(C5, engine.voice(0).frequency, "the note itself is untouched");
        assertEquals(C5 + 240, engine.soundingFrequency(engine.voice(0)), "but what sounds is raised");
        engine.nextTick();
        assertEquals(C5, engine.soundingFrequency(engine.voice(0)), "until the next note");
    }

    @Test
    void raisesTheFrequencyEveryOtherTickForVibrato() {
        final C669Engine engine = engine(noteWith(24, VIBRATO, 2), C669Cell.EMPTY);
        final int[] sounding = new int[SPEED + 1];
        for (int tick = 0; tick < sounding.length; tick++) {
            engine.nextTick();
            sounding[tick] = engine.soundingFrequency(engine.voice(0));
        }

        assertEquals(List.of(C5, C5 + 1336, C5, C5 + 1336, C5), Arrays.stream(sounding).boxed().toList(),
                "668 Hz a step on the odd ticks, and none on the row after");
    }

    @Test
    void slidesThePanningSixteenARowUntilANote() {
        final C669Engine engine = extended(noteWith(24, PAN_SLIDE, 1), C669Cell.EMPTY, note(24));
        engine.nextTick();
        assertEquals(0x30 + 16, engine.voice(0).panning);
        ticks(engine, SPEED);
        assertEquals(0x30 + 32, engine.voice(0).panning, "the empty row slides again");
        ticks(engine, SPEED);
        assertEquals(0x30 + 32, engine.voice(0).panning, "the note ends it");
    }

    @Test
    void retriggersTheNoteEverySoManyTicksInUnis669Only() {
        final C669Engine unis = extended(noteWith(24, RETRIGGER, 2));
        unis.nextTick();
        unis.voice(0).position = 10;
        unis.nextTick();
        assertEquals(10, unis.voice(0).position, "not on the first tick");
        unis.nextTick();
        assertEquals(0, unis.voice(0).position, "but on the second");

        final C669Engine composer = engine(noteWith(24, RETRIGGER, 2));
        composer.nextTick();
        composer.voice(0).position = 10;
        ticks(composer, 2);
        assertEquals(10, composer.voice(0).position, "Composer 669 has no such effect");
    }

    @Test
    void leavesAnUnknownEffectAlone() {
        final C669Engine engine = engine(noteWith(24, UNKNOWN, 5));
        ticks(engine, SPEED);

        assertEquals(C5, engine.voice(0).frequency);
        assertEquals(SPEED, engine.speed());
    }

    @Test
    void silencesAChannelWhoseInstrumentDoesNotExist() {
        final C669Engine engine = engine(note(24), new C669Cell(24, 40, C669Cell.NONE, C669Cell.NONE, 0));
        ticks(engine, SPEED + 1);

        assertFalse(engine.voice(0).sounding);
    }

    @Test
    void mixesTheNoteIntoBothSides() throws IOException {
        final C669Engine engine = fixture();
        final short[] out = new short[FRAMES * 2];

        assertEquals(FRAMES, engine.mix(out, FRAMES), "the song is still playing");
        assertTrue(loudest(out, 0) > AUDIBLE, "the square is heard on the left, peak was " + loudest(out, 0));
        assertTrue(loudest(out, 1) > 0, "and a little on the right");
    }

    @Test
    void mixesNothingOnceTheSongHasEnded() throws IOException {
        final C669Engine engine = fixture();
        ticks(engine, FIXTURE_TICKS);

        assertEquals(0, engine.mix(new short[FRAMES * 2], FRAMES));
    }

    @Test
    void measuresTheSongByPlayingItThrough() throws IOException {
        final C669Engine engine = fixture();
        long frames = 0;
        for (int tick = 0; tick < FIXTURE_TICKS; tick++) {
            engine.nextTick();
            frames += engine.tickFrames();
        }

        assertEquals(frames, C669Engine.songFrames(C669Reader.read(TestModules.composer669()), SAMPLE_RATE,
                LONGER_THAN_THE_SONG * SAMPLE_RATE).orElseThrow());
    }

    @Test
    void givesUpMeasuringASongThatRunsPastTheLimit() throws IOException {
        assertTrue(C669Engine.songFrames(C669Reader.read(TestModules.composer669()), SAMPLE_RATE, 1).isEmpty());
    }

    private static int loudest(short[] out, int side) {
        int peak = 0;
        for (int i = side; i < out.length; i += 2) {
            peak = Math.max(peak, Math.abs(out[i]));
        }
        return peak;
    }

    private static C669Cell note(int note) {
        return new C669Cell(note, 0, 15, C669Cell.NONE, 0);
    }

    private static C669Cell noteWith(int note, int command, int parameter) {
        return new C669Cell(note, 0, 15, command, parameter);
    }

    private static C669Cell effect(int command, int parameter) {
        return new C669Cell(C669Cell.NONE, C669Cell.NONE, C669Cell.NONE, command, parameter);
    }

    /**
     * One pattern at speed four with the given cells down channel zero, a looping square as the only sample,
     * played once.
     */
    private static C669File fileWith(boolean extended, C669Cell... channelZero) {
        final C669Cell[][] cells = new C669Cell[C669Pattern.ROWS][C669Pattern.CHANNELS];
        for (final C669Cell[] row : cells) {
            Arrays.fill(row, C669Cell.EMPTY);
        }
        for (int row = 0; row < channelZero.length; row++) {
            cells[row][0] = channelZero[row];
        }
        final short[] square = new short[64];
        Arrays.fill(square, 0, 32, (short) 25600);
        Arrays.fill(square, 32, 64, (short) -25600);
        return new C669File(extended, List.of("test", "", ""), List.of(new C669Sample("square", square, 0, 64)),
                List.of(new C669Pattern(SPEED, C669Pattern.ROWS - 1, cells)), new int[] {0}, 0);
    }
}

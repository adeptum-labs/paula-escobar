package com.adeptum.paula.module.xtracker;

import static com.adeptum.paula.module.xtracker.DmfFiles.C3;
import static com.adeptum.paula.module.xtracker.DmfFiles.C3_NOTE;
import static com.adeptum.paula.module.xtracker.DmfFiles.ROW_FRAMES;
import static com.adeptum.paula.module.xtracker.DmfFiles.SAMPLE_RATE;
import static com.adeptum.paula.module.xtracker.DmfFiles.empty;
import static com.adeptum.paula.module.xtracker.DmfFiles.engine;
import static com.adeptum.paula.module.xtracker.DmfFiles.instrumentEffect;
import static com.adeptum.paula.module.xtracker.DmfFiles.note;
import static com.adeptum.paula.module.xtracker.DmfFiles.noteEffect;
import static com.adeptum.paula.module.xtracker.DmfFiles.playRows;
import static com.adeptum.paula.module.xtracker.DmfFiles.rows;
import static com.adeptum.paula.module.xtracker.DmfFiles.track;
import static com.adeptum.paula.module.xtracker.DmfFiles.units;
import static com.adeptum.paula.module.xtracker.DmfFiles.volumeEffect;
import static com.adeptum.paula.module.xtracker.DmfFiles.withVolume;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DmfEffectsTest {

    private static final int NONE = DmfTrackEntry.NONE;
    private static final int SQUARE = 1;
    private static final int RAMP = 2;
    private static final int WIDE = 3;
    private static final int SEMITONE = DmfChannel.SEMITONE;
    private static final int C3_PITCH = 36 * SEMITONE;
    private static final double STEP = (double) C3 / SAMPLE_RATE;

    private static final int STOP_SAMPLE = 1;
    private static final int STOP_LOOP = 2;
    private static final int RESTART = 3;
    private static final int SAMPLE_DELAY = 4;
    private static final int RETRIG = 5;
    private static final int OFFSET = 6;
    private static final int OFFSET_64K = 7;
    private static final int INVERT = 10;

    private static final int FINETUNE = 1;
    private static final int NOTE_DELAY = 2;
    private static final int ARPEGGIO = 3;
    private static final int PORTAMENTO_UP = 4;
    private static final int PORTAMENTO_DOWN = 5;
    private static final int TONE_PORTAMENTO = 6;
    private static final int SCRATCH = 7;
    private static final int VIBRATO_SINE = 8;
    private static final int NOTE_TREMOLO = 11;
    private static final int NOTE_CUT = 12;

    private static final int VOLUME_UP = 1;
    private static final int VOLUME_DOWN = 2;
    private static final int VOLUME_TREMOLO = 3;
    private static final int TREMOLO_SINE = 4;
    private static final int SET_BALANCE = 7;
    private static final int BALANCE_LEFT = 8;
    private static final int BALANCE_RIGHT = 9;
    private static final int BALANCE_VIBRATO = 10;

    private static DmfTrackEntry c3(int instrument) {
        return note(C3_NOTE, instrument);
    }

    private static double semitones(double frequency) {
        return 12 * Math.log(frequency / C3) / Math.log(2);
    }

    // Instrument effects

    @Test
    void stopsTheSampleSoThatANoteWithoutAnInstrumentBringsNothingBack() {
        final DmfEngine engine = engine(track(c3(SQUARE), instrumentEffect(empty(), STOP_SAMPLE, 0), note(C3_NOTE + 1, NONE)));
        rows(engine, 2);
        engine.nextUnit();

        assertFalse(engine.channel(0).voice.sounding);
    }

    @Test
    void releasesTheLoopOnStopSampleLoop() {
        final DmfEngine engine = engine(track(c3(SQUARE), instrumentEffect(empty(), STOP_LOOP, 0)));
        rows(engine, 1);
        engine.nextUnit();

        assertTrue(engine.channel(0).voice.released);
    }

    @Test
    void restartsTheLastNoteWithoutTheSamplesVolume() {
        final DmfEngine engine = engine(track(withVolume(c3(RAMP), 50), instrumentEffect(empty(), RESTART, 0)));
        playRows(engine, 1);
        assertTrue(engine.channel(0).voice.position > 0);
        engine.nextUnit();

        assertEquals(0, engine.channel(0).voice.position, 1e-9);
        assertEquals(50, engine.channel(0).loudness());
    }

    @Test
    void startsTheSampleAfterTheUnitsASampleDelayAsksFor() {
        final DmfEngine engine = engine(track(instrumentEffect(c3(RAMP), SAMPLE_DELAY, 64)));
        units(engine, 64);
        assertEquals(null, engine.channel(0).voice.sample, "nothing has started after 64 units");

        engine.nextUnit();
        assertTrue(engine.channel(0).voice.sounding);
    }

    @Test
    void retriggersTheSampleEveryDataUnits() {
        final DmfEngine engine = engine(track(instrumentEffect(c3(RAMP), RETRIG, 100)));
        final int hundredUnits = 100 * ROW_FRAMES / DmfTempo.UNITS_PER_ROW;
        engine.mix(new short[(hundredUnits + 1) * 2], hundredUnits + 1);

        assertEquals(STEP, engine.channel(0).voice.position, 1e-9, "back at the head one frame into the 100th unit");
    }

    @Test
    void jumpsToTheOffsetInBlocksOf256Bytes() {
        final DmfEngine engine = engine(track(instrumentEffect(c3(RAMP), OFFSET, 2)));
        engine.nextUnit();
        assertEquals(512, engine.channel(0).voice.position, 1e-9);

        final DmfEngine wide = engine(track(instrumentEffect(c3(WIDE), OFFSET, 2)));
        wide.nextUnit();
        assertEquals(256, wide.channel(0).voice.position, 1e-9, "two bytes a frame in a 16-bit sample");

        final DmfEngine past = engine(track(instrumentEffect(c3(RAMP), OFFSET_64K, 0)));
        past.nextUnit();
        assertFalse(past.channel(0).voice.sounding, "an offset past the end leaves nothing to sound");
    }

    @Test
    void restartsTheLastNoteAtAnOffsetGivenWithoutANote() {
        final DmfEngine engine = engine(track(c3(RAMP), instrumentEffect(empty(), OFFSET, 1)));
        rows(engine, 1);
        engine.nextUnit();

        assertEquals(256, engine.channel(0).voice.position, 1e-9);
    }

    @Test
    void turnsTheSampleAroundWhereItStandsAndANewNotePlaysForwards() {
        final DmfEngine engine = engine(track(c3(RAMP), instrumentEffect(empty(), INVERT, 0), c3(RAMP)));
        playRows(engine, 1);
        final double standing = engine.channel(0).voice.position;
        engine.mix(new short[200], 100);

        assertTrue(engine.channel(0).voice.backwards);
        assertTrue(engine.channel(0).voice.position < standing);
        playRows(engine, 1);
        engine.nextUnit();
        assertFalse(engine.channel(0).voice.backwards);
    }

    // Note effects

    @Test
    void finetunesInHundredTwentyEighthsOfASemitoneUntilTheNextNote() {
        final DmfEngine up = engine(track(noteEffect(c3(SQUARE), FINETUNE, 64), c3(SQUARE)));
        up.nextUnit();
        assertEquals(C3 * Math.pow(2, 64 / 1536.0), up.channel(0).frequency(), 1e-9);
        rows(up, 1);
        up.nextUnit();
        assertEquals(C3, up.channel(0).frequency(), 1e-9);

        final DmfEngine down = engine(track(noteEffect(c3(SQUARE), FINETUNE, 0xC0)));
        down.nextUnit();
        assertEquals(C3 * Math.pow(2, -64 / 1536.0), down.channel(0).frequency(), 1e-9, "the data is signed");
    }

    @Test
    void holdsBackThePitchChangeByTheUnitsANoteDelayAsksFor() {
        final DmfEngine engine = engine(track(c3(SQUARE), noteEffect(note(C3_NOTE + 12, NONE), NOTE_DELAY, 128)));
        rows(engine, 1);
        units(engine, 128);
        assertEquals(C3, engine.channel(0).frequency(), 1e-9);

        engine.nextUnit();
        assertEquals(C3 * 2.0, engine.channel(0).frequency(), 1e-9);
    }

    @Test
    void stepsThroughTheArpeggioInThirdsOfARow() {
        final DmfEngine engine = engine(track(noteEffect(c3(SQUARE), ARPEGGIO, 0x47)));
        units(engine, 86);
        assertEquals(0, semitones(engine.channel(0).frequency()), 1e-9);
        units(engine, 1);
        assertEquals(4, semitones(engine.channel(0).frequency()), 1e-9);
        units(engine, 85);
        assertEquals(7, semitones(engine.channel(0).frequency()), 1e-9);
    }

    @Test
    void slidesDataSixteenthsOfASemitoneARowUntilTheNextEntry() {
        final DmfEngine engine = engine(track(noteEffect(c3(SQUARE), PORTAMENTO_UP, 16), null, empty()));
        rows(engine, 1);
        assertEquals(C3_PITCH + SEMITONE, engine.channel(0).pitch);
        rows(engine, 1);
        assertEquals(C3_PITCH + 2 * SEMITONE, engine.channel(0).pitch, "a row without an entry carries on");
        rows(engine, 1);
        assertEquals(C3_PITCH + 2 * SEMITONE, engine.channel(0).pitch, "an entry ends it");
    }

    @Test
    void keepsPortamentosBetweenCZeroAndBEight() {
        final DmfEngine down = engine(track(noteEffect(note(1, SQUARE), PORTAMENTO_DOWN, 255)));
        rows(down, 1);
        assertEquals(0, down.channel(0).pitch);

        final DmfEngine up = engine(track(noteEffect(note(108, SQUARE), PORTAMENTO_UP, 255)));
        rows(up, 1);
        assertEquals(107 * SEMITONE, up.channel(0).pitch);
    }

    @Test
    void slidesTowardsTheBufferNoteAndStopsThere() {
        final int buffered = C3_NOTE + 2 + DmfTrackEntry.BUFFER_OFFSET;
        final DmfEngine engine = engine(track(c3(SQUARE), noteEffect(note(buffered, NONE), TONE_PORTAMENTO, 16)));
        rows(engine, 2);
        assertEquals(C3_PITCH + SEMITONE, engine.channel(0).pitch);
        rows(engine, 2);
        assertEquals(C3_PITCH + 2 * SEMITONE, engine.channel(0).pitch);
    }

    @Test
    void doesNotSlideWithoutABufferNote() {
        final DmfEngine engine = engine(track(c3(SQUARE), noteEffect(empty(), TONE_PORTAMENTO, 16)));
        rows(engine, 2);

        assertEquals(C3_PITCH, engine.channel(0).pitch);
    }

    @Test
    void scratchesInWholeSemitonesAndArrivesAtTheEndOfTheRow() {
        final DmfEngine engine = engine(track(c3(SQUARE), noteEffect(empty(), SCRATCH, 48)));
        rows(engine, 1);
        units(engine, 129);
        assertEquals((36 + 12 * 17 / 32) * SEMITONE, engine.channel(0).pitch, "seventeen of 32 steps along");

        rows(engine, 1);
        assertEquals(48 * SEMITONE, engine.channel(0).pitch);
    }

    @Test
    void drawsTheThreeWaveforms() {
        assertEquals(1, DmfChannel.wave(0, 0.25), 1e-9);
        assertEquals(1, DmfChannel.wave(1, 0.25), 1e-9);
        assertEquals(0, DmfChannel.wave(1, 0.5), 1e-9);
        assertEquals(-1, DmfChannel.wave(1, 0.75), 1e-9);
        assertEquals(-1, DmfChannel.wave(2, 0.75), 1e-9);
    }

    @Test
    void swingsTheNoteTwoSemitonesEitherWayAtFullDepth() {
        final DmfEngine engine = engine(track(noteEffect(c3(SQUARE), VIBRATO_SINE, 0x1F)));
        units(engine, 65);

        assertEquals(2, semitones(engine.channel(0).frequency()), 1e-9, "a quarter into a period of one row");
    }

    @Test
    void alternatesTheNoteOnAndOffInFifteenthsOfHalfARow() {
        final DmfEngine engine = engine(track(noteEffect(c3(SQUARE), NOTE_TREMOLO, 0xF5)));
        units(engine, 128);
        assertTrue(engine.channel(0).frequency() > 0);
        units(engine, 1);
        assertEquals(0, engine.channel(0).frequency(), 1e-9);
        units(engine, 42);
        assertTrue(engine.channel(0).frequency() > 0, "five fifteenths of half a row later");
    }

    @Test
    void cutsTheNoteAfterTheUnitsGiven() {
        final DmfEngine engine = engine(track(noteEffect(c3(SQUARE), NOTE_CUT, 100)));
        units(engine, 100);
        assertTrue(engine.channel(0).frequency() > 0);
        engine.nextUnit();
        assertEquals(0, engine.channel(0).frequency(), 1e-9);

        final DmfEngine atOnce = engine(track(noteEffect(c3(SQUARE), NOTE_CUT, 0)));
        atOnce.nextUnit();
        assertEquals(0, atOnce.channel(0).frequency(), 1e-9);
    }

    // Volume effects

    @Test
    void slidesTheVolumeByDataARowWithinWhatItCanBe() {
        final DmfEngine up = engine(track(volumeEffect(withVolume(c3(SQUARE), 100), VOLUME_UP, 33)));
        rows(up, 1);
        assertEquals(133, up.channel(0).loudness(), "the row's total exact though 33 does not share evenly");
        rows(up, 5);
        assertEquals(DmfVoice.FULL, up.channel(0).loudness());

        final DmfEngine down = engine(track(volumeEffect(withVolume(c3(SQUARE), 100), VOLUME_DOWN, 64)));
        rows(down, 2);
        assertEquals(0, down.channel(0).loudness());
    }

    @Test
    void mutesTheVolumeInItsTremolosOffTime() {
        final DmfEngine engine = engine(track(volumeEffect(c3(SQUARE), VOLUME_TREMOLO, 0xF5)));
        units(engine, 128);
        assertEquals(DmfVoice.FULL, engine.channel(0).loudness());
        units(engine, 1);
        assertEquals(0, engine.channel(0).loudness());
    }

    @Test
    void swingsTheWholeVolumeAtFullTremoloDepth() {
        final DmfEngine engine = engine(track(volumeEffect(c3(SQUARE), TREMOLO_SINE, 0x1F)));
        units(engine, 65);
        assertEquals(DmfVoice.FULL, engine.channel(0).loudness());
        units(engine, 128);
        assertEquals(0, engine.channel(0).loudness());
    }

    @Test
    void setsAndSlidesTheBalance() {
        final DmfEngine set = engine(track(volumeEffect(c3(SQUARE), SET_BALANCE, 32)));
        set.nextUnit();
        assertEquals(32, set.channel(0).panning());

        final DmfEngine left = engine(track(volumeEffect(c3(SQUARE), BALANCE_LEFT, 64)));
        rows(left, 1);
        assertEquals(DmfVoice.MIDDLE - 64, left.channel(0).panning());

        final DmfEngine right = engine(track(volumeEffect(c3(SQUARE), BALANCE_RIGHT, 64)));
        rows(right, 3);
        assertEquals(DmfVoice.FULL, right.channel(0).panning());
    }

    @Test
    void swingsTheBalanceHalfTheWidthEitherWayAtFullDepth() {
        final DmfEngine engine = engine(track(volumeEffect(c3(SQUARE), BALANCE_VIBRATO, 0x1F)));
        units(engine, 65);
        assertEquals(DmfVoice.FULL, engine.channel(0).panning());
        units(engine, 128);
        assertEquals(0, engine.channel(0).panning());
    }

    @Test
    void endsWhateverTheChannelCarriedOnItsNextEntry() {
        final DmfEngine engine = engine(track(volumeEffect(withVolume(c3(SQUARE), 200), VOLUME_DOWN, 64), empty()));
        rows(engine, 2);

        assertEquals(136, engine.channel(0).loudness());
    }

    @Test
    void letsAllThreeColumnsActTogether() {
        final DmfTrackEntry all = volumeEffect(noteEffect(instrumentEffect(c3(RAMP), OFFSET, 1), PORTAMENTO_UP, 16),
                BALANCE_RIGHT, 64);
        final DmfEngine engine = engine(track(all));
        engine.nextUnit();
        assertEquals(256, engine.channel(0).voice.position, 1e-9);
        units(engine, DmfTempo.UNITS_PER_ROW - 1);

        assertEquals(C3_PITCH + SEMITONE, engine.channel(0).pitch);
        assertEquals(DmfVoice.MIDDLE + 64, engine.channel(0).panning());
    }
}

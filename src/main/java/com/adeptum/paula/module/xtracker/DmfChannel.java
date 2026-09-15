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

package com.adeptum.paula.module.xtracker;

import java.util.Arrays;

/**
 * One track of the song while it plays: the voice it sounds through, the note and instrument it last had, its
 * pitch, volume and balance, and the effects its last entry set going. Pitch counts 128ths of a semitone up from
 * C-0, and a sample sounds at its own C-3 frequency on C-3. Timed effects act on the unit they fall on; continuous
 * ones move every eighth unit, a 32nd of a row. A slide shares its whole amount out over the steps of the
 * entry's span to the track's next entry, so the total comes out exact as the span ends and holds from there.
 * Everything an entry sets going lasts until the track's next entry.
 */
final class DmfChannel {

    static final int SEMITONE = 128;
    static final int UNITS_PER_STEP = 8;

    private static final int NONE = DmfTrackEntry.NONE;
    private static final int ROW = DmfTempo.UNITS_PER_ROW;
    private static final int HALF_ROW = ROW / 2;
    private static final int STEPS_PER_ROW = ROW / UNITS_PER_STEP;
    private static final int C3_PITCH = 36 * SEMITONE;
    private static final int HIGHEST_NOTE = 107;
    private static final int HIGHEST_PITCH = HIGHEST_NOTE * SEMITONE;
    private static final double OCTAVE = 12.0 * SEMITONE;
    private static final int SIXTEENTH = SEMITONE / 16;
    private static final int VIBRATO_RANGE = 2 * SEMITONE;
    private static final double DEPTH_STEPS = 15;
    private static final int OFFSET_BLOCK = 256;
    private static final int HIGH_OFFSET_BLOCK = 0x10000;
    private static final int NIBBLE_BITS = 4;
    private static final int NIBBLE = 0x0F;
    private static final int ARPEGGIO_STEPS = 3;
    private static final int NOTE_COLUMN = 0;
    private static final int VOLUME_COLUMN = 1;
    private static final int SINE = 0;
    private static final int TRIANGLE = 1;

    private static final int STOP_SAMPLE = 1;
    private static final int STOP_LOOP = 2;
    private static final int RESTART = 3;
    private static final int SAMPLE_DELAY = 4;
    private static final int RETRIG = 5;
    private static final int OFFSET = 6;
    private static final int LAST_OFFSET = 9;
    private static final int INVERT = 10;

    private static final int FINETUNE = 1;
    private static final int NOTE_DELAY = 2;
    private static final int ARPEGGIO = 3;
    private static final int PORTAMENTO_UP = 4;
    private static final int PORTAMENTO_DOWN = 5;
    private static final int TONE_PORTAMENTO = 6;
    private static final int SCRATCH = 7;
    private static final int VIBRATO_SINE = 8;
    private static final int VIBRATO_TRIANGLE = 9;
    private static final int VIBRATO_SQUARE = 10;
    private static final int NOTE_TREMOLO = 11;
    private static final int NOTE_CUT = 12;

    private static final int VOLUME_UP = 1;
    private static final int VOLUME_DOWN = 2;
    private static final int VOLUME_TREMOLO = 3;
    private static final int TREMOLO_SINE = 4;
    private static final int TREMOLO_TRIANGLE = 5;
    private static final int TREMOLO_SQUARE = 6;
    private static final int SET_BALANCE = 7;
    private static final int BALANCE_LEFT = 8;
    private static final int BALANCE_RIGHT = 9;
    private static final int BALANCE_VIBRATO = 10;

    final DmfVoice voice = new DmfVoice();
    int instrument;
    int note;
    int pitch;
    int finetune;
    int volume = DmfVoice.FULL;
    int balance = DmfVoice.MIDDLE;
    boolean held;

    private final DmfFile file;
    private final int[] shared = new int[2];
    private int spanSteps = STEPS_PER_ROW;
    private int stepsTaken;
    private DmfTrackEntry pending;
    private boolean touchDue;
    private boolean tuneDue;
    private int touchAt;
    private int tuneAt;
    private int unitsSinceEntry;
    private int unitsSinceStart;
    private int instrumentEffect = NONE;
    private int instrumentData;
    private int noteEffect = NONE;
    private int noteData;
    private int volumeEffect = NONE;
    private int volumeData;
    private int bufferPitch = NONE;
    private int scratchFrom;
    private int scratchSteps;
    private int arpeggio;
    private double vibrato;
    private double tremolo = 1;
    private double balanceSwing;
    private boolean noteSilenced;
    private boolean volumeSilenced;

    DmfChannel(DmfFile file) {
        this.file = file;
    }

    /**
     * A new entry on the track: whatever the channel carried ends and a balance is set at once, while the note,
     * instrument and volume wait for the unit a sample delay names and the pitch for the one a note delay names.
     * {@code spanRows} is the rows to the track's next entry, the span any slide the entry starts moves over.
     */
    void entry(DmfTrackEntry entry, int spanRows) {
        pending = entry;
        resetCarriedEffects();
        instrumentEffect = entry.instrumentEffect();
        instrumentData = entry.instrumentData();
        noteEffect = entry.noteEffect();
        noteData = entry.noteData();
        volumeEffect = entry.volumeEffect();
        volumeData = entry.volumeData();
        unitsSinceEntry = 0;
        spanSteps = Math.max(1, spanRows) * STEPS_PER_ROW;
        touchAt = instrumentEffect == SAMPLE_DELAY ? instrumentData : 0;
        tuneAt = noteEffect == NOTE_DELAY ? noteData : 0;
        touchDue = true;
        tuneDue = true;
        if (entry.hasBufferNote()) {
            bufferPitch = pitchOf(entry.note() - DmfTrackEntry.BUFFER_OFFSET);
        }
        if (volumeEffect == SET_BALANCE) {
            balance = volumeData;
        }
    }

    void unit(int rowUnit) {
        if (touchDue && unitsSinceEntry >= touchAt) {
            touchDue = false;
            touch(pending);
        }
        if (tuneDue && unitsSinceEntry >= tuneAt) {
            tuneDue = false;
            tune(pending);
        }
        timed(rowUnit);
        if (rowUnit % UNITS_PER_STEP == 0) {
            step();
        }
        unitsSinceEntry++;
        unitsSinceStart++;
    }

    /**
     * Silences a track the pattern being played does not have.
     */
    void cut() {
        voice.silence();
        pending = null;
        resetCarriedEffects();
        touchDue = false;
        tuneDue = false;
        held = false;
    }

    /**
     * Ends whatever the channel carried: the three effects and their data, the shared slide remainders, the
     * span and its steps taken, the scratch, arpeggio, vibrato and tremolo, the balance swing, and the note and
     * volume silencing. A new entry then sets its own effects going over this same clean slate.
     */
    private void resetCarriedEffects() {
        instrumentEffect = NONE;
        instrumentData = 0;
        noteEffect = NONE;
        noteData = 0;
        volumeEffect = NONE;
        volumeData = 0;
        spanSteps = STEPS_PER_ROW;
        stepsTaken = 0;
        Arrays.fill(shared, 0);
        scratchSteps = 0;
        arpeggio = 0;
        vibrato = 0;
        tremolo = 1;
        balanceSwing = 0;
        noteSilenced = false;
        volumeSilenced = false;
    }

    /**
     * The rate the sample is stepped through at; a note off, a note cut or a note tremolo's off time holds it
     * still.
     */
    double frequency() {
        if (voice.sample == null || held || noteSilenced) {
            return 0;
        }
        return voice.sample.c3Frequency() * Math.pow(2, (pitch + finetune + arpeggio + vibrato - C3_PITCH) / OCTAVE);
    }

    int loudness() {
        return volumeSilenced ? 0 : (int) Math.round(volume * tremolo);
    }

    int panning() {
        return Math.clamp(Math.round(balance + balanceSwing), 0, DmfVoice.FULL);
    }

    /**
     * A vibrato or tremolo waveform from -1 to 1 at a phase from 0 to 1: a sine, a triangle rising from 0, or a
     * square high for its first half.
     */
    static double wave(int shape, double phase) {
        return switch (shape) {
            case SINE -> Math.sin(2 * Math.PI * phase);
            case TRIANGLE -> phase < 0.25 ? 4 * phase : phase < 0.75 ? 2 - 4 * phase : 4 * phase - 4;
            default -> phase < 0.5 ? 1 : -1;
        };
    }

    /**
     * What the entry does to the sample: a note with an instrument starts that sample; an instrument alone, a
     * restart or an offset starts the last note again on the sample the channel has; a note alone sounds a held
     * sample again, and a note off holds it. The volume byte is set after the sample's own volume so it wins.
     */
    private void touch(DmfTrackEntry entry) {
        if (entry.hasNote() && entry.hasInstrument()) {
            start(entry.instrument());
        } else if (entry.hasInstrument() || instrumentEffect == RESTART || isOffset()) {
            restart();
        } else if (entry.hasNote()) {
            held = false;
        }
        if (entry.isNoteOff()) {
            held = true;
        }
        if (entry.hasVolume()) {
            volume = entry.volume();
        }
        switch (instrumentEffect) {
            case STOP_SAMPLE -> voice.silence();
            case STOP_LOOP -> voice.released = true;
            case INVERT -> voice.backwards = !voice.backwards;
            default -> {
            }
        }
    }

    private void start(int number) {
        final DmfSample sample = file.sample(number);
        instrument = number;
        held = false;
        if (sample == null) {
            voice.silence();
            return;
        }
        begin(sample);
        if (sample.volume() > 0) {
            volume = sample.volume();
        }
    }

    /**
     * Starts the last note again on the sample the channel already has, at the volume it already has, the way
     * OpenMPT plays an instrument without a note.
     */
    private void restart() {
        if (note > 0 && voice.sample != null) {
            begin(voice.sample);
            held = false;
        }
    }

    private void begin(DmfSample sample) {
        voice.start(sample, isOffset() ? offsetFrame(sample) : 0);
        unitsSinceStart = 0;
    }

    private boolean isOffset() {
        return instrumentEffect >= OFFSET && instrumentEffect <= LAST_OFFSET;
    }

    /**
     * Offsets count blocks of 256 bytes above a base of 0, 64k, 128k or 192k, and a 16-bit sample holds two
     * bytes a frame.
     */
    private int offsetFrame(DmfSample sample) {
        final int bytes = instrumentData * OFFSET_BLOCK + (instrumentEffect - OFFSET) * HIGH_OFFSET_BLOCK;
        return sample.sixteenBit() ? bytes / Short.BYTES : bytes;
    }

    /**
     * A note sets the pitch and clears the last note's finetune; a finetune, signed, holds until the next note.
     */
    private void tune(DmfTrackEntry entry) {
        if (entry.hasNote()) {
            note = entry.note();
            pitch = pitchOf(note);
            finetune = 0;
        }
        if (noteEffect == FINETUNE) {
            finetune = (byte) noteData;
        }
    }

    private void timed(int rowUnit) {
        if (instrumentEffect == RETRIG && instrumentData > 0 && unitsSinceEntry > 0
                && unitsSinceEntry % instrumentData == 0 && voice.sample != null) {
            voice.start(voice.sample, 0);
        }
        switch (noteEffect) {
            case ARPEGGIO -> arpeggio = arpeggioStep(rowUnit);
            case NOTE_TREMOLO -> noteSilenced = offTime(noteData);
            case NOTE_CUT -> {
                if (unitsSinceEntry == noteData) {
                    held = true;
                }
            }
            default -> {
            }
        }
        if (volumeEffect == VOLUME_TREMOLO) {
            volumeSilenced = offTime(volumeData);
        }
    }

    /**
     * The note, then Data1 and then Data2 semitones above it, each for a third of every row.
     */
    private int arpeggioStep(int rowUnit) {
        return switch (rowUnit % ROW * ARPEGGIO_STEPS / ROW) {
            case 0 -> 0;
            case 1 -> high(noteData) * SEMITONE;
            default -> low(noteData) * SEMITONE;
        };
    }

    /**
     * Whether a tremor stands in its off time: on for Data1 and off for Data2 fifteenths of half a row, counted
     * from the entry.
     */
    private boolean offTime(int data) {
        final int on = (int) (high(data) * HALF_ROW / DEPTH_STEPS);
        final int cycle = on + (int) (low(data) * HALF_ROW / DEPTH_STEPS);
        return cycle > 0 && unitsSinceEntry % cycle >= on;
    }

    private void step() {
        switch (noteEffect) {
            case PORTAMENTO_UP -> pitch = Math.min(HIGHEST_PITCH, pitch + share(NOTE_COLUMN, noteData * SIXTEENTH));
            case PORTAMENTO_DOWN -> pitch = Math.max(0, pitch - share(NOTE_COLUMN, noteData * SIXTEENTH));
            case TONE_PORTAMENTO -> slideToBuffer();
            case SCRATCH -> scratch();
            case VIBRATO_SINE, VIBRATO_TRIANGLE, VIBRATO_SQUARE -> vibrato = low(noteData) / DEPTH_STEPS * VIBRATO_RANGE
                    * wave(noteEffect - VIBRATO_SINE, phase(high(noteData)));
            default -> {
            }
        }
        switch (volumeEffect) {
            case VOLUME_UP -> volume = Math.min(DmfVoice.FULL, volume + share(VOLUME_COLUMN, volumeData));
            case VOLUME_DOWN -> volume = Math.max(0, volume - share(VOLUME_COLUMN, volumeData));
            case TREMOLO_SINE, TREMOLO_TRIANGLE, TREMOLO_SQUARE -> tremolo = 1 - low(volumeData) / DEPTH_STEPS
                    * (1 - wave(volumeEffect - TREMOLO_SINE, phase(high(volumeData)))) / 2;
            case BALANCE_LEFT -> balance = Math.max(0, balance - share(VOLUME_COLUMN, volumeData));
            case BALANCE_RIGHT -> balance = Math.min(DmfVoice.FULL, balance + share(VOLUME_COLUMN, volumeData));
            case BALANCE_VIBRATO -> balanceSwing = low(volumeData) / DEPTH_STEPS * DmfVoice.MIDDLE
                    * wave(SINE, phase(high(volumeData)));
            default -> {
            }
        }
        stepsTaken++;
    }

    /**
     * A slide's amount for this step: nothing once the entry's span has taken all its steps, otherwise the part
     * of {@code total} that does not divide carried to the next step, so the whole amount is spent exactly as
     * the span ends.
     */
    private int share(int column, int total) {
        if (stepsTaken >= spanSteps) {
            return 0;
        }
        shared[column] += total;
        final int whole = shared[column] / spanSteps;
        shared[column] %= spanSteps;
        return whole;
    }

    private void slideToBuffer() {
        if (bufferPitch == NONE) {
            return;
        }
        final int step = share(NOTE_COLUMN, noteData * SIXTEENTH);
        pitch = pitch < bufferPitch ? Math.min(bufferPitch, pitch + step) : Math.max(bufferPitch, pitch - step);
    }

    /**
     * Towards note Data1 in whole semitones, as far along as the steps of the entry's span taken so far, so the
     * target is reached as the span ends and held after. Data1 is clamped to the highest note the tracker has,
     * unlike the raw byte OpenMPT stores it as.
     */
    private void scratch() {
        final int target = Math.min(noteData, HIGHEST_NOTE);
        if (scratchSteps == 0) {
            scratchFrom = pitch / SEMITONE;
        }
        scratchSteps = Math.min(spanSteps, scratchSteps + 1);
        pitch = (scratchFrom + (target - scratchFrom) * scratchSteps / spanSteps) * SEMITONE;
    }

    private double phase(int periodRows) {
        final int period = Math.max(1, periodRows) * ROW;
        return unitsSinceStart % period / (double) period;
    }

    private static int pitchOf(int note) {
        return (note - 1) * SEMITONE;
    }

    private static int high(int data) {
        return data >> NIBBLE_BITS;
    }

    private static int low(int data) {
        return data & NIBBLE;
    }
}

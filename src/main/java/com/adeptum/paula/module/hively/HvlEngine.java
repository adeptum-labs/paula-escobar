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
 *
 * The replay follows hvl_replay.c of HivelyTracker, Copyright © 2006-2018
 * Pete Gordon, licensed under the three-clause BSD licence and used here
 * under the GNU General Public License.
 */

package com.adeptum.paula.module.hively;

import java.util.Arrays;
import java.util.OptionalLong;

/**
 * Plays an AHX or HivelyTracker tune. Fifty times a second the sequencer takes a step through the
 * arrangement, hands every channel the note and effects its track holds, and runs a frame of the
 * instrument's envelope, sweeps and performance list. The frame ends with a waveform written into the
 * voice and the period and volume Paula would have been given, which is what the mixer plays.
 */
final class HvlEngine {

    private static final int FIRST_SUBSONG = 0;
    private static final int FRAMES_PER_SECOND = 50;
    private static final int DEFAULT_TEMPO = 6;
    private static final int FULL_VOLUME = 0x40;
    private static final int VOLUME_SHIFT = 6;
    private static final int ENVELOPE_SHIFT = 8;
    /** The transpose a note has not overridden; the tune's own values never reach it. */
    private static final int NO_OVERRIDE = 1000;
    private static final int HIGHEST_NOTE = 5 * 12;
    private static final int LOWEST_PERIOD = 0x0071;
    private static final int HIGHEST_PERIOD = 0x0d60;
    private static final int WAVE_LENGTH = 0x280;
    private static final int LONGEST_WAVE = 5;
    private static final int WAVEFORMS = 4;
    private static final int TRIANGLE = 0;
    private static final int SAWTOOTH = 1;
    private static final int SQUARE = 2;
    private static final int NOISE = 3;
    private static final int MIDDLE_FILTER = 0x20;
    private static final int LOWEST_FILTER = 1;
    private static final int HIGHEST_FILTER = 63;
    private static final int FILTER_STRIDE = 0xfc + 0xfc + 0x80 * 0x1f + 0x80 + 0x280 * 3;
    private static final int SQUARE_STRIDE = 0x80;
    private static final int RING_LOWEST_NOTE = 1;
    private static final int RING_HIGHEST_NOTE = 0x3c;
    private static final int RING_RELATIVE = 0x80;
    private static final int NOISE_STEP = 2239384;
    private static final int NOISE_ADD = 782323;
    private static final int NOISE_XOR = 75;
    private static final int NOISE_SUBTRACT = 6735;
    private static final int WORD = 0xffff;
    private static final int BYTE = 0xff;
    private static final int NIBBLE = 0x0f;
    /** Where each of the six wave lengths starts inside a filtered bank of waveforms. */
    private static final int[] WAVE_OFFSETS = {0x00, 0x04, 0x04 + 0x08, 0x04 + 0x08 + 0x10,
            0x04 + 0x08 + 0x10 + 0x20, 0x04 + 0x08 + 0x10 + 0x20 + 0x40};

    private final HvlTune tune;
    private final int sampleRate;
    private final HvlVoice[] voices = new HvlVoice[HvlTune.MAX_CHANNELS];
    private final byte[][] waveformSource = new byte[WAVEFORMS][];
    private final int[] waveformOffset = new int[WAVEFORMS];

    private int posNr;
    private int noteNr;
    private int posJump;
    private int posJumpNote;
    private int tempo;
    private int stepWaitFrames;
    private int playingTime;
    private boolean patternBreak;
    private boolean getNewPosition;
    private boolean songEndReached;

    HvlEngine(final HvlTune tune, final int sampleRate, final int subsong) {
        this.tune = tune;
        this.sampleRate = sampleRate;
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new HvlVoice();
        }
        waveformSource[TRIANGLE] = HvlTables.WAVES;
        waveformOffset[TRIANGLE] = HvlTables.TRIANGLE_04;
        waveformSource[SAWTOOTH] = HvlTables.WAVES;
        waveformOffset[SAWTOOTH] = HvlTables.SAWTOOTH_04;
        waveformSource[NOISE] = HvlTables.WAVES;
        waveformOffset[NOISE] = HvlTables.WHITE_NOISE;
        initSubsong(subsong);
    }

    int voices() {
        return tune.channels();
    }

    HvlVoice voice(final int index) {
        return voices[index];
    }

    int position() {
        return posNr;
    }

    int row() {
        return noteNr;
    }

    boolean songEnded() {
        return songEndReached;
    }

    /**
     * How many frames the song lasts, counted by stepping the sequencer without mixing any audio; empty for
     * a song that jumps back on itself and never reaches an end.
     */
    static OptionalLong songFrames(final HvlTune tune, final int sampleRate, final long limit) {
        final HvlEngine engine = new HvlEngine(tune, sampleRate, FIRST_SUBSONG);
        final long tickFrames = sampleRate / FRAMES_PER_SECOND / tune.speedMultiplier();
        long frames = 0;

        while (frames < limit) {
            engine.tick();
            frames += tickFrames;
            if (engine.songEnded()) {
                return OptionalLong.of(frames);
            }
        }
        return OptionalLong.empty();
    }

    /**
     * One fiftieth of a second of the sequencer: the next step of the arrangement when the last one has been
     * held long enough, then a frame of every channel and the Paula registers it leaves behind.
     */
    void tick() {
        if (stepWaitFrames == 0) {
            if (getNewPosition) {
                takePosition();
            }
            for (int i = 0; i < tune.channels(); i++) {
                processStep(voices[i]);
            }
            stepWaitFrames = tempo;
        }

        for (int i = 0; i < tune.channels(); i++) {
            processFrame(voices[i]);
        }

        playingTime++;
        stepWaitFrames = stepWaitFrames - 1 & WORD;
        if (stepWaitFrames == 0) {
            advance();
        }

        for (int i = 0; i < tune.channels(); i++) {
            setAudio(voices[i]);
        }
    }

    private void takePosition() {
        final int nextPos = posNr + 1 == tune.positionCount() ? 0 : posNr + 1;
        final HvlPosition position = tune.positions().get(posNr);
        final HvlPosition next = tune.positions().get(nextPos);

        for (int i = 0; i < tune.channels(); i++) {
            voices[i].track = position.track()[i];
            voices[i].transpose = position.transpose()[i];
            voices[i].nextTrack = next.track()[i];
            voices[i].nextTranspose = next.transpose()[i];
        }
        getNewPosition = false;
    }

    private void advance() {
        if (!patternBreak) {
            noteNr++;
            if (noteNr >= tune.trackLength()) {
                posJump = posNr + 1;
                posJumpNote = 0;
                patternBreak = true;
            }
        }

        if (patternBreak) {
            patternBreak = false;
            posNr = (short) posJump;
            noteNr = posJumpNote;
            if (posNr == tune.positionCount()) {
                songEndReached = true;
                posNr = tune.restart();
            }
            posJumpNote = 0;
            posJump = 0;
            getNewPosition = true;
        }
    }

    /**
     * Starts a subsong at the position it begins on, with every channel silent, panned where the tune's
     * stereo separation puts it and waiting for the first step.
     */
    private void initSubsong(final int subsong) {
        posNr = subsong == 0 ? 0 : tune.subsongs()[subsong - 1];
        posJump = 0;
        patternBreak = false;
        noteNr = 0;
        posJumpNote = 0;
        tempo = DEFAULT_TEMPO;
        stepWaitFrames = 0;
        getNewPosition = true;
        songEndReached = false;
        playingTime = 0;

        for (int i = 0; i < HvlTune.MAX_CHANNELS; i += 4) {
            pan(voices[i], tune.defaultPanLeft());
            pan(voices[i + 1], tune.defaultPanRight());
            pan(voices[i + 2], tune.defaultPanRight());
            pan(voices[i + 3], tune.defaultPanLeft());
        }
        reset();
    }

    private static void pan(final HvlVoice voice, final int pan) {
        voice.pan = pan;
        voice.setPan = pan;
        voice.panMultLeft = HvlTables.PANNING_LEFT[pan];
        voice.panMultRight = HvlTables.PANNING_RIGHT[pan];
    }

    private void reset() {
        for (final HvlVoice voice : voices) {
            clear(voice);
        }

        for (int i = 0; i < voices.length; i++) {
            voices[i].noiseRandom = WAVE_LENGTH;
            voices[i].voiceNum = i;
            voices[i].trackMasterVolume = FULL_VOLUME;
            voices[i].trackOn = true;
            voices[i].mixSource = voices[i].voiceBuffer;
            voices[i].mixOffset = 0;
        }
    }

    /**
     * Everything the replayer silences between subsongs; the panning and the instrument a voice holds
     * survive it, as they do in the C.
     */
    private static void clear(final HvlVoice voice) {
        voice.delta = 1;
        voice.overrideTranspose = NO_OVERRIDE;
        voice.samplePos = 0;
        voice.track = voice.nextTrack = voice.transpose = voice.nextTranspose = 0;
        voice.adsrVolume = 0;
        voice.attackFrames = voice.attackVolume = voice.decayFrames = voice.decayVolume = 0;
        voice.sustainFrames = voice.releaseFrames = voice.releaseVolume = 0;
        voice.instrPeriod = voice.trackPeriod = voice.vibratoPeriod = 0;
        voice.noteMaxVolume = voice.perfSubVolume = voice.trackMasterVolume = 0;
        voice.newWaveform = voice.plantSquare = voice.plantPeriod = voice.ignoreSquare = false;
        voice.waveform = 0;
        voice.trackOn = voice.fixedNote = voice.hardCutRelease = false;
        voice.volumeSlideUp = voice.volumeSlideDown = 0;
        voice.hardCutFrames = voice.hardCutReleaseFrames = 0;
        voice.periodSlideOn = voice.periodSlideWithLimit = voice.periodPerfSlideOn = false;
        voice.periodSlideSpeed = voice.periodSlideLimit = voice.periodPerfSlideSpeed = 0;
        voice.periodSlidePeriod = voice.periodPerfSlidePeriod = 0;
        voice.vibratoDelay = voice.vibratoCurrent = voice.vibratoDepth = voice.vibratoSpeed = 0;
        voice.squareOn = voice.squareInit = voice.squareSlidingIn = voice.squareReverse = false;
        voice.squareLowerLimit = voice.squareUpperLimit = voice.squarePos = voice.squareSign = 0;
        voice.filterOn = voice.filterInit = voice.filterSlidingIn = false;
        voice.filterLowerLimit = voice.filterUpperLimit = voice.filterPos = voice.filterSign = 0;
        voice.filterSpeed = voice.ignoreFilter = 0;
        voice.perfCurrent = voice.perfSpeed = voice.waveLength = 0;
        voice.noteDelayOn = voice.noteCutOn = false;
        voice.audioPeriod = voice.audioVolume = voice.voicePeriod = 0;
        voice.voiceVolume = voice.voiceNum = voice.noiseRandom = 0;
        voice.squareWait = voice.filterWait = voice.perfWait = 0;
        voice.noteDelayWait = voice.noteCutWait = 0;
        voice.playlist = null;
        voice.ringPlantPeriod = voice.ringNewWaveform = voice.ringFixedPeriod = false;
        voice.ringSamplePos = voice.ringDelta = voice.ringWaveform = voice.ringBasePeriod = 0;
        voice.ringAudioPeriod = 0;
        voice.ringMixSource = voice.ringAudioSource = null;
        voice.ringMixOffset = voice.ringAudioOffset = 0;

        Arrays.fill(voice.squareTempBuffer, (byte) 0);
        Arrays.fill(voice.voiceBuffer, 0, WAVE_LENGTH + 1, (byte) 0);
        Arrays.fill(voice.ringVoiceBuffer, 0, WAVE_LENGTH + 1, (byte) 0);
    }

    private void processStepFx1(final HvlVoice voice, final int fx, final int fxParam) {
        switch (fx) {
            case 0x0 -> {
                if ((fxParam & NIBBLE) > 0 && (fxParam & NIBBLE) <= 9) {
                    posJump = fxParam & NIBBLE;
                }
            }
            case 0x5, 0xa -> {
                voice.volumeSlideDown = fxParam & NIBBLE;
                voice.volumeSlideUp = fxParam >> 4;
            }
            case 0x7 -> pan(voice, fxParam + 128 & BYTE);
            case 0xb -> {
                posJump = posJump * 100 + (fxParam & NIBBLE) + (fxParam >> 4) * 10 & WORD;
                patternBreak = true;
                if (posJump <= posNr) {
                    songEndReached = true;
                }
            }
            case 0xd -> {
                posJump = posNr + 1;
                posJumpNote = (fxParam & NIBBLE) + (fxParam >> 4) * 10;
                patternBreak = true;
                if (posJumpNote > tune.trackLength()) {
                    posJumpNote = 0;
                }
            }
            case 0xe -> {
                if (fxParam >> 4 == 0xc && (fxParam & NIBBLE) < tempo) {
                    voice.noteCutWait = fxParam & NIBBLE;
                    if (voice.noteCutWait != 0) {
                        voice.noteCutOn = true;
                        voice.hardCutRelease = false;
                    }
                }
            }
            case 0xf -> {
                tempo = fxParam;
                if (fxParam == 0) {
                    songEndReached = true;
                }
            }
            default -> { }
        }
    }

    private int processStepFx2(final HvlVoice voice, final int fx, final int fxParam, final int note) {
        switch (fx) {
            case 0x9 -> {
                voice.squarePos = fxParam >> LONGEST_WAVE - voice.waveLength;
                voice.ignoreSquare = true;
            }
            case 0x3, 0x5 -> {
                if (fx == 0x3 && fxParam != 0) {
                    voice.periodSlideSpeed = fxParam;
                }
                if (note != 0) {
                    final int difference = HvlTables.PERIOD[voice.trackPeriod] - HvlTables.PERIOD[note];
                    if (difference + voice.periodSlidePeriod != 0) {
                        voice.periodSlideLimit = -difference;
                    }
                }
                voice.periodSlideOn = true;
                voice.periodSlideWithLimit = true;
                return 0;
            }
            default -> { }
        }
        return note;
    }

    private void processStepFx3(final HvlVoice voice, final int fx, final int fxParam) {
        switch (fx) {
            case 0x01 -> {
                voice.periodSlideSpeed = -fxParam;
                voice.periodSlideOn = true;
                voice.periodSlideWithLimit = false;
            }
            case 0x02 -> {
                voice.periodSlideSpeed = fxParam;
                voice.periodSlideOn = true;
                voice.periodSlideWithLimit = false;
            }
            case 0x04 -> filterOverride(voice, fxParam);
            case 0x0c -> setVolume(voice, fxParam & BYTE);
            case 0x0e -> extendedStepFx(voice, fxParam);
            default -> { }
        }
    }

    private static void filterOverride(final HvlVoice voice, final int fxParam) {
        if (fxParam == 0 || fxParam == 0x40 || fxParam > 0x7f) {
            return;
        }
        if (fxParam < 0x40) {
            voice.ignoreFilter = fxParam;
        } else {
            voice.filterPos = fxParam - 0x40;
        }
    }

    private void setVolume(final HvlVoice voice, final int fxParam) {
        if (fxParam <= FULL_VOLUME) {
            voice.noteMaxVolume = fxParam;
            return;
        }
        final int master = fxParam - 0x50;
        if (master < 0) {
            return;
        }
        if (master <= FULL_VOLUME) {
            for (int i = 0; i < tune.channels(); i++) {
                voices[i].trackMasterVolume = master;
            }
            return;
        }
        final int track = master - (0xa0 - 0x50);
        if (track >= 0 && track <= FULL_VOLUME) {
            voice.trackMasterVolume = track;
        }
    }

    private void extendedStepFx(final HvlVoice voice, final int fxParam) {
        switch (fxParam >> 4) {
            case 0x1 -> {
                voice.periodSlidePeriod -= fxParam & NIBBLE;
                voice.plantPeriod = true;
            }
            case 0x2 -> {
                voice.periodSlidePeriod += fxParam & NIBBLE;
                voice.plantPeriod = true;
            }
            case 0x4 -> voice.vibratoDepth = fxParam & NIBBLE;
            case 0xa -> voice.noteMaxVolume = Math.min(voice.noteMaxVolume + (fxParam & NIBBLE), FULL_VOLUME);
            case 0xb -> voice.noteMaxVolume = Math.max(voice.noteMaxVolume - (fxParam & NIBBLE), 0);
            case 0xf -> {
                if (tune.version() >= 1 && (fxParam & NIBBLE) == 1) {
                    voice.overrideTranspose = voice.transpose;
                }
            }
            default -> { }
        }
    }

    /**
     * Takes the step the channel's track holds on the row the song has reached and starts the note, the
     * instrument and the effects it names.
     */
    private void processStep(final HvlVoice voice) {
        if (!voice.trackOn) {
            return;
        }
        voice.volumeSlideUp = voice.volumeSlideDown = 0;

        final HvlStep step = tune.tracks()[tune.positions().get(posNr).track()[voice.voiceNum]][noteNr];
        int note = step.note();
        final int instrument = step.instrument();

        if (noteDelayed(voice, step)) {
            return;
        }
        if (note != 0) {
            voice.overrideTranspose = NO_OVERRIDE;
        }

        processStepFx1(voice, step.fx() & NIBBLE, step.fxParam());
        processStepFx1(voice, step.fxb() & NIBBLE, step.fxbParam());

        if (instrument != 0 && instrument < tune.instruments().size()) {
            startInstrument(voice, tune.instruments().get(instrument));
        }

        voice.periodSlideOn = false;

        note = processStepFx2(voice, step.fx() & NIBBLE, step.fxParam(), note);
        note = processStepFx2(voice, step.fxb() & NIBBLE, step.fxbParam(), note);

        if (note != 0) {
            voice.trackPeriod = (short) note;
            voice.plantPeriod = true;
        }

        processStepFx3(voice, step.fx() & NIBBLE, step.fxParam());
        processStepFx3(voice, step.fxb() & NIBBLE, step.fxbParam());
    }

    /**
     * Whether either effect of the step holds the note back for a few frames, in which case the step is
     * taken again once the wait is over.
     */
    private boolean noteDelayed(final HvlVoice voice, final HvlStep step) {
        boolean waitedOut = false;

        if ((step.fx() & NIBBLE) == 0xe && (step.fxParam() & 0xf0) == 0xd0) {
            if (voice.noteDelayOn) {
                voice.noteDelayOn = false;
                waitedOut = true;
            } else if (startNoteDelay(voice, step.fxParam())) {
                return true;
            }
        }

        if (!waitedOut && (step.fxb() & NIBBLE) == 0xe && (step.fxbParam() & 0xf0) == 0xd0) {
            if (voice.noteDelayOn) {
                voice.noteDelayOn = false;
            } else if (startNoteDelay(voice, step.fxbParam())) {
                return true;
            }
        }
        return false;
    }

    private boolean startNoteDelay(final HvlVoice voice, final int fxParam) {
        if ((fxParam & NIBBLE) >= tempo) {
            return false;
        }
        voice.noteDelayWait = fxParam & NIBBLE;
        voice.noteDelayOn = voice.noteDelayWait != 0;
        return voice.noteDelayOn;
    }

    private static void startInstrument(final HvlVoice voice, final HvlInstrument instrument) {
        pan(voice, voice.setPan);

        voice.periodSlideSpeed = voice.periodSlideLimit = 0;
        voice.periodSlidePeriod = 0;
        voice.perfSubVolume = FULL_VOLUME;
        voice.adsrVolume = 0;
        voice.instrument = instrument;
        voice.samplePos = 0;

        final HvlEnvelope adsr = instrument.envelope();
        voice.attackFrames = adsr.attackFrames();
        voice.attackVolume = adsr.attackFrames() != 0
                ? adsr.attackVolume() * 256 / adsr.attackFrames() : adsr.attackVolume() * 256;
        voice.decayFrames = adsr.decayFrames();
        voice.decayVolume = adsr.decayFrames() != 0
                ? (adsr.decayVolume() - adsr.attackVolume()) * 256 / adsr.decayFrames() : adsr.decayVolume() * 256;
        voice.sustainFrames = adsr.sustainFrames();
        voice.releaseFrames = adsr.releaseFrames();
        voice.releaseVolume = adsr.releaseFrames() != 0
                ? (adsr.releaseVolume() - adsr.decayVolume()) * 256 / adsr.releaseFrames() : adsr.releaseVolume() * 256;

        voice.waveLength = instrument.waveLength();
        voice.noteMaxVolume = instrument.volume();

        voice.vibratoCurrent = 0;
        voice.vibratoDelay = instrument.vibratoDelay();
        voice.vibratoDepth = instrument.vibratoDepth();
        voice.vibratoSpeed = instrument.vibratoSpeed();
        voice.vibratoPeriod = 0;

        voice.hardCutRelease = instrument.hardCutRelease();
        voice.hardCutFrames = instrument.hardCutReleaseFrames();

        voice.ignoreSquare = voice.squareSlidingIn = voice.squareOn = false;
        voice.squareWait = 0;

        final int squareLower = instrument.squareLowerLimit() >> LONGEST_WAVE - voice.waveLength;
        final int squareUpper = instrument.squareUpperLimit() >> LONGEST_WAVE - voice.waveLength;
        voice.squareUpperLimit = Math.max(squareLower, squareUpper);
        voice.squareLowerLimit = Math.min(squareLower, squareUpper);

        voice.ignoreFilter = voice.filterWait = 0;
        voice.filterOn = voice.filterSlidingIn = false;

        int speed = instrument.filterSpeed();
        final int filterLower = instrument.filterLowerLimit();
        final int filterUpper = instrument.filterUpperLimit();
        if ((filterLower & 0x80) != 0) {
            speed |= 0x20;
        }
        if ((filterUpper & 0x80) != 0) {
            speed |= 0x40;
        }
        voice.filterSpeed = speed;
        voice.filterUpperLimit = Math.max(filterLower & ~0x80, filterUpper & ~0x80);
        voice.filterLowerLimit = Math.min(filterLower & ~0x80, filterUpper & ~0x80);
        voice.filterPos = 32;

        voice.perfWait = voice.perfCurrent = 0;
        voice.perfSpeed = instrument.playlist().speed();
        voice.playlist = instrument.playlist();

        voice.ringMixSource = null;
        voice.ringSamplePos = 0;
        voice.ringPlantPeriod = voice.ringNewWaveform = false;
    }

    private void playlistCommand(final HvlVoice voice, final int fx, final int fxParam) {
        switch (fx) {
            case 0 -> {
                if (fxParam > 0 && fxParam < 0x40) {
                    voice.filterPos = voice.ignoreFilter != 0 ? voice.ignoreFilter : fxParam;
                    voice.ignoreFilter = 0;
                    voice.newWaveform = true;
                }
            }
            case 1 -> {
                voice.periodPerfSlideSpeed = fxParam;
                voice.periodPerfSlideOn = true;
            }
            case 2 -> {
                voice.periodPerfSlideSpeed = -fxParam;
                voice.periodPerfSlideOn = true;
            }
            case 3 -> {
                if (voice.ignoreSquare) {
                    voice.ignoreSquare = false;
                } else {
                    voice.squarePos = fxParam >> LONGEST_WAVE - voice.waveLength;
                }
            }
            case 4 -> toggleSweeps(voice, fxParam);
            case 5 -> voice.perfCurrent = fxParam;
            case 7 -> ringModulate(voice, fxParam, TRIANGLE);
            case 8 -> ringModulate(voice, fxParam, SAWTOOTH);
            case 9 -> pan(voice, fxParam + 128 & BYTE);
            case 12 -> setPerformanceVolume(voice, fxParam);
            case 15 -> voice.perfSpeed = voice.perfWait = fxParam;
            default -> { }
        }
    }

    private static void toggleSweeps(final HvlVoice voice, final int fxParam) {
        if (fxParam == 0) {
            voice.squareOn = !voice.squareOn;
            voice.squareInit = voice.squareOn;
            voice.squareSign = 1;
            return;
        }
        if ((fxParam & NIBBLE) != 0) {
            voice.squareOn = !voice.squareOn;
            voice.squareInit = voice.squareOn;
            voice.squareSign = (fxParam & NIBBLE) == NIBBLE ? -1 : 1;
        }
        if ((fxParam & 0xf0) != 0) {
            voice.filterOn = !voice.filterOn;
            voice.filterInit = voice.filterOn;
            voice.filterSign = (fxParam & 0xf0) == 0xf0 ? -1 : 1;
        }
    }

    private static void ringModulate(final HvlVoice voice, final int fxParam, final int waveform) {
        if (fxParam >= RING_LOWEST_NOTE && fxParam <= RING_HIGHEST_NOTE) {
            voice.ringBasePeriod = fxParam;
            voice.ringFixedPeriod = true;
        } else if (fxParam >= RING_RELATIVE + RING_LOWEST_NOTE && fxParam <= RING_RELATIVE + RING_HIGHEST_NOTE) {
            voice.ringBasePeriod = fxParam - RING_RELATIVE;
            voice.ringFixedPeriod = false;
        } else {
            voice.ringBasePeriod = 0;
            voice.ringFixedPeriod = false;
            voice.ringNewWaveform = false;
            voice.ringAudioSource = null;
            voice.ringMixSource = null;
            return;
        }
        voice.ringWaveform = waveform;
        voice.ringNewWaveform = true;
        voice.ringPlantPeriod = true;
    }

    private static void setPerformanceVolume(final HvlVoice voice, final int fxParam) {
        if (fxParam <= FULL_VOLUME) {
            voice.noteMaxVolume = fxParam;
            return;
        }
        final int performance = fxParam - 0x50;
        if (performance < 0) {
            return;
        }
        if (performance <= FULL_VOLUME) {
            voice.perfSubVolume = performance;
            return;
        }
        final int track = performance - (0xa0 - 0x50);
        if (track >= 0 && track <= FULL_VOLUME) {
            voice.trackMasterVolume = track;
        }
    }

    /**
     * One frame of a channel: the note cut and the envelope, everything sliding or sweeping, the next entry
     * of the performance list, and finally the waveform, period and volume the frame leaves behind.
     */
    private void processFrame(final HvlVoice voice) {
        if (!voice.trackOn) {
            return;
        }

        if (voice.noteDelayOn) {
            if (voice.noteDelayWait <= 0) {
                processStep(voice);
            } else {
                voice.noteDelayWait--;
            }
        }

        hardCut(voice);
        noteCut(voice);
        runEnvelope(voice);

        voice.noteMaxVolume = Math.clamp(voice.noteMaxVolume + voice.volumeSlideUp - voice.volumeSlideDown,
                0, FULL_VOLUME);

        slidePeriod(voice);
        runVibrato(voice);
        runPlaylist(voice);

        if (voice.periodPerfSlideOn) {
            voice.periodPerfSlidePeriod -= voice.periodPerfSlideSpeed;
            if (voice.periodPerfSlidePeriod != 0) {
                voice.plantPeriod = true;
            }
        }

        sweepSquare(voice);
        sweepFilter(voice);

        if (voice.waveform == SQUARE || voice.plantSquare) {
            buildSquare(voice);
        }
        if (voice.waveform == NOISE) {
            voice.newWaveform = true;
        }
        if (voice.ringNewWaveform) {
            takeRingWaveform(voice);
        }
        if (voice.newWaveform) {
            takeWaveform(voice);
        }
        if (voice.ringAudioSource != null) {
            voice.ringAudioPeriod = period(voice, voice.ringBasePeriod, voice.ringFixedPeriod);
        }

        voice.audioPeriod = period(voice, voice.instrPeriod, voice.fixedNote);
        voice.audioVolume = (short) ((((voice.adsrVolume >> ENVELOPE_SHIFT) * voice.noteMaxVolume
                >> VOLUME_SHIFT) * voice.perfSubVolume >> VOLUME_SHIFT) * voice.trackMasterVolume
                >> VOLUME_SHIFT);
    }

    /**
     * Releases the note early enough that the next one on the track starts from silence.
     */
    private void hardCut(final HvlVoice voice) {
        if (voice.hardCutFrames == 0) {
            return;
        }
        final HvlStep[][] tracks = tune.tracks();
        final int nextInstrument = noteNr + 1 < tune.trackLength()
                ? tracks[voice.track][noteNr + 1].instrument() : tracks[voice.nextTrack][0].instrument();
        if (nextInstrument == 0) {
            return;
        }

        final int wait = Math.max(tempo - voice.hardCutFrames, 0);
        if (voice.noteCutOn) {
            voice.hardCutFrames = 0;
        } else {
            voice.noteCutOn = true;
            voice.noteCutWait = wait;
            voice.hardCutReleaseFrames = -(wait - tempo);
        }
    }

    private static void noteCut(final HvlVoice voice) {
        if (!voice.noteCutOn) {
            return;
        }
        if (voice.noteCutWait > 0) {
            voice.noteCutWait--;
            return;
        }

        voice.noteCutOn = false;
        if (!voice.hardCutRelease) {
            voice.noteMaxVolume = 0;
            return;
        }
        voice.releaseFrames = voice.hardCutReleaseFrames;
        voice.releaseVolume = voice.releaseFrames > 0
                ? -(voice.adsrVolume - (voice.instrument.envelope().releaseVolume() << ENVELOPE_SHIFT))
                        / voice.releaseFrames : 0;
        voice.attackFrames = voice.decayFrames = voice.sustainFrames = 0;
    }

    /**
     * A frame of attack, decay, sustain or release; the frame counters are only ever non-zero once an
     * instrument has been started, so the envelope it names is always there to end the stage on.
     */
    private static void runEnvelope(final HvlVoice voice) {
        if (voice.attackFrames != 0) {
            voice.adsrVolume += voice.attackVolume;
            if (--voice.attackFrames <= 0) {
                voice.adsrVolume = voice.instrument.envelope().attackVolume() << ENVELOPE_SHIFT;
            }
        } else if (voice.decayFrames != 0) {
            voice.adsrVolume += voice.decayVolume;
            if (--voice.decayFrames <= 0) {
                voice.adsrVolume = voice.instrument.envelope().decayVolume() << ENVELOPE_SHIFT;
            }
        } else if (voice.sustainFrames != 0) {
            voice.sustainFrames--;
        } else if (voice.releaseFrames != 0) {
            voice.adsrVolume += voice.releaseVolume;
            if (--voice.releaseFrames <= 0) {
                voice.adsrVolume = voice.instrument.envelope().releaseVolume() << ENVELOPE_SHIFT;
            }
        }
    }

    private static void slidePeriod(final HvlVoice voice) {
        if (!voice.periodSlideOn) {
            return;
        }
        if (!voice.periodSlideWithLimit) {
            voice.periodSlidePeriod += voice.periodSlideSpeed;
            voice.plantPeriod = true;
            return;
        }

        final int distance = voice.periodSlidePeriod - voice.periodSlideLimit;
        final int speed = distance > 0 ? -voice.periodSlideSpeed : voice.periodSlideSpeed;
        if (distance != 0) {
            voice.periodSlidePeriod = (short) (((distance + speed ^ distance) >= 0)
                    ? voice.periodSlidePeriod + speed : voice.periodSlideLimit);
            voice.plantPeriod = true;
        }
    }

    private static void runVibrato(final HvlVoice voice) {
        if (voice.vibratoDepth == 0) {
            return;
        }
        if (voice.vibratoDelay > 0) {
            voice.vibratoDelay--;
            return;
        }
        voice.vibratoPeriod = (short) (HvlTables.VIBRATO[voice.vibratoCurrent] * voice.vibratoDepth >> 7);
        voice.plantPeriod = true;
        voice.vibratoCurrent = voice.vibratoCurrent + voice.vibratoSpeed & 0x3f;
    }

    private void runPlaylist(final HvlVoice voice) {
        if (voice.playlist == null) {
            return;
        }
        if (voice.instrument == null || voice.perfCurrent >= voice.instrument.playlist().entries().size()) {
            if (voice.perfWait != 0) {
                voice.perfWait--;
            } else {
                voice.periodPerfSlideSpeed = 0;
            }
            return;
        }

        final boolean signedOverflow = voice.perfWait == 128;
        voice.perfWait--;
        if (!signedOverflow && (byte) voice.perfWait > 0) {
            return;
        }

        final HvlPlaylistEntry entry = voice.playlist.entries().get(voice.perfCurrent++);
        voice.perfWait = voice.perfSpeed;

        if (entry.waveform() != 0) {
            voice.waveform = entry.waveform() - 1;
            voice.newWaveform = true;
            voice.periodPerfSlideSpeed = 0;
            voice.periodPerfSlidePeriod = 0;
        }
        voice.periodPerfSlideOn = false;

        for (int i = 0; i < entry.fx().length; i++) {
            playlistCommand(voice, entry.fx()[i] & BYTE, entry.fxParam()[i] & BYTE);
        }

        if (entry.note() != 0) {
            voice.instrPeriod = (short) entry.note();
            voice.plantPeriod = true;
            voice.fixedNote = entry.fixed();
        }
    }

    private static void sweepSquare(final HvlVoice voice) {
        if (voice.waveform != SQUARE || !voice.squareOn || --voice.squareWait > 0) {
            return;
        }

        int position = voice.squarePos;
        if (voice.squareInit) {
            voice.squareInit = false;
            if (position <= voice.squareLowerLimit) {
                voice.squareSlidingIn = true;
                voice.squareSign = 1;
            } else if (position >= voice.squareUpperLimit) {
                voice.squareSlidingIn = true;
                voice.squareSign = -1;
            }
        }

        if (position == voice.squareLowerLimit || position == voice.squareUpperLimit) {
            if (voice.squareSlidingIn) {
                voice.squareSlidingIn = false;
            } else {
                voice.squareSign = -voice.squareSign;
            }
        }

        position += voice.squareSign;
        voice.squarePos = position;
        voice.plantSquare = true;
        voice.squareWait = voice.instrument.squareSpeed();
    }

    private static void sweepFilter(final HvlVoice voice) {
        if (!voice.filterOn || --voice.filterWait > 0) {
            return;
        }

        int position = voice.filterPos;
        if (voice.filterInit) {
            voice.filterInit = false;
            if (position <= voice.filterLowerLimit) {
                voice.filterSlidingIn = true;
                voice.filterSign = 1;
            } else if (position >= voice.filterUpperLimit) {
                voice.filterSlidingIn = true;
                voice.filterSign = -1;
            }
        }

        final int steps = voice.filterSpeed < 4 ? 5 - voice.filterSpeed : 1;
        for (int i = 0; i < steps; i++) {
            if (position == voice.filterLowerLimit || position == voice.filterUpperLimit) {
                if (voice.filterSlidingIn) {
                    voice.filterSlidingIn = false;
                } else {
                    voice.filterSign = -voice.filterSign;
                }
            }
            position += voice.filterSign;
        }

        voice.filterPos = Math.clamp(position, LOWEST_FILTER, HIGHEST_FILTER);
        voice.newWaveform = true;
        voice.filterWait = Math.max(voice.filterSpeed - 3, 1);
    }

    /**
     * Cuts one period out of the square bank the filter position picks and leaves it where the waveform
     * table's third entry points, which is where this voice reads its square from.
     */
    private void buildSquare(final HvlVoice voice) {
        int source = HvlTables.SQUARES + (voice.filterPos - MIDDLE_FILTER) * FILTER_STRIDE;
        int position = voice.squarePos << LONGEST_WAVE - voice.waveLength;

        if (position > 0x20) {
            position = 0x40 - position;
            voice.squareReverse = true;
        }
        if (position > 0) {
            source += (position - 1) * SQUARE_STRIDE;
        }

        final int step = 32 >> voice.waveLength;
        waveformSource[SQUARE] = voice.squareTempBuffer;
        waveformOffset[SQUARE] = 0;
        for (int i = 0; i < (1 << voice.waveLength) * 4; i++, source += step) {
            voice.squareTempBuffer[i] = HvlTables.WAVES[source];
        }

        voice.newWaveform = true;
        voice.waveform = SQUARE;
        voice.plantSquare = false;
    }

    private void takeRingWaveform(final HvlVoice voice) {
        voice.ringWaveform = Math.min(voice.ringWaveform, SAWTOOTH);
        voice.ringAudioSource = waveformSource[voice.ringWaveform];
        voice.ringAudioOffset = waveformOffset[voice.ringWaveform] + WAVE_OFFSETS[voice.waveLength];
    }

    private void takeWaveform(final HvlVoice voice) {
        int offset = waveformOffset[voice.waveform];

        if (voice.waveform != SQUARE) {
            offset += (voice.filterPos - MIDDLE_FILTER) * FILTER_STRIDE;
        }
        if (voice.waveform < SQUARE) {
            offset += WAVE_OFFSETS[voice.waveLength];
        }
        if (voice.waveform == NOISE) {
            offset += voice.noiseRandom & 2 * WAVE_LENGTH - 1 & ~1;
            voice.noiseRandom += NOISE_STEP;
            voice.noiseRandom = ((voice.noiseRandom >> 8 | voice.noiseRandom << 24) + NOISE_ADD ^ NOISE_XOR)
                    - NOISE_SUBTRACT;
        }

        voice.audioSource = waveformSource[voice.waveform];
        voice.audioOffset = offset;
    }

    /**
     * The Paula period a note sounds at: the note itself moved by the track's transpose unless it is fixed,
     * then everything sliding and vibrating on top, all of it kept inside what Paula can play.
     */
    private static short period(final HvlVoice voice, final int base, final boolean fixed) {
        int note = base;

        if (!fixed) {
            note += (voice.overrideTranspose != NO_OVERRIDE ? voice.overrideTranspose : voice.transpose)
                    + voice.trackPeriod - 1;
        }
        int period = HvlTables.PERIOD[Math.clamp(note, 0, HIGHEST_NOTE)];
        if (!fixed) {
            period += voice.periodSlidePeriod;
        }
        period = (short) (period + voice.periodPerfSlidePeriod + voice.vibratoPeriod);

        return (short) Math.clamp(period, LOWEST_PERIOD, HIGHEST_PERIOD);
    }

    /**
     * Hands the mixer the period as a 16.16 step through the voice buffer and, when the frame planted a new
     * waveform, the buffer itself filled with as many periods of it as fit.
     */
    private void setAudio(final HvlVoice voice) {
        if (!voice.trackOn) {
            voice.voiceVolume = 0;
            return;
        }
        voice.voiceVolume = voice.audioVolume & BYTE;

        if (voice.plantPeriod) {
            voice.plantPeriod = false;
            voice.voicePeriod = voice.audioPeriod;
            voice.delta = delta(voice.audioPeriod);
        }

        if (voice.newWaveform) {
            if (voice.waveform == NOISE) {
                System.arraycopy(voice.audioSource, voice.audioOffset, voice.voiceBuffer, 0, WAVE_LENGTH);
            } else {
                fill(voice.voiceBuffer, voice.audioSource, voice.audioOffset, voice.waveLength);
            }
            voice.voiceBuffer[WAVE_LENGTH] = voice.voiceBuffer[0];
            voice.mixSource = voice.voiceBuffer;
            voice.mixOffset = 0;
        }

        if (voice.ringPlantPeriod) {
            voice.ringPlantPeriod = false;
            voice.ringDelta = delta(voice.ringAudioPeriod);
        }

        if (voice.ringNewWaveform) {
            fill(voice.ringVoiceBuffer, voice.ringAudioSource, voice.ringAudioOffset, voice.waveLength);
            voice.ringVoiceBuffer[WAVE_LENGTH] = voice.ringVoiceBuffer[0];
            voice.ringMixSource = voice.ringVoiceBuffer;
            voice.ringMixOffset = 0;
        }
    }

    private static void fill(final byte[] buffer, final byte[] source, final int offset, final int waveLength) {
        final int period = 4 << waveLength;
        final int loops = (1 << LONGEST_WAVE - waveLength) * 5;

        for (int i = 0; i < loops; i++) {
            System.arraycopy(source, offset, buffer, i * period, period);
        }
    }

    private int delta(final int period) {
        int delta = (int) (HvlTables.periodToFrequency(period) / sampleRate);

        if (delta > WAVE_LENGTH << 16) {
            delta -= WAVE_LENGTH << 16;
        }
        return delta == 0 ? 1 : delta;
    }
}

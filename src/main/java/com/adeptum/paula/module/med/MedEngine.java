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
 * The replay follows the MED loaders and med_extras.c of libxmp,
 * Copyright © 1996-2026 Claudio Matsuoka and Hipolito Carraro Jr,
 * licensed under the MIT licence and used here under the GNU General
 * Public License.
 */

package com.adeptum.paula.module.med;

import java.util.OptionalLong;

/**
 * Plays an OctaMED module: a sequencer walking the play sequence line by line and tick by tick, the command on
 * every track applied on the way, and a mixdown of all tracks into 16-bit stereo. The song is played once
 * through, as the tracker's own replayer does.
 */
final class MedEngine {

    private static final int FULL_VOLUME = 64;
    private static final int MIDDLE_PANNING = 128;
    private static final int TICKS_PER_MINUTE = 24;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int LOWEST_PERIOD = 113;
    private static final int HIGHEST_PERIOD = 6848;
    private static final int VIBRATO_SHIFT = 10;
    private static final int VIBRATO_STEP_SHIFT = 5;
    private static final int NIBBLE = 0x0F;
    private static final int NIBBLE_BITS = 4;
    private static final int FINETUNES = 8;
    private static final int FINETUNE_EIGHTHS = 3;
    private static final int ARPEGGIO_STEPS = 3;
    private static final int SAMPLE_OFFSET_BITS = 8;

    private final MedFile module;
    private final MedSong song;
    private final int sampleRate;
    private final MedVoice[] voices;

    private int order;
    private int line;
    private int tick;
    private int speed;
    private int tempo;
    private int tickFrames;
    private int patternDelay;
    private int loopLine;
    private int loopsLeft;
    private int arpeggioStep;
    private int nextOrder = -1;
    private int nextLine = -1;
    private boolean ended;

    MedEngine(MedFile module, int sampleRate) {
        this.module = module;
        this.song = module.song();
        this.sampleRate = sampleRate;
        this.voices = new MedVoice[Math.max(1, module.tracks())];
        for (int voice = 0; voice < voices.length; voice++) {
            voices[voice] = new MedVoice();
            voices[voice].trackVolume = trackVolume(voice);
            voices[voice].panning = MIDDLE_PANNING;
        }
        this.speed = Math.max(1, song.tempo2());
        this.tempo = MedEffects.convertTempo(song.tempo(), song);
        measureTick();
    }

    int tracks() {
        return voices.length;
    }

    MedVoice voice(int number) {
        return voices[number];
    }

    boolean hasEnded() {
        return ended;
    }

    /**
     * Moves the sequencer to a line without sounding anything on the way, which is how a seek catches up.
     */
    void position(int atOrder, int atLine) {
        order = atOrder;
        line = atLine;
        tick = 0;
        ended = false;
    }

    int tickFrames() {
        return tickFrames;
    }

    private int trackVolume(int voice) {
        final int[] volumes = song.trackVolumes();
        return voice < volumes.length && volumes[voice] > 0 ? volumes[voice] : FULL_VOLUME;
    }

    /**
     * How many frames one tick lasts. A tempo counts beats a minute, each of which the tracker divides into
     * twenty-four ticks, so the beat length the song sets moves every line with it.
     */
    private void measureTick() {
        final int beats = Math.max(1, tempo) * TICKS_PER_MINUTE;
        final int scaled = song.isBpm() ? beats * song.beatLines() / 4 : beats;
        final int frames = sampleRate * SECONDS_PER_MINUTE;
        tickFrames = frames / Math.max(1, scaled);
    }

    /**
     * Plays one tick of the song: a new line is read at the first tick of each, then every voice is moved on
     * by whatever its command asks for.
     */
    void nextTick() {
        if (tick == 0) {
            if (patternDelay > 0) {
                patternDelay--;
            } else {
                playLine();
            }
        }
        perTick();
        if (++arpeggioStep >= ARPEGGIO_STEPS) {
            arpeggioStep = 0;
        }
        if (++tick >= speed) {
            tick = 0;
            if (patternDelay == 0) {
                advanceLine();
            }
        }
    }

    private void advanceLine() {
        if (nextOrder >= 0) {
            order = nextOrder;
            line = nextLine < 0 ? 0 : nextLine;
            nextOrder = -1;
            nextLine = -1;
            if (order >= song.length()) {
                ended = true;
            }
            return;
        }
        final MedBlock block = blockAt(order);
        if (block == null || ++line >= block.lines()) {
            line = 0;
            if (++order >= song.length()) {
                ended = true;
            }
        }
    }

    private MedBlock blockAt(int position) {
        return position >= 0 && position < song.length() ? module.block(song.playSeq()[position]) : null;
    }

    /**
     * Reads the line every track stands on and does what it asks: a note is started, a command is applied, and
     * a track with nothing written on it carries on as it was.
     */
    private void playLine() {
        final MedBlock block = blockAt(order);
        if (block == null) {
            ended = true;
            return;
        }
        for (int number = 0; number < voices.length; number++) {
            final MedVoice voice = voices[number];
            final MedCommand command = MedEffects.translate(block.entry(line, number), song);
            voice.delayed = null;
            if (command.effect() == MedEffect.NOTE_DELAY) {
                voice.delayTicks = command.parameter();
                voice.delayed = command;
                continue;
            }
            apply(voice, command);
        }
    }

    private void apply(MedVoice voice, MedCommand command) {
        clearPerLine(voice);
        onLine(voice, command);
        if (command.hasNote() && startsNote(command.effect())) {
            trigger(voice, command);
        }
    }

    /**
     * A portamento slides the note sounding towards the one written rather than starting it, and a hold
     * symbol keeps what is already sounding.
     */
    private boolean startsNote(MedEffect effect) {
        return effect != MedEffect.PORTAMENTO && effect != MedEffect.PORTAMENTO_AND_VOLUME_SLIDE;
    }

    private void clearPerLine(MedVoice voice) {
        voice.arpeggio = 0;
        voice.retriggerEvery = 0;
    }

    private void trigger(MedVoice voice, MedCommand command) {
        final MedInstrument playing = instrumentFor(voice, command);
        if (playing == null || playing.isSilent()) {
            voice.silence();
            return;
        }
        voice.note = command.note();
        voice.finetune = playing.finetune();
        voice.hold = playing.hold();
        voice.decay = playing.decay();
        voice.holdLeft = playing.hold();
        voice.held = false;
        voice.start(playing, periodOf(command.note(), playing, voice.finetune), playing.volume());
    }

    private MedInstrument instrumentFor(MedVoice voice, MedCommand command) {
        if (command.instrument() > 0) {
            voice.instrument = command.instrument();
        }
        return module.instrument(voice.instrument);
    }

    /**
     * The period a note sounds at, moved by the instrument's own transpose and pulled off pitch by its
     * finetune, which the format writes as eighths of a semitone either way.
     */
    private int periodOf(int note, MedInstrument playing, int tuning) {
        final int sounded = Math.max(1, note + playing.transpose());
        final int period = MedTables.period(sounded);
        final int tuned = period - period * tuning / (MedTables.NOTES_PER_OCTAVE * FINETUNES * FINETUNE_EIGHTHS);
        return clampPeriod(tuned);
    }

    private static int clampPeriod(int period) {
        return Math.max(LOWEST_PERIOD, Math.min(HIGHEST_PERIOD, period));
    }

    private void onLine(MedVoice voice, MedCommand command) {
        final int parameter = command.parameter();
        switch (command.effect()) {
            case ARPEGGIO -> voice.arpeggio = parameter;
            case PORTAMENTO -> portamento(voice, command);
            case DEEP_VIBRATO -> vibrato(voice, parameter, true);
            case VIBRATO -> vibrato(voice, parameter, false);
            case TREMOLO -> tremolo(voice, parameter);
            case HOLD_AND_DECAY -> holdAndDecay(voice, parameter);
            case SET_VOLUME -> voice.volume = Math.min(FULL_VOLUME, parameter);
            case SET_SPEED -> speed = Math.max(1, parameter);
            case SET_TEMPO -> setTempo(parameter);
            case BREAK -> breakToNextBlock(parameter);
            case POSITION_JUMP -> jumpTo(parameter);
            case FINE_SLIDE_UP -> voice.period = clampPeriod(voice.period - parameter);
            case FINE_SLIDE_DOWN -> voice.period = clampPeriod(voice.period + parameter);
            case FINE_VOLUME_UP -> voice.volume = Math.min(FULL_VOLUME, voice.volume + parameter);
            case FINE_VOLUME_DOWN -> voice.volume = Math.max(0, voice.volume - parameter);
            case FINETUNE -> voice.finetune = parameter - FINETUNES;
            case SAMPLE_OFFSET -> voice.position = (double) parameter * (1 << SAMPLE_OFFSET_BITS);
            case PATTERN_DELAY -> patternDelay = parameter;
            case RETRIGGER -> voice.retriggerEvery = parameter;
            case REVERSE -> voice.position = voice.sample == null ? 0 : voice.sample.length - 1.0;
            case SET_PAN -> voice.panning = Math.min(MedVoice.HARD_RIGHT, parameter);
            case LOOP -> loop(parameter);
            default -> {
            }
        }
    }

    private void portamento(MedVoice voice, MedCommand command) {
        if (command.parameter() > 0) {
            voice.portamentoSpeed = command.parameter();
        }
        if (command.hasNote()) {
            final MedInstrument playing = instrumentFor(voice, command);
            if (playing != null) {
                voice.targetPeriod = periodOf(command.note(), playing, voice.finetune);
            }
        }
    }

    private void vibrato(MedVoice voice, int parameter, boolean deep) {
        final int speedNibble = parameter >> NIBBLE_BITS;
        final int depthNibble = parameter & NIBBLE;
        if (speedNibble > 0) {
            voice.vibratoSpeed = speedNibble;
        }
        if (depthNibble > 0) {
            voice.vibratoDepth = deep ? depthNibble * 2 : depthNibble;
        }
    }

    private void tremolo(MedVoice voice, int parameter) {
        final int speedNibble = parameter >> NIBBLE_BITS;
        final int depthNibble = parameter & NIBBLE;
        if (speedNibble > 0) {
            voice.tremoloSpeed = speedNibble;
        }
        if (depthNibble > 0) {
            voice.tremoloDepth = depthNibble;
        }
    }

    /**
     * The command that holds a note past its line and then fades it: the high nibble counts the lines it is
     * held for and the low one how fast it falls once let go.
     */
    private void holdAndDecay(MedVoice voice, int parameter) {
        voice.hold = parameter >> NIBBLE_BITS;
        voice.decay = parameter & NIBBLE;
        voice.holdLeft = voice.hold;
    }

    private void setTempo(int parameter) {
        tempo = parameter;
        measureTick();
    }

    private void breakToNextBlock(int parameter) {
        nextOrder = order + 1;
        nextLine = parameter;
    }

    private void jumpTo(int parameter) {
        nextOrder = parameter;
        nextLine = 0;
    }

    /**
     * A loop marks the line it starts at, then sends the sequencer back to it as many times as it was asked.
     */
    private void loop(int parameter) {
        if (parameter == 0) {
            loopLine = line;
            return;
        }
        if (loopsLeft == 0) {
            loopsLeft = parameter;
        } else {
            loopsLeft--;
        }
        if (loopsLeft > 0) {
            nextOrder = order;
            nextLine = loopLine;
        }
    }

    private void perTick() {
        for (final MedVoice voice : voices) {
            if (voice.delayed != null && tick == voice.delayTicks) {
                apply(voice, voice.delayed);
                voice.delayed = null;
            }
            if (voice.retriggerEvery > 0 && tick > 0 && tick % voice.retriggerEvery == 0) {
                voice.position = 0;
            }
            slide(voice);
            decay(voice);
        }
    }

    private void slide(MedVoice voice) {
        if (tick == 0) {
            return;
        }
        if (voice.portamentoSpeed > 0 && voice.targetPeriod != voice.period) {
            final int step = voice.period < voice.targetPeriod ? voice.portamentoSpeed : -voice.portamentoSpeed;
            voice.period = clampPeriod(step > 0 ? Math.min(voice.period + step, voice.targetPeriod)
                    : Math.max(voice.period + step, voice.targetPeriod));
        }
    }

    /**
     * A note that was held counts its lines down and then falls by its decay every line until it is silent.
     */
    private void decay(MedVoice voice) {
        if (tick != 0 || voice.hold == 0) {
            return;
        }
        if (voice.holdLeft > 0) {
            voice.holdLeft--;
            return;
        }
        if (voice.decay > 0) {
            voice.volume = Math.max(0, voice.volume - voice.decay);
        }
    }

    /**
     * The period the voice is heard at this tick, which the vibrato swings around and the arpeggio steps
     * between three notes of.
     */
    int soundingPeriod(MedVoice voice) {
        int period = voice.period;
        if (voice.vibratoDepth > 0) {
            period += MedTables.vibrato(voice.vibratoStep >> VIBRATO_STEP_SHIFT) * voice.vibratoDepth
                    >> VIBRATO_SHIFT;
            voice.vibratoStep = voice.vibratoStep + voice.vibratoSpeed
                    & (MedTables.VIBRATO_STEPS << VIBRATO_STEP_SHIFT) - 1;
        }
        if (voice.arpeggio > 0) {
            final int step = arpeggioStep == 1 ? voice.arpeggio >> NIBBLE_BITS
                    : arpeggioStep == 2 ? voice.arpeggio & NIBBLE : 0;
            if (step > 0) {
                period = MedTables.period(voice.note + step);
            }
        }
        return clampPeriod(period);
    }

    int soundingVolume(MedVoice voice) {
        int volume = voice.volume;
        if (voice.tremoloDepth > 0) {
            volume += MedTables.vibrato(voice.tremoloStep >> VIBRATO_STEP_SHIFT) * voice.tremoloDepth
                    >> VIBRATO_SHIFT;
            voice.tremoloStep = voice.tremoloStep + voice.tremoloSpeed
                    & (MedTables.VIBRATO_STEPS << VIBRATO_STEP_SHIFT) - 1;
        }
        return Math.max(0, Math.min(FULL_VOLUME, volume));
    }
}

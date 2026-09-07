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

/**
 * Runs the two sequences of a synthetic instrument, one stepping the volume and one swapping the waveform
 * under the note, each at its own speed and each able to wait, jump, slide and hand control to the other.
 * This is what makes a MED instrument that carries no sample sound at all.
 */
final class MedSynth {

    private static final int END = 0xFF;
    private static final int JUMP = 0xFE;
    private static final int ARPEGGIO_END = 0xFD;
    private static final int ARPEGGIO_BEGIN = 0xFC;
    private static final int HALT = 0xFB;
    private static final int JUMP_OTHER = 0xFA;
    private static final int SET_VIBRATO_WAVEFORM = 0xF7;
    private static final int RESET_PITCH = 0xF6;
    private static final int LOOPING_ENVELOPE = 0xF5;
    private static final int SET_VIBRATO_SPEED = 0xF5;
    private static final int ONE_SHOT_ENVELOPE = 0xF4;
    private static final int SET_VIBRATO_DEPTH = 0xF4;
    private static final int CHANGE_UP = 0xF3;
    private static final int CHANGE_DOWN = 0xF2;
    private static final int WAIT = 0xF1;
    private static final int SET_SPEED = 0xF0;

    private static final int ENVELOPE_LENGTH = 0x80;
    private static final int ENVELOPE_SHIFT = 2;
    private static final int ENVELOPE_BIAS = 0x80;
    private static final int VIBRATO_SHIFT = 10;
    private static final int VIBRATO_SUB_STEPS = 5;
    private static final int NOTHING = 0;

    private MedSynth() {
    }

    /**
     * Starts both sequences at their heads with the speeds the instrument was written at, which is what a new
     * note on a new instrument does.
     */
    static void start(MedVoice voice, MedSynthInstrument synth, int period) {
        voice.synth = synth;
        voice.basePeriod = period;
        voice.synthVolume = MedVoice.FULL_VOLUME;
        voice.volumePointer = 0;
        voice.volumeCounter = 0;
        voice.volumeWait = 0;
        voice.volumeSlide = 0;
        voice.volumeSpeed = synth.volumeSpeed();
        voice.wavePointer = 0;
        voice.waveCounter = 0;
        voice.waveWait = 0;
        voice.waveSlide = 0;
        voice.waveSpeed = synth.waveformSpeed();
        voice.arpeggioStart = 0;
        voice.arpeggioIndex = 0;
        voice.envelopeWave = MedVoice.NO_ENVELOPE;
        voice.envelopeIndex = 0;
        voice.envelopeLoops = false;
        voice.synthVibratoDepth = 0;
        voice.synthVibratoSpeed = 0;
        voice.synthVibratoStep = 0;
    }

    /**
     * One tick of both sequences. Either may end by handing its position to the other, which is done last so
     * that both have run before the jump takes effect.
     */
    static void tick(MedVoice voice) {
        final MedSynthInstrument synth = voice.synth;
        if (synth == null) {
            return;
        }
        int jumpWaveform = NOTHING;
        int jumpVolume = NOTHING;
        if (voice.volumeSpeed > 0 && voice.volumeCounter-- == 0) {
            voice.volumeCounter = voice.volumeSpeed - 1;
            jumpWaveform = volumeStep(voice, synth);
            envelope(voice, synth);
            voice.synthVolume = Math.clamp(voice.synthVolume + voice.volumeSlide, 0, MedVoice.FULL_VOLUME);
        }
        if (voice.waveSpeed > 0 && voice.waveCounter-- == 0) {
            voice.waveCounter = voice.waveSpeed - 1;
            jumpVolume = waveformStep(voice, synth);
            voice.period += voice.waveSlide;
        }
        if (jumpWaveform != NOTHING) {
            voice.wavePointer = jumpWaveform;
        }
        if (jumpVolume != NOTHING) {
            voice.volumePointer = jumpVolume;
        }
    }

    private static int volumeStep(MedVoice voice, MedSynthInstrument synth) {
        if (voice.volumeWait > 0) {
            voice.volumeWait--;
            return NOTHING;
        }
        boolean jumped = false;
        while (true) {
            final int command = read(synth.volumeTable(), voice.volumePointer++);
            switch (command) {
                case END, HALT -> voice.volumePointer--;
                case JUMP -> {
                    if (jumped) {
                        return NOTHING;
                    }
                    voice.volumePointer = read(synth.volumeTable(), voice.volumePointer);
                    jumped = true;
                    continue;
                }
                case JUMP_OTHER -> {
                    return read(synth.volumeTable(), voice.volumePointer++);
                }
                case LOOPING_ENVELOPE -> {
                    voice.envelopeWave = read(synth.volumeTable(), voice.volumePointer++);
                    voice.envelopeLoops = true;
                }
                case ONE_SHOT_ENVELOPE -> {
                    voice.envelopeWave = read(synth.volumeTable(), voice.volumePointer++);
                    voice.envelopeLoops = false;
                }
                case CHANGE_UP -> voice.volumeSlide = read(synth.volumeTable(), voice.volumePointer++);
                case CHANGE_DOWN -> voice.volumeSlide = -read(synth.volumeTable(), voice.volumePointer++);
                case WAIT -> voice.volumeWait = read(synth.volumeTable(), voice.volumePointer++);
                case SET_SPEED -> voice.volumeSpeed = read(synth.volumeTable(), voice.volumePointer++);
                default -> {
                    if (command <= MedVoice.FULL_VOLUME) {
                        voice.synthVolume = command;
                    }
                }
            }
            return NOTHING;
        }
    }

    private static int waveformStep(MedVoice voice, MedSynthInstrument synth) {
        if (voice.waveWait > 0) {
            voice.waveWait--;
            return NOTHING;
        }
        boolean jumped = false;
        while (true) {
            final int command = read(synth.waveformTable(), voice.wavePointer++);
            switch (command) {
                case END, HALT -> voice.wavePointer--;
                case JUMP -> {
                    final int target = read(synth.waveformTable(), voice.wavePointer);
                    if (jumped || target == END) {
                        voice.wavePointer--;
                        return NOTHING;
                    }
                    voice.wavePointer = target;
                    jumped = true;
                    continue;
                }
                case ARPEGGIO_END -> {
                }
                case ARPEGGIO_BEGIN -> beginArpeggio(voice, synth);
                case JUMP_OTHER -> {
                    return read(synth.waveformTable(), voice.wavePointer++);
                }
                case SET_VIBRATO_WAVEFORM -> voice.wavePointer++;
                case RESET_PITCH -> voice.period = voice.basePeriod;
                case SET_VIBRATO_SPEED -> voice.synthVibratoSpeed = read(synth.waveformTable(), voice.wavePointer++);
                case SET_VIBRATO_DEPTH -> voice.synthVibratoDepth = read(synth.waveformTable(), voice.wavePointer++);
                case CHANGE_UP -> voice.waveSlide = -read(synth.waveformTable(), voice.wavePointer++);
                case CHANGE_DOWN -> voice.waveSlide = read(synth.waveformTable(), voice.wavePointer++);
                case WAIT -> voice.waveWait = read(synth.waveformTable(), voice.wavePointer++);
                case SET_SPEED -> voice.waveSpeed = read(synth.waveformTable(), voice.wavePointer++);
                default -> swapWaveform(voice, synth, command);
            }
            return NOTHING;
        }
    }

    /**
     * The arpeggio is the run of notes between its opening command and the end of it, which the sequence then
     * steps past.
     */
    private static void beginArpeggio(MedVoice voice, MedSynthInstrument synth) {
        voice.arpeggioStart = voice.wavePointer;
        voice.arpeggioIndex = voice.wavePointer;
        int command = NOTHING;
        while (command != ARPEGGIO_END && command != END) {
            command = read(synth.waveformTable(), voice.wavePointer++);
        }
    }

    private static void swapWaveform(MedVoice voice, MedSynthInstrument synth, int number) {
        final short[] waveform = synth.waveform(number);
        if (waveform != null && waveform.length > 0 && waveform != voice.sample) {
            voice.sample = waveform;
            voice.loopStart = 0;
            voice.loopLength = waveform.length;
            voice.sounding = true;
        }
    }

    /**
     * An envelope is one of the waveforms read as a shape for the volume rather than a sound, a hundred and
     * twenty-eight steps long, looping only where the sequence asked it to.
     */
    private static void envelope(MedVoice voice, MedSynthInstrument synth) {
        final short[] shape = synth.waveform(voice.envelopeWave);
        if (voice.envelopeWave < 0 || shape == null || shape.length != ENVELOPE_LENGTH) {
            return;
        }
        voice.synthVolume = ((shape[voice.envelopeIndex] >> Byte.SIZE) + ENVELOPE_BIAS) >> ENVELOPE_SHIFT;
        if (++voice.envelopeIndex >= ENVELOPE_LENGTH) {
            voice.envelopeIndex = 0;
            if (!voice.envelopeLoops) {
                voice.envelopeWave = MedVoice.NO_ENVELOPE;
            }
        }
    }

    /**
     * How far the instrument's own vibrato has swung, which is a sine whatever waveform the sequence named;
     * libxmp approximates it the same way, and no module has been found that hears the difference.
     */
    static int vibrato(MedVoice voice) {
        if (voice.synthVibratoDepth == 0) {
            return 0;
        }
        final int swing = MedTables.signedSine(voice.synthVibratoStep >> VIBRATO_SUB_STEPS)
                * voice.synthVibratoDepth >> VIBRATO_SHIFT;
        voice.synthVibratoStep = voice.synthVibratoStep + voice.synthVibratoSpeed
                & (MedTables.VIBRATO_STEPS << VIBRATO_SUB_STEPS) - 1;
        return swing;
    }

    /**
     * The note the arpeggio has reached, counted in semitones above the one written, or nothing where the
     * instrument defines no arpeggio.
     */
    static int arpeggio(MedVoice voice) {
        final MedSynthInstrument synth = voice.synth;
        if (synth == null || voice.arpeggioStart == 0) {
            return 0;
        }
        if (read(synth.waveformTable(), voice.arpeggioStart) == ARPEGGIO_END) {
            return 0;
        }
        int step = read(synth.waveformTable(), voice.arpeggioIndex);
        if (step == ARPEGGIO_END) {
            voice.arpeggioIndex = voice.arpeggioStart;
            step = read(synth.waveformTable(), voice.arpeggioIndex);
        }
        voice.arpeggioIndex++;
        return step == ARPEGGIO_END || step == END ? 0 : step;
    }

    private static int read(byte[] table, int at) {
        return at >= 0 && at < table.length ? table[at] & 0xFF : END;
    }
}

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
 * What the fields of an MO3 instrument and sample are taken to mean follows
 * Load_mo3.cpp of OpenMPT, Copyright © 2004-2026 the OpenMPT project
 * developers and Copyright © 1997-2003 Olivier Lapicque, licensed under the
 * three-clause BSD licence and used here under the GNU General Public
 * License.
 */

package com.adeptum.paula.module.mo3;

import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.instrument.Envelope;
import de.quippy.javamod.multimedia.mod.loader.instrument.Instrument;
import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;

/**
 * Fills in the instruments and samples of a module from what an MO3 says about them.
 *
 * <p>The five trackers tune their samples differently: Impulse Tracker and Scream Tracker name a frequency in
 * hertz, and the rest a finetune and a transpose. Fast Tracker keeps its vibrato on the instrument rather than
 * the sample, so there it is handed down to every sample the instrument plays.</p>
 */
final class Mo3Instruments {

    /**
     * The vibrato shapes as Impulse Tracker numbered them, in the order the mixer expects.
     */
    private static final int[] VIBRATO_SHAPES = {0, 3, 1, 4, 2, 0, 0, 0};

    private static final int LOUDEST = 64;
    private static final int MIDDLE_PANNING = 128;
    private static final int SHALLOWEST_FREQUENCY = 256;
    private static final int FINETUNES_PER_SEMITONE = 16;
    private static final int FINEST_AMIGA_TUNE = -8;
    private static final int COARSEST_AMIGA_TUNE = 7;
    private static final int MIDDLE_FINETUNE = 128;
    private static final int SHAPES = 7;

    /**
     * The frequency of a sample is written as a step on a scale of a hundred and twenty-eight to the octave
     * where this many steps below the middle is the frequency Impulse Tracker calls the middle C.
     */
    private static final double FREQUENCY_STEPS_PER_OCTAVE = 1536.0;
    private static final int FREQUENCY_MIDDLE = 1408;

    /**
     * A sample the format leaves unnamed reaches past the last it could number, and counting on from there
     * comes back to none at all.
     */
    private static final int UNSET_SAMPLE = 0xFFFF;

    private static final int PITCH_ENVELOPE_SHIFT = 5;
    private static final int LOUDEST_ENVELOPE = 64;
    private static final int LOUDEST_INSTRUMENT = 128;
    private static final int PANNING_SWING_STEPS = 4;
    private static final int LOUDEST_SWING = 100;

    private Mo3Instruments() {
    }

    static void read(InstrumentsContainer container, Mo3File file, boolean amigaLike) {
        final boolean hertz = file.version() >= Mo3Reader.NEWEST_LAYOUT_FROM
                || !file.song().has(Mo3Song.LINEAR_SLIDES);
        for (int index = 0; index < file.samples().size(); index++) {
            container.setSample(index, sample(file.samples().get(index), file.kind(), amigaLike, hertz));
        }
        if (!file.hasInstruments()) {
            return;
        }
        for (int index = 0; index < file.instruments().size(); index++) {
            container.setInstrument(index, instrument(file.instruments().get(index), file.kind()));
        }
        if (file.kind() == Mo3Kind.FAST_TRACKER) {
            handVibratoDownToTheSamples(container, file);
        }
    }

    private static Sample sample(Mo3Sample mo3, Mo3Kind kind, boolean amigaLike, boolean hertz) {
        final Sample sample = new Sample();
        sample.name = mo3.name();
        sample.dosFileName = mo3.fileName();
        sample.ITPingPongCorrection = 1;

        sample.sampleLength = sample.byteLength = mo3.length();
        sample.loopStart = mo3.loopStart();
        sample.loopStop = mo3.loopEnd();
        sample.loopLength = mo3.loopEnd() - mo3.loopStart();
        sample.sustainLoopStart = mo3.sustainStart();
        sample.sustainLoopStop = mo3.sustainEnd();
        sample.sustainLoopLength = mo3.sustainEnd() - mo3.sustainStart();
        sample.loopType = loopType(mo3);

        sample.volume = Math.min(mo3.volume(), LOUDEST);
        sample.globalVolume = kind == Mo3Kind.IMPULSE_TRACKER
                ? Math.min(mo3.globalVolume(), LOUDEST) : ModConstants.MAXSAMPLEVOLUME;
        sample.setPanning = mo3.panning() <= Mo3Sample.LARGEST_PANNING;
        sample.defaultPanning = sample.setPanning ? mo3.panning() : MIDDLE_PANNING;

        vibrato(sample, mo3.vibrato());
        sample.isStereo = mo3.has(Mo3Sample.STEREO);
        sample.sampleType = ModConstants.SM_PCMS
                | (mo3.has(Mo3Sample.SIXTEEN_BIT) ? ModConstants.SM_16BIT : 0)
                | (mo3.has(Mo3Sample.STEREO) ? ModConstants.SM_STEREO : 0);

        tune(sample, mo3, kind, amigaLike, hertz);
        return sample;
    }

    private static int loopType(Mo3Sample mo3) {
        int loop = 0;
        if (mo3.has(Mo3Sample.LOOP)) {
            loop |= ModConstants.LOOP_ON;
        }
        if (mo3.has(Mo3Sample.PING_PONG_LOOP)) {
            loop |= ModConstants.LOOP_IS_PINGPONG;
        }
        if (mo3.has(Mo3Sample.SUSTAIN)) {
            loop |= ModConstants.LOOP_SUSTAIN_ON;
        }
        if (mo3.has(Mo3Sample.PING_PONG_SUSTAIN)) {
            loop |= ModConstants.LOOP_SUSTAIN_IS_PINGPONG;
        }
        return loop;
    }

    private static void vibrato(Sample sample, Mo3Vibrato mo3) {
        sample.vibratoType = VIBRATO_SHAPES[mo3.type() & SHAPES];
        sample.vibratoSweep = mo3.sweep();
        sample.vibratoDepth = mo3.depth();
        sample.vibratoRate = mo3.rate();
    }

    /**
     * Impulse Tracker and Scream Tracker name the frequency of the middle C outright, either in hertz or as a
     * step on a scale of its own; ProTracker, Fast Tracker and MultiTracker tune by finetune and transpose,
     * and ProTracker in the sixteenths of a semitone the Amiga had.
     *
     * <p>Only the Scream Tracker mixer sounds a sample at the frequency named here, so for Fast Tracker and
     * MultiTracker it is the frequency a middle C is written at and nothing is played by it.</p>
     */
    private static void tune(Sample sample, Mo3Sample mo3, Mo3Kind kind, boolean amigaLike, boolean hertz) {
        if (kind.isScreamTrackerFamily()) {
            final int frequency = hertz ? mo3.frequency() : (int) (ModConstants.BASEFREQUENCY
                    * Math.pow(2, (mo3.frequency() + FREQUENCY_MIDDLE) / FREQUENCY_STEPS_PER_OCTAVE));
            sample.baseFrequency = frequency == 0 ? ModConstants.BASEFREQUENCY
                    : Math.max(frequency, SHALLOWEST_FREQUENCY);
            return;
        }

        final int finetune = kind == Mo3Kind.MULTITRACKER
                ? (byte) mo3.frequency() : (byte) (mo3.frequency() - MIDDLE_FINETUNE);
        sample.transpose = mo3.transpose();
        if (kind == Mo3Kind.PROTRACKER) {
            final int amiga = Math.clamp(finetune / FINETUNES_PER_SEMITONE, FINEST_AMIGA_TUNE, COARSEST_AMIGA_TUNE);
            sample.fineTune = amigaLike ? amiga : amiga * FINETUNES_PER_SEMITONE;
            sample.baseFrequency = ModConstants.IT_fineTuneTable[amiga - FINEST_AMIGA_TUNE];
        } else {
            sample.fineTune = finetune;
            sample.baseFrequency = ModConstants.BASEFREQUENCY;
        }
    }

    private static Instrument instrument(Mo3Instrument mo3, Mo3Kind kind) {
        final Instrument instrument = new Instrument();
        instrument.name = mo3.name();
        instrument.dosFileName = mo3.fileName();
        keyMap(instrument, mo3, kind);

        instrument.volumeEnvelope = envelope(mo3.volume(), Envelope.EnvelopeType.volume, kind, 0);
        instrument.panningEnvelope = envelope(mo3.panning(), Envelope.EnvelopeType.panning, kind, 0);
        instrument.pitchEnvelope = kind == Mo3Kind.FAST_TRACKER
                ? null : envelope(mo3.pitch(), Envelope.EnvelopeType.pitch, kind, PITCH_ENVELOPE_SHIFT);

        if (kind != Mo3Kind.FAST_TRACKER || namesASample(instrument)) {
            instrument.volumeFadeOut = mo3.fadeOut();
        }
        instrument.globalVolume = LOUDEST_INSTRUMENT;
        instrument.setPanning = mo3.panningValue() <= Mo3Instrument.LARGEST_PANNING;
        instrument.defaultPanning = instrument.setPanning ? mo3.panningValue() : MIDDLE_PANNING;
        instrument.mute = mo3.has(Mo3Instrument.MUTE);
        if (kind == Mo3Kind.IMPULSE_TRACKER) {
            impulseTracker(instrument, mo3);
        }

        midi(instrument, mo3, kind);
        return instrument;
    }

    /**
     * An instrument that names no sample at all is one Fast Tracker wrote a header for and nothing else, and
     * its player leaves such an instrument at its own defaults rather than reading what is not there.
     */
    private static boolean namesASample(Instrument instrument) {
        for (final int sample : instrument.sampleIndex) {
            if (sample != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The settings only Impulse Tracker keeps: what happens when a note is cut short or another lands on top
     * of it, how far the sound may wander from one note to the next, and where its filter opens.
     */
    private static void impulseTracker(Instrument instrument, Mo3Instrument mo3) {
        instrument.globalVolume = Math.min(mo3.globalVolume(), LOUDEST_INSTRUMENT);
        instrument.NNA = mo3.newNoteAction();
        instrument.pitchPanSeparation = mo3.pitchPanSeparation();
        instrument.pitchPanCenter = mo3.pitchPanCentre();
        instrument.dublicateNoteCheck = mo3.duplicateCheck();
        instrument.dublicateNoteAction = mo3.duplicateAction();
        instrument.randomVolumeVariation = Math.min(mo3.volumeSwing(), LOUDEST_SWING);
        instrument.randomPanningVariation = Math.min(mo3.panningSwing(), Mo3Instrument.LARGEST_PANNING)
                / PANNING_SWING_STEPS;
        instrument.initialFilterCutoff = mo3.cutoff();
        instrument.initialFilterResonance = mo3.resonance();
    }

    /**
     * Fast Tracker plays one octave up and has no note map of its own, so there only the samples are read and
     * the keys they answer to start an octave above the first.
     */
    private static void keyMap(Instrument instrument, Mo3Instrument mo3, Mo3Kind kind) {
        final int keys = kind == Mo3Kind.FAST_TRACKER ? Mo3Instrument.FAST_TRACKER_KEYS : Mo3Instrument.KEYS;
        instrument.sampleIndex = new int[keys];
        instrument.noteIndex = new int[keys];
        for (int key = 0; key < keys; key++) {
            instrument.sampleIndex[key] = mo3.sampleFor()[key] + 1 & UNSET_SAMPLE;
            instrument.noteIndex[key] = kind == Mo3Kind.FAST_TRACKER ? key : mo3.noteFor()[key];
        }
    }

    private static void midi(Instrument instrument, Mo3Instrument mo3, Mo3Kind kind) {
        final Mo3Instrument.Mo3Midi mo3Midi = mo3.midi();
        if (mo3Midi.channel() >= Mo3Instrument.Mo3Midi.FIRST_PLUGIN_CHANNEL) {
            instrument.mixPlugIn = mo3Midi.channel() - Mo3Instrument.Mo3Midi.FIRST_PLUGIN_CHANNEL + 1;
            return;
        }
        final boolean plays = kind == Mo3Kind.FAST_TRACKER
                ? mo3.has(Mo3Instrument.PLAY_ON_MIDI) : mo3Midi.channel() > 0;
        if (!plays || mo3Midi.channel() > Mo3Instrument.Mo3Midi.CHANNELS) {
            return;
        }
        instrument.midiChannel = mo3Midi.channel();
        instrument.midiBank = mo3Midi.bank();
        instrument.midiProgram = mo3Midi.patch();
        instrument.pitchWheelDepth = mo3Midi.bend();
    }

    private static Envelope envelope(Mo3Envelope mo3, Envelope.EnvelopeType type, Mo3Kind kind, int shift) {
        final Envelope envelope = new Envelope(type);
        envelope.on = mo3.has(Mo3Envelope.ENABLED);
        envelope.sustain = mo3.has(Mo3Envelope.SUSTAIN);
        envelope.loop = mo3.has(Mo3Envelope.LOOP);
        envelope.carry = mo3.has(Mo3Envelope.CARRY);
        envelope.filter = mo3.has(Mo3Envelope.FILTER);
        envelope.xm_style = !kind.isScreamTrackerFamily();

        envelope.setNumberOfPoints(mo3.nodes());
        envelope.loopStartPoint = mo3.loopStart();
        envelope.loopEndPoint = mo3.loopEnd();
        envelope.sustainStartPoint = mo3.sustainStart();
        envelope.sustainEndPoint = envelope.xm_style ? mo3.sustainStart() : mo3.sustainEnd();

        envelope.positions = mo3.ticks().clone();
        envelope.value = new int[mo3.values().length];
        for (int point = 0; point < envelope.value.length; point++) {
            envelope.value[point] = Math.clamp(mo3.values()[point] >> shift, 0, LOUDEST_ENVELOPE);
        }
        envelope.sanitize(LOUDEST_ENVELOPE);
        return envelope;
    }

    private static void handVibratoDownToTheSamples(InstrumentsContainer container, Mo3File file) {
        for (int index = 0; index < file.instruments().size(); index++) {
            final Mo3Vibrato mo3 = file.instruments().get(index).vibrato();
            for (final int key : container.getInstrument(index).sampleIndex) {
                final Sample sample = key > 0 ? container.getSample(key - 1) : null;
                if (sample != null) {
                    vibrato(sample, mo3);
                }
            }
        }
    }
}

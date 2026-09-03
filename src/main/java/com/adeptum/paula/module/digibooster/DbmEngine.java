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
 * The replay follows player.c of libdigibooster3, Copyright © 2014 Grzegorz
 * Kraszewski, licensed under the two-clause BSD licence and used here under
 * the GNU General Public License.
 */

package com.adeptum.paula.module.digibooster;

import java.util.Arrays;
import java.util.OptionalLong;

/**
 * Plays a DigiBooster module: a sequencer stepping the playlist row by row and tick by tick, the effects of
 * every track applied on the way, and a mixdown of all tracks into 16-bit stereo. The song is played once
 * through, which is what the tracker's own replayer does.
 */
final class DbmEngine {

    private static final int STEREO = 2;
    private static final int DEFAULT_SPEED = 6;
    private static final int DEFAULT_TEMPO = 125;
    private static final int FULL_VOLUME = 64;
    private static final int TICK_SECONDS_NUMERATOR = 5;
    private static final int GAIN_BITS = 14;
    private static final int GAIN_UNIT = 1 << GAIN_BITS;
    private static final int VOLUME_BITS = 8;
    private static final int PANNING_BITS = 7;
    private static final int VOLUME_ENVELOPE_SCALE = 8;
    private static final int PANNING_ENVELOPE_SCALE = 7;
    private static final int VOLUME_SHIFT = 6;
    private static final int LOWEST_PITCH = 96;
    private static final int HIGHEST_PITCH = 864;
    private static final int SEMITONES = 12;
    private static final int FINETUNES_PER_SEMITONE_BITS = 3;
    private static final int MIDDLE_PANNING = 128;
    private static final int SAMPLE_OFFSET_BITS = 8;
    private static final int COARSE_OFFSET_BITS = 16;
    private static final int VIBRATO_BITS = 8;
    private static final int BACKWARDS_TRIGGER = 0x7FFF;
    private static final int DELAY = 0;
    private static final int FEEDBACK = 1;
    private static final int MIX = 2;
    private static final int CROSS = 3;
    private static final int LONGEST_PATTERN_DELAY = 15;
    private static final int MUTE_TRACK = 0x40;
    private static final int OCTAVE_SHIFT = 19;
    private static final int SMOOTH_PORTA = 0xF0;
    private static final int NIBBLE = 0xF;
    private static final int WORD = 0xFFFF;
    private static final int NONE = -1;
    private static final int CLIP_HIGH = 0x7FFF;
    private static final int CLIP_LOW = 0x8001;
    private static final int MAX_LEVEL = 0x7FFFFFFF;

    private final DbmFile module;
    private final int sampleRate;
    private final DbmTrack[] tracks;
    private final int boostMultiplier;
    private final int boostLimit;

    private int song;
    private int order;
    private int pattern;
    private int row;
    private int tick;
    private int speed = DEFAULT_SPEED;
    private int tempo = DEFAULT_TEMPO;
    private int tickFramesWhole;
    private int tickFramesFraction;
    private int patternDelay;
    private boolean songEnded;
    private int delayedBreak = NONE;
    private int delayedJump = NONE;
    private int delayedLoop = NONE;
    private int loopCounter;
    private int loopOrder;
    private int loopRow;
    private short globalVolume = FULL_VOLUME;
    private short globalVolumeSlide;
    private int lastGlobalVolumeSlide;
    private int arpeggioStep;
    private short minVolume;
    private short maxVolume;
    private short minPanning;
    private short maxPanning;
    private short minPitch;
    private short maxPitch;
    private short[] preMix = new short[0];
    private int[] accumulator = new int[0];

    DbmEngine(DbmFile module, int sampleRate) {
        this.module = module;
        this.sampleRate = sampleRate;
        this.tracks = new DbmTrack[module.tracks()];
        final int[] shifts = DbmPanoramizer.shifts(sampleRate);
        for (int track = 0; track < tracks.length; track++) {
            tracks[track] = new DbmTrack(shifts);
        }
        this.boostMultiplier = (int) ((long) DbmTables.UNIT * DbmTables.DECIBELS[0] / tracks.length >> Short.SIZE);
        this.boostLimit = boostMultiplier == 0 ? MAX_LEVEL : MAX_LEVEL / boostMultiplier;
        reset();
    }

    int tracks() {
        return tracks.length;
    }

    /**
     * How many frames the song lasts, counted by stepping the sequencer without mixing any audio; empty for a
     * song that jumps back on itself and never reaches an end.
     */
    static OptionalLong songFrames(DbmFile module, int sampleRate, long limit) {
        final DbmEngine engine = new DbmEngine(module, sampleRate);
        long frames = 0;
        while (frames < limit) {
            final boolean ended = engine.nextTick();
            frames += engine.tickFramesWhole;
            if (ended) {
                return OptionalLong.of(frames);
            }
        }
        return OptionalLong.empty();
    }

    int order() {
        return order;
    }

    int row() {
        return row;
    }

    DbmTrack track(int number) {
        return tracks[number];
    }

    void position(int song, int order, int row) {
        this.song = song;
        this.order = order;
        this.pattern = module.songs().get(song).playList()[order];
        this.row = row;
        this.tick = 0;
    }

    /**
     * Mixes the next frames and says how many of them the song still had music for; a shorter answer than
     * asked means the song ended inside the buffer.
     */
    int mix(short[] out, int frames) {
        room(frames);
        Arrays.fill(accumulator, 0, frames * STEREO, 0);
        int mixed = 0;
        int left = frames;
        boolean stop = false;
        while (!stop && left > 0) {
            if (tickFramesWhole == 0) {
                stop = nextTick();
            }
            final int chunk = Math.min(tickFramesWhole, left);
            for (final DbmTrack track : tracks) {
                mixIn(track, mixed * STEREO, chunk);
            }
            left -= chunk;
            tickFramesWhole -= chunk;
            mixed += chunk;
        }
        flush(out, frames);
        return mixed;
    }

    private void room(int frames) {
        if (preMix.length < frames * STEREO) {
            preMix = new short[frames * STEREO];
            accumulator = new int[frames * STEREO];
        }
    }

    private void mixIn(DbmTrack track, int at, int frames) {
        if (!track.playing) {
            return;
        }
        track.playing = track.pull(preMix, 0, frames);
        if (track.muted) {
            return;
        }
        for (int frame = 0; frame < frames * STEREO; frame += STEREO) {
            accumulator[at + frame] += preMix[frame] * track.gainLeft >> GAIN_BITS;
            accumulator[at + frame + 1] += preMix[frame + 1] * track.gainRight >> GAIN_BITS;
        }
    }

    private void flush(short[] out, int frames) {
        for (int slot = 0; slot < frames * STEREO; slot++) {
            final int level = accumulator[slot];
            out[slot] = level > boostLimit ? (short) CLIP_HIGH
                    : level < -boostLimit ? (short) CLIP_LOW : (short) (level * boostMultiplier >> Short.SIZE);
        }
    }

    private void reset() {
        tick = 0;
        speed = DEFAULT_SPEED;
        tempo = DEFAULT_TEMPO;
        tickFramesWhole = 0;
        tickFramesFraction = 0;
        patternDelay = 0;
        globalVolume = FULL_VOLUME;
        globalVolumeSlide = 0;
        arpeggioStep = 0;
        minVolume = 0;
        minPanning = -(DEFAULT_SPEED << PANNING_BITS);
        minPitch = DEFAULT_SPEED * LOWEST_PITCH;
        maxVolume = DEFAULT_SPEED << VOLUME_SHIFT;
        maxPanning = DEFAULT_SPEED << PANNING_BITS;
        maxPitch = DEFAULT_SPEED * HIGHEST_PITCH;
        resetDelayed();
        resetLoop();
        initialiseTracks();
        position(0, 0, 0);
    }

    private void resetDelayed() {
        delayedBreak = NONE;
        delayedJump = NONE;
        delayedLoop = NONE;
        songEnded = false;
    }

    private void resetLoop() {
        loopCounter = 0;
        loopOrder = 0;
        loopRow = 0;
    }

    private void initialiseTracks() {
        for (int number = 0; number < tracks.length; number++) {
            final DbmTrack track = tracks[number];
            track.echoDelay = module.echo().delay();
            track.echoFeedback = module.echo().feedback();
            track.echoMix = module.echo().mix();
            track.echoCross = module.echo().cross();
            if (module.echo().tracks()[number]) {
                track.echoOn(DbmEchoUnit.GLOBAL, sampleRate);
            }
        }
    }

    /**
     * Plays one tick of the song: slides are advanced, a new row is fetched at the first tick of a position,
     * notes are triggered and every track gets its gain and pitch for the frames that follow.
     */
    private boolean nextTick() {
        boolean stop = false;
        postTick();
        if (tick == 0) {
            if (patternDelay > 0) {
                stop = patternDelay-- > LONGEST_PATTERN_DELAY;
            } else {
                applyDelayed();
                clearSlides();
                scanForSpeed();
                setupSlides();
                nextRow();
            }
        }
        doTriggers();
        doEnvelopes();
        tickGainsAndPitch();
        if (++arpeggioStep > 2) {
            arpeggioStep = 0;
        }
        final int beats = tempo * 2;
        final int frames = TICK_SECONDS_NUMERATOR * sampleRate;
        tickFramesWhole = frames / beats;
        tickFramesFraction += frames % beats;
        if (tickFramesFraction > beats) {
            tickFramesFraction -= beats;
            tickFramesWhole++;
        }
        if (++tick == speed) {
            tick = 0;
        }
        return stop;
    }

    private void postTick() {
        for (final DbmTrack track : tracks) {
            track.volume = Math.clamp(track.volume + track.volumeDelta, minVolume, maxVolume);
            track.panning = Math.clamp(track.panning + track.panningDelta, minPanning, maxPanning);
            if (track.portaDelta != 0) {
                final int target = (short) (track.portaTarget * speed);
                track.pitch += track.portaDelta;
                track.pitch = track.portaDelta > 0 ? Math.min(track.pitch, target) : Math.max(track.pitch, target);
            }
            track.pitch = Math.clamp(track.pitch + track.pitchDelta, minPitch, maxPitch);
        }
        globalVolume = (short) Math.clamp(globalVolume + globalVolumeSlide, 0, FULL_VOLUME);
    }

    private void applyDelayed() {
        final DbmSong playing = module.songs().get(song);
        if (delayedJump != NONE) {
            order = delayedJump < playing.playList().length ? delayedJump : 0;
            pattern = playing.playList()[order];
            row = 0;
        }
        if (delayedBreak != NONE) {
            if (delayedJump == NONE && row > 0 && ++order >= playing.playList().length) {
                order = 0;
            }
            pattern = playing.playList()[order];
            row = Math.min(delayedBreak, module.patterns().get(pattern).rows() - 1);
        }
        if (delayedLoop != NONE) {
            order = loopOrder;
            pattern = playing.playList()[order];
            row = loopRow;
        }
        if (songEnded) {
            patternDelay = DbmTrack.NEVER;
        }
        resetDelayed();
    }

    private void clearSlides() {
        for (final DbmTrack track : tracks) {
            track.volumeDelta = 0;
            track.panningDelta = 0;
            track.pitchDelta = 0;
            track.portaDelta = 0;
            track.volume /= speed;
            track.panning /= speed;
            track.pitch /= speed;
            track.arpeggio[1] = 0;
            track.arpeggio[2] = 0;
            track.vibratoSpeed = 0;
            track.vibratoDepth = 0;
        }
        globalVolumeSlide = 0;
        arpeggioStep = 0;
    }

    private void setupSlides() {
        for (final DbmTrack track : tracks) {
            track.volume *= speed;
            track.panning *= speed;
            track.pitch *= speed;
        }
        minVolume = 0;
        minPanning = (short) -(speed << PANNING_BITS);
        minPitch = (short) (speed * LOWEST_PITCH);
        maxVolume = (short) (speed << VOLUME_SHIFT);
        maxPanning = (short) (speed << PANNING_BITS);
        maxPitch = (short) (speed * HIGHEST_PITCH);
    }

    /**
     * The speed and tempo of a row are read before the row is played, since they say how long its ticks are.
     */
    private void scanForSpeed() {
        final DbmPattern playing = module.patterns().get(pattern);
        for (int track = 0; track < tracks.length; track++) {
            final DbmEntry entry = playing.entry(row, track, tracks.length);
            speedOrTempo(entry.command1(), entry.parameter1());
            speedOrTempo(entry.command2(), entry.parameter2());
        }
    }

    private void speedOrTempo(int command, int parameter) {
        if (command != 0x0F) {
            return;
        }
        if (parameter == 0) {
            patternDelay = DbmTrack.NEVER;
        } else if (parameter < 0x20) {
            speed = parameter;
        } else {
            tempo = parameter;
        }
    }

    private void nextRow() {
        final DbmPattern playing = module.patterns().get(pattern);
        for (int number = 0; number < tracks.length; number++) {
            playRow(tracks[number], playing.entry(row, number, tracks.length));
        }
        if (++row >= playing.rows()) {
            nextPattern();
        }
    }

    /**
     * A note alone retriggers the instrument the track already holds; an instrument alone only resets the
     * volume; the two together change the instrument. A portamento to note keeps the note from being played.
     */
    private void playRow(DbmTrack track, DbmEntry entry) {
        track.triggerCounter = DbmTrack.NEVER;
        track.cutCounter = DbmTrack.NEVER;
        track.retrigger = 0;
        track.backwards = false;
        track.triggerOffset = 0;
        final boolean portamento = isPortamento(entry);
        if (entry.instrument() != 0 && entry.octave() != 0 && !portamento) {
            setInstrument(track, entry.instrument());
        }
        if (entry.octave() != 0) {
            if (entry.note() < SEMITONES) {
                final short note = (short) ((entry.octave() * SEMITONES + entry.note()) << FINETUNES_PER_SEMITONE_BITS);
                if (portamento) {
                    track.portaTarget = note;
                } else {
                    track.pitch = note * speed;
                    track.triggerCounter = 0;
                }
            } else {
                keyOff(track);
            }
        }
        if (entry.instrument() != 0) {
            defaultVolume(track);
        }
        if (entry.command1() != 0 || entry.parameter1() != 0) {
            effect(track, entry.command1(), entry.parameter1());
        }
        if (entry.command2() != 0 || entry.parameter2() != 0) {
            effect(track, entry.command2(), entry.parameter2());
        }
    }

    private static boolean isPortamento(DbmEntry entry) {
        return entry.command1() == 3 || entry.command1() == 5 || entry.command2() == 3 || entry.command2() == 5;
    }

    /**
     * The reference replayer clears the panning envelope here instead of reading it, so a note off on a track
     * without a volume envelope takes the panning envelope with it and stops the track.
     */
    private static void keyOff(DbmTrack track) {
        if (!track.volumeEnvelope.playing()) {
            track.panningEnvelope.use(DbmEnvelope.NONE);
            track.playing = false;
        }
        if (track.volumeEnvelope.playing()) {
            track.volumeEnvelope.release();
        }
        if (track.panningEnvelope.playing()) {
            track.panningEnvelope.release();
        }
    }

    private void nextPattern() {
        final DbmSong playing = module.songs().get(song);
        if (++order >= playing.playList().length) {
            order = 0;
            songEnded = true;
        }
        pattern = playing.playList()[order];
        row = 0;
    }

    private void setInstrument(DbmTrack track, int number) {
        track.silence();
        if (number > module.instruments().size()) {
            return;
        }
        final DbmInstrument instrument = module.instruments().get(number - 1);
        track.volumeEnvelope.use(instrument.volumeEnvelope());
        track.panningEnvelope.use(instrument.panningEnvelope());
        final short[] sample = module.sampleFor(instrument);
        if (sample.length == 0) {
            return;
        }
        track.sound(sample, instrument.loopStart(), instrument.loopLength(), instrument.loopType());
        track.instrument = number;
    }

    private void trigger(DbmTrack track) {
        track.volumeEnvelopeValue = GAIN_UNIT;
        track.panningEnvelopeValue = 0;
        track.volumeEnvelope.rewind();
        if (track.volumeEnvelope.playing()) {
            track.volumeEnvelope.trigger(module.volumeEnvelopes().get(track.volumeEnvelope.envelope()));
        }
        track.panningEnvelope.rewind();
        if (track.panningEnvelope.playing()) {
            track.panningEnvelope.trigger(module.panningEnvelopes().get(track.panningEnvelope.envelope()));
        }
        if (!track.sounding()) {
            return;
        }
        track.panoramizer.flush();
        track.wavetable.reverse(track.backwards);
        track.wavetable.offset(track.triggerOffset);
        track.vibratoCounter = 0;
        track.triggerOffset = 0;
        track.playing = true;
    }

    private void defaultVolume(DbmTrack track) {
        if (track.instrument == 0) {
            return;
        }
        final DbmInstrument instrument = module.instruments().get(track.instrument - 1);
        track.volume = instrument.volume() * speed;
        track.panning = instrument.panning() * speed;
        track.volumeEnvelope.rewind();
        track.panningEnvelope.rewind();
    }

    private void doTriggers() {
        for (final DbmTrack track : tracks) {
            if (track.instrument == 0) {
                continue;
            }
            if (track.triggerCounter == 0) {
                trigger(track);
                track.triggerCounter = track.retrigger;
            }
            track.triggerCounter--;
            if (track.cutCounter-- <= 0) {
                track.volume = 0;
            }
        }
    }

    private void doEnvelopes() {
        for (final DbmTrack track : tracks) {
            if (track.playing && track.volumeEnvelope.playing()) {
                track.volumeEnvelopeValue = track.volumeEnvelope.next(
                        module.volumeEnvelopes().get(track.volumeEnvelope.envelope()), VOLUME_ENVELOPE_SCALE);
            }
            if (track.playing && track.panningEnvelope.playing()) {
                track.panningEnvelopeValue = track.panningEnvelope.next(
                        module.panningEnvelopes().get(track.panningEnvelope.envelope()), PANNING_ENVELOPE_SCALE);
            }
        }
    }

    /**
     * Turns what the effects left on each track into the pitch its instrument plays at and the gain each side
     * of the stereo image gets, the panning envelope narrowing as the track moves out to one side.
     */
    private void tickGainsAndPitch() {
        for (final DbmTrack track : tracks) {
            int pitch = track.pitch + track.arpeggio[arpeggioStep];
            pitch += DbmTables.VIBRATO[track.vibratoCounter] * track.vibratoDepth >> VIBRATO_BITS;
            track.vibratoCounter = (short) ((track.vibratoCounter + track.vibratoSpeed) & DbmTables.VIBRATO_MASK);
            pitch(track, pitch & WORD);

            int volume = (track.volume << VOLUME_BITS) / speed;
            int panning = (track.panning << PANNING_BITS) / speed;
            volume = volume * track.volumeEnvelopeValue >> GAIN_BITS;
            volume = volume * globalVolume >> VOLUME_SHIFT;
            panning += (GAIN_UNIT - Math.abs(panning)) * track.panningEnvelopeValue >> GAIN_BITS;
            track.gainLeft = (short) (panning > 0 ? volume * (GAIN_UNIT - panning) >> GAIN_BITS : volume);
            track.gainRight = (short) (panning < 0 ? volume * (GAIN_UNIT + panning) >> GAIN_BITS : volume);
            if (track.sounding()) {
                track.panoramizer.panning(track.panning / speed);
            }
        }
    }

    /**
     * The pitch is a finetune, an eighth of a semitone, scaled by the speed so slides can move it smoothly;
     * the fraction left over picks the finer step within the tick.
     */
    private void pitch(DbmTrack track, int pitch) {
        if (track.instrument == 0 || !track.sounding()) {
            return;
        }
        final DbmInstrument instrument = module.instruments().get(track.instrument - 1);
        final int step = DbmTables.SMOOTH_PORTA[speed][pitch % speed];
        int finetune = (pitch / speed - LOWEST_PITCH) & WORD;
        int octave = 0;
        while (finetune >= DbmTables.FINETUNES_PER_OCTAVE) {
            finetune -= DbmTables.FINETUNES_PER_OCTAVE;
            octave++;
        }
        final long samples = (long) instrument.c3Frequency() * DbmTables.MUSIC_SCALE[finetune] * step;
        track.resampler.ratio((int) ((samples >> (OCTAVE_SHIFT - octave)) / sampleRate));
    }

    private void effect(DbmTrack track, int command, int parameter) {
        switch (command) {
            case 0x0 -> arpeggio(track, parameter);
            case 0x1 -> track.pitchDelta = (short) (track.pitchDelta + portamento(reused(track, DbmTrack.PORTA_UP, parameter)));
            case 0x2 -> track.pitchDelta = (short) (track.pitchDelta - portamento(reused(track, DbmTrack.PORTA_DOWN, parameter)));
            case 0x3 -> portaToNote(track, reused(track, DbmTrack.PORTA_SPEED, parameter));
            case 0x4 -> vibrato(track, reused(track, DbmTrack.VIBRATO, parameter));
            case 0x5 -> portaWithVolumeSlide(track, reused(track, DbmTrack.PORTA_VOLUME_SLIDE, parameter));
            case 0x6 -> vibratoWithVolumeSlide(track, reused(track, DbmTrack.VIBRATO_VOLUME_SLIDE, parameter));
            case 0x8 -> track.panning = (parameter - MIDDLE_PANNING) * speed;
            case 0x9 -> track.triggerOffset += parameter << SAMPLE_OFFSET_BITS;
            case 0xA -> volumeSlide(track, reused(track, DbmTrack.VOLUME_SLIDE, parameter));
            case 0xB -> delayedJump = parameter;
            case 0xC -> instrumentVolume(track, parameter);
            case 0xD -> delayedBreak = binaryCodedDecimal(parameter);
            case 0xE -> extended(track, parameter);
            case 0x10 -> globalVolume = parameter <= FULL_VOLUME ? (short) parameter : globalVolume;
            case 0x11 -> globalVolumeSlide(parameter);
            case 0x19 -> panningSlide(track, reused(track, DbmTrack.PANNING_SLIDE, parameter));
            case 0x1F -> echoSwitch(track, parameter);
            case 0x20 -> echo(track, parameter, DELAY);
            case 0x21 -> echo(track, parameter, FEEDBACK);
            case 0x22 -> echo(track, parameter, MIX);
            case 0x23 -> echo(track, parameter, CROSS);
            default -> { }
        }
    }

    private void echo(DbmTrack track, int parameter, int setting) {
        switch (setting) {
            case DELAY -> track.echoDelay = parameter;
            case FEEDBACK -> track.echoFeedback = parameter;
            case MIX -> track.echoMix = parameter;
            default -> track.echoCross = parameter;
        }
        changeEchoParameters(track);
    }

    private void arpeggio(DbmTrack track, int parameter) {
        track.arpeggio[1] = (short) (((parameter >> 4) << 3) * speed);
        track.arpeggio[2] = (short) (((parameter & NIBBLE) << 3) * speed);
    }

    private int portamento(int parameter) {
        return parameter < SMOOTH_PORTA ? parameter * speed : parameter & NIBBLE;
    }

    private void portaToNote(DbmTrack track, int parameter) {
        final short target = (short) (track.portaTarget * speed);
        track.portaDelta = (short) (track.portaDelta + (target >= track.pitch ? parameter * speed : -parameter * speed));
    }

    private void vibrato(DbmTrack track, int parameter) {
        track.vibratoSpeed = (short) (parameter >> 4);
        track.vibratoDepth = (short) ((parameter & NIBBLE) * speed);
    }

    private void portaWithVolumeSlide(DbmTrack track, int parameter) {
        final short target = (short) (track.portaTarget * speed);
        final int portaSpeed = track.remembered[DbmTrack.PORTA_SPEED];
        track.portaDelta = (short) (track.portaDelta + (target >= track.pitch ? portaSpeed * speed : -portaSpeed * speed));
        volumeSlide(track, parameter);
    }

    private void vibratoWithVolumeSlide(DbmTrack track, int parameter) {
        final int remembered = track.remembered[DbmTrack.VIBRATO];
        track.vibratoSpeed = (short) (remembered >> 4);
        track.vibratoDepth = (short) ((remembered & NIBBLE) * speed);
        volumeSlide(track, parameter);
    }

    /**
     * A slide with both halves set is a fine one, moving by the nibble against the F rather than by a step a
     * tick.
     */
    private void volumeSlide(DbmTrack track, int parameter) {
        track.volumeDelta = (short) (track.volumeDelta + slide(parameter));
    }

    private void panningSlide(DbmTrack track, int parameter) {
        final int up = parameter >> 4;
        final int down = parameter & NIBBLE;
        if (up == 0 || down == 0) {
            track.panningDelta = (short) (track.panningDelta + (up - down) * speed);
        }
    }

    private int slide(int parameter) {
        final int up = parameter >> 4;
        final int down = parameter & NIBBLE;
        if (up == 0 || down == 0) {
            return (up - down) * speed;
        }
        if (down == NIBBLE) {
            return up;
        }
        return up == NIBBLE ? -down : 0;
    }

    private void instrumentVolume(DbmTrack track, int parameter) {
        if (parameter <= FULL_VOLUME) {
            track.volume = parameter * speed;
        }
    }

    private void globalVolumeSlide(int parameter) {
        final int used = parameter == 0 ? lastGlobalVolumeSlide : parameter;
        lastGlobalVolumeSlide = parameter == 0 ? lastGlobalVolumeSlide : parameter;
        final int up = used >> 4;
        final int down = used & NIBBLE;
        if (up == 0 || down == 0) {
            globalVolumeSlide = (short) (globalVolumeSlide + up - down);
        }
    }

    private void extended(DbmTrack track, int parameter) {
        final int value = parameter & NIBBLE;
        switch (parameter >> 4) {
            case 0x1 -> track.pitch = Math.min(track.pitch + value * speed, maxPitch);
            case 0x2 -> track.pitch = Math.max(track.pitch - value * speed, minPitch);
            case 0x3 -> track.backwards = track.triggerCounter != BACKWARDS_TRIGGER;
            case 0x4 -> track.playing = track.playing && parameter != MUTE_TRACK;
            case 0x6 -> patternLoop(value);
            case 0x7 -> track.triggerOffset += value << COARSE_OFFSET_BITS;
            case 0x8 -> track.panning = ((value << 4) - MIDDLE_PANNING) * speed;
            case 0x9 -> track.retrigger = value;
            case 0xA -> track.volume = Math.min(track.volume + value * speed, maxVolume);
            case 0xB -> track.volume = Math.max(track.volume - value * speed, minVolume);
            case 0xC -> track.cutCounter = value;
            case 0xD -> track.triggerCounter = value;
            case 0xE -> patternDelay = value;
            default -> { }
        }
    }

    private void patternLoop(int times) {
        if (times == 0) {
            if (loopCounter == 0) {
                loopOrder = order;
                loopRow = row;
            }
            return;
        }
        if (loopCounter == 0) {
            loopCounter = times;
            delayedLoop = 0;
        } else if (--loopCounter > 0) {
            delayedLoop = 0;
        } else {
            resetLoop();
        }
    }

    private void echoSwitch(DbmTrack track, int parameter) {
        final int which = parameter >> 4;
        final boolean on = (parameter & NIBBLE) == 0;
        if (!on && (parameter & NIBBLE) != 1) {
            return;
        }
        switch (which) {
            case 0 -> echoForTrack(track, DbmEchoUnit.GLOBAL, on);
            case 1 -> echoForAllTracks(on);
            case 2 -> echoForTrack(track, DbmEchoUnit.PER_TRACK, on);
            default -> { }
        }
    }

    private void echoForTrack(DbmTrack track, int type, boolean on) {
        if (on) {
            track.echoOn(type, sampleRate);
        } else {
            track.echoOff(type);
        }
    }

    private void echoForAllTracks(boolean on) {
        for (final DbmTrack track : tracks) {
            echoForTrack(track, DbmEchoUnit.GLOBAL, on);
        }
    }

    /**
     * Echo settings are per track once a track runs its own echo, and shared by every track still on the
     * module's echo otherwise.
     */
    private void changeEchoParameters(DbmTrack changed) {
        if (changed.echoType() == DbmEchoUnit.PER_TRACK) {
            changed.echoParameters();
            return;
        }
        for (final DbmTrack track : tracks) {
            if (track.echoType() == DbmEchoUnit.GLOBAL) {
                track.echoDelay = changed.echoDelay;
                track.echoFeedback = changed.echoFeedback;
                track.echoMix = changed.echoMix;
                track.echoCross = changed.echoCross;
                track.echoParameters();
            }
        }
    }

    /**
     * An effect given no parameter plays the last one it was given on that track.
     */
    private static int reused(DbmTrack track, int effect, int parameter) {
        if (parameter != 0) {
            track.remembered[effect] = parameter;
        }
        return track.remembered[effect];
    }

    private static int binaryCodedDecimal(int digits) {
        final int tens = digits >> 4;
        final int units = digits & NIBBLE;
        return tens < 10 && units < 10 ? tens * 10 + units : 0;
    }
}

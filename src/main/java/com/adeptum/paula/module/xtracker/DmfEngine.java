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
import java.util.OptionalLong;

/**
 * Plays an X-Tracker module: a sequencer walking the order list row by row and unit by unit, each unit a 256th of
 * a row as long as the tempo makes it, and a mixdown of every track into 16-bit stereo. The song is played once
 * through.
 */
final class DmfEngine {

    private static final int STEREO = 2;

    private final DmfFile file;
    private final int sampleRate;
    private final DmfTempo tempo;
    private final DmfChannel[] channels;
    private int order;
    private int row;
    private int unit;
    private int unitsInRow;
    private int unitFrames;
    private int unitFramesLeft;
    private int[] accumulator = new int[0];
    private boolean ended;

    DmfEngine(DmfFile file, int sampleRate) {
        this.file = file;
        this.sampleRate = sampleRate;
        this.tempo = new DmfTempo(sampleRate);
        this.channels = new DmfChannel[file.tracks()];
        for (int track = 0; track < channels.length; track++) {
            channels[track] = new DmfChannel(file);
        }
    }

    DmfChannel channel(int track) {
        return channels[track];
    }

    boolean hasEnded() {
        return ended;
    }

    int order() {
        return order;
    }

    int row() {
        return row;
    }

    int unitFrames() {
        return unitFrames;
    }

    /**
     * Mixes the next frames and says how many of them the song still had music for; a shorter answer than asked
     * means the song ended inside the buffer.
     */
    int mix(short[] out, int frames) {
        room(frames);
        Arrays.fill(accumulator, 0, frames * STEREO, 0);
        int mixed = 0;
        while (!ended && mixed < frames) {
            if (unitFramesLeft == 0) {
                nextUnit();
                unitFramesLeft = unitFrames;
                continue;
            }
            final int chunk = Math.min(unitFramesLeft, frames - mixed);
            for (final DmfChannel channel : channels) {
                mixIn(channel, mixed * STEREO, chunk);
            }
            unitFramesLeft -= chunk;
            mixed += chunk;
        }
        flush(out, frames);
        return mixed;
    }

    /**
     * How many frames the song lasts, played through without mixing anything, or nothing at all where it runs
     * past the length a caller is willing to wait for.
     */
    static OptionalLong songFrames(DmfFile file, int sampleRate, long limit) {
        final DmfEngine engine = new DmfEngine(file, sampleRate);
        long frames = 0;
        while (frames < limit) {
            engine.nextUnit();
            if (engine.ended) {
                return OptionalLong.of(frames);
            }
            frames += engine.unitFrames;
        }
        return OptionalLong.empty();
    }

    /**
     * Plays one unit: the row is read on its first, then every track is moved on by what it carries.
     */
    void nextUnit() {
        if (ended) {
            return;
        }
        if (unit == 0 && !startRow()) {
            ended = true;
            return;
        }
        for (final DmfChannel channel : channels) {
            channel.unit();
        }
        unitFrames = tempo.nextUnitFrames();
        if (++unit >= unitsInRow) {
            unit = 0;
            advanceRow();
        }
    }

    /**
     * Reads the row every track stands on, or says the order list has run out. A pattern with fewer tracks than
     * the song silences the rest as it starts, as OpenMPT does.
     */
    private boolean startRow() {
        final DmfPattern pattern = file.patternAt(order);
        if (pattern == null) {
            return false;
        }
        if (row == 0) {
            tempo.patternStarts(pattern.beat());
            for (int track = pattern.tracks(); track < channels.length; track++) {
                channels[track].cut();
            }
        }
        unitsInRow = tempo.startRow(pattern.global(row));
        for (int track = 0; track < pattern.tracks(); track++) {
            final DmfTrackEntry entry = pattern.entry(row, track);
            if (entry != null) {
                channels[track].entry(entry);
            }
        }
        return true;
    }

    private void advanceRow() {
        if (++row >= file.patternAt(order).rows()) {
            row = 0;
            order++;
        }
    }

    private void room(int frames) {
        if (accumulator.length < frames * STEREO) {
            accumulator = new int[frames * STEREO];
        }
    }

    /**
     * One track into the running total at the rate, loudness and balance it has for this unit. The loudest frame
     * is kept for the scope.
     */
    private void mixIn(DmfChannel channel, int at, int frames) {
        final DmfVoice voice = channel.voice;
        final double frequency = channel.frequency();
        if (!voice.sounding || voice.sample == null || frequency <= 0) {
            voice.peak = 0;
            return;
        }
        final double step = frequency / sampleRate;
        final int gain = channel.loudness();
        final int right = channel.panning();
        final int left = DmfVoice.FULL - right;
        int peak = 0;
        for (int frame = 0; frame < frames && voice.sounding; frame++) {
            final int sound = voice.frameAt();
            peak = Math.max(peak, Math.abs(sound));
            if (!voice.muted) {
                final int level = sound * gain / DmfVoice.FULL;
                accumulator[at + frame * STEREO] += level * left / DmfVoice.FULL;
                accumulator[at + frame * STEREO + 1] += level * right / DmfVoice.FULL;
            }
            voice.advance(step);
        }
        voice.peak = peak;
    }

    /**
     * Every track can reach full scale on its own, so the sum is given room for all of them before it is handed
     * out.
     */
    private void flush(short[] out, int frames) {
        for (int slot = 0; slot < frames * STEREO; slot++) {
            out[slot] = (short) Math.clamp(accumulator[slot] / channels.length, Short.MIN_VALUE, Short.MAX_VALUE);
        }
    }
}

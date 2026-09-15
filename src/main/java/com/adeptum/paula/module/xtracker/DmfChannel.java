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

/**
 * One track of the song while it plays: the voice it sounds through, the note and instrument it last had, and
 * its pitch, volume and balance. Pitch counts 128ths of a semitone up from C-0, and a sample sounds at its own
 * C-3 frequency on C-3.
 */
final class DmfChannel {

    static final int SEMITONE = 128;
    private static final int C3_PITCH = 36 * SEMITONE;
    private static final double OCTAVE = 12.0 * SEMITONE;

    final DmfVoice voice = new DmfVoice();
    int instrument;
    int note;
    int pitch;
    int volume = DmfVoice.FULL;
    int balance = DmfVoice.MIDDLE;
    boolean held;

    private final DmfFile file;
    private DmfTrackEntry pending;

    DmfChannel(DmfFile file) {
        this.file = file;
    }

    /**
     * A new entry on the track, taken up on the unit it arrives on.
     */
    void entry(DmfTrackEntry entry) {
        pending = entry;
    }

    void unit() {
        if (pending != null) {
            touch(pending);
            tune(pending);
            pending = null;
        }
    }

    /**
     * Silences a track the pattern being played does not have.
     */
    void cut() {
        voice.silence();
        pending = null;
        held = false;
    }

    /**
     * The rate the sample is stepped through at; a note off holds it still.
     */
    double frequency() {
        if (voice.sample == null || held) {
            return 0;
        }
        return voice.sample.c3Frequency() * Math.pow(2, (pitch - C3_PITCH) / OCTAVE);
    }

    int loudness() {
        return volume;
    }

    int panning() {
        return balance;
    }

    /**
     * What the entry does to the sample: a note with an instrument starts it, an instrument alone starts the last
     * note again, a note alone sounds a held sample again, a note off holds it, and the volume byte is set last so
     * it wins over the sample's own.
     */
    private void touch(DmfTrackEntry entry) {
        if (entry.hasNote() && entry.hasInstrument()) {
            start(entry.instrument());
        } else if (entry.hasInstrument()) {
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
    }

    private void start(int number) {
        final DmfSample sample = file.sample(number);
        instrument = number;
        held = false;
        if (sample == null) {
            voice.silence();
            return;
        }
        voice.start(sample, 0);
        if (sample.volume() > 0) {
            volume = sample.volume();
        }
    }

    /**
     * An instrument without a note starts the last note again on the sample the channel already has, at the
     * volume it already has, the way OpenMPT plays such an entry.
     */
    private void restart() {
        if (note > 0 && voice.sample != null) {
            voice.start(voice.sample, 0);
            held = false;
        }
    }

    private void tune(DmfTrackEntry entry) {
        if (entry.hasNote()) {
            note = entry.note();
            pitch = (note - 1) * SEMITONE;
        }
    }
}

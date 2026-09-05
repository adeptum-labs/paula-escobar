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

/**
 * One channel of a tune while it plays. The sequencer keeps the note, the instrument and everything the
 * effects move here; the frame it ends in leaves behind a waveform in {@link #voiceBuffer}, the period as
 * a 16.16 step through it in {@link #delta}, and the volume and panning the mixer reads.
 *
 * <p>The four pointers of the replayer become an array reference and an offset into it, since they point
 * either into {@link HvlTables#WAVES} or into one of this voice's own buffers. The fields the C keeps as
 * 16-bit are {@code short} here so that they wrap where its arithmetic wraps.
 */
final class HvlVoice {

    /** How much of a voice buffer the mixer plays before it steps back to the start of it. */
    static final int WAVE_SAMPLES = 0x280;
    static final int BUFFER_LENGTH = 0x282 * 4;
    static final int SQUARE_BUFFER_LENGTH = 0x80;

    final byte[] voiceBuffer = new byte[BUFFER_LENGTH];
    final byte[] ringVoiceBuffer = new byte[BUFFER_LENGTH];
    final byte[] squareTempBuffer = new byte[SQUARE_BUFFER_LENGTH];

    boolean muted;

    int voiceNum;
    boolean trackOn;
    int track;
    int nextTrack;
    int transpose;
    int nextTranspose;
    int overrideTranspose;

    HvlInstrument instrument;
    HvlPlaylist playlist;

    int adsrVolume;
    int attackFrames;
    int attackVolume;
    int decayFrames;
    int decayVolume;
    int sustainFrames;
    int releaseFrames;
    int releaseVolume;

    byte[] audioSource;
    int audioOffset;
    byte[] mixSource;
    int mixOffset;
    int samplePos;
    int delta;

    short instrPeriod;
    short trackPeriod;
    short vibratoPeriod;
    short audioPeriod;
    short voicePeriod;
    short audioVolume;
    int voiceVolume;

    int waveLength;
    int waveform;
    boolean newWaveform;
    boolean plantPeriod;
    boolean plantSquare;
    boolean ignoreSquare;
    boolean fixedNote;
    int noiseRandom;

    int noteMaxVolume;
    int perfSubVolume;
    int trackMasterVolume;
    int volumeSlideUp;
    int volumeSlideDown;

    int hardCutFrames;
    boolean hardCutRelease;
    int hardCutReleaseFrames;

    boolean periodSlideOn;
    boolean periodSlideWithLimit;
    int periodSlideSpeed;
    short periodSlidePeriod;
    int periodSlideLimit;
    boolean periodPerfSlideOn;
    int periodPerfSlideSpeed;
    short periodPerfSlidePeriod;

    int vibratoDelay;
    int vibratoSpeed;
    int vibratoCurrent;
    int vibratoDepth;

    boolean squareOn;
    boolean squareInit;
    boolean squareSlidingIn;
    boolean squareReverse;
    int squareWait;
    int squareLowerLimit;
    int squareUpperLimit;
    int squarePos;
    int squareSign;

    boolean filterOn;
    boolean filterInit;
    boolean filterSlidingIn;
    int filterWait;
    int filterSpeed;
    int filterUpperLimit;
    int filterLowerLimit;
    int filterPos;
    int filterSign;
    int ignoreFilter;

    int perfCurrent;
    int perfSpeed;
    int perfWait;

    boolean noteDelayOn;
    boolean noteCutOn;
    int noteDelayWait;
    int noteCutWait;

    int pan;
    int setPan;
    int panMultLeft;
    int panMultRight;

    byte[] ringAudioSource;
    int ringAudioOffset;
    byte[] ringMixSource;
    int ringMixOffset;
    int ringSamplePos;
    int ringDelta;
    boolean ringPlantPeriod;
    boolean ringNewWaveform;
    boolean ringFixedPeriod;
    int ringWaveform;
    int ringBasePeriod;
    short ringAudioPeriod;
}

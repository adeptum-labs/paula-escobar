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
 * What the flags of an MO3 are taken to mean follows Load_mo3.cpp of
 * OpenMPT, Copyright © 2004-2026 the OpenMPT project developers and
 * Copyright © 1997-2003 Olivier Lapicque, licensed under the three-clause
 * BSD licence and used here under the GNU General Public License.
 */

package com.adeptum.paula.module.mo3;

import de.quippy.javamod.io.ModfileInputStream;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternContainer;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.midi.MidiMacros;
import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import de.quippy.javamod.multimedia.mod.mixer.ProTrackerMixer;
import de.quippy.javamod.multimedia.mod.mixer.ScreamTrackerMixer;
import java.io.IOException;
import java.util.Arrays;

/**
 * An MO3 as the tracker it was packed from, ready for that tracker's player.
 *
 * <p>The format keeps a flag saying which of the five wrote the module and nothing else of the file it came
 * from, so everything here answers from that flag: Impulse Tracker and Scream Tracker modules are handed to
 * the Scream Tracker mixer and count their effects by letter, and ProTracker, Fast Tracker and MultiTracker
 * ones go to the ProTracker mixer and count theirs by number.</p>
 */
public final class Mo3Module extends de.quippy.javamod.multimedia.mod.loader.Module {

    private static final String[] EXTENSIONS = {"mo3"};
    private static final String MOD_ID = "MO3";
    private static final byte[] MAGIC = {'M', 'O', '3'};

    private static final int LOUDEST_MIX = 127;
    private static final int PANNING_SEPARATION = 128;
    private static final int DEFAULT_SPEED = 6;
    private static final int DEFAULT_TEMPO = 125;
    private static final int FULL_RIGHT = 256;
    private static final int AMIGA_PANNING_PERIOD = 3;
    private static final int AMIGA_CHANNELS = 4;

    /**
     * The three octaves ProTracker itself could sound, outside which a module is played as a Fast Tracker one.
     */
    private static final int FIRST_AMIGA_NOTE = 37;
    private static final int LAST_AMIGA_NOTE = 72;

    /**
     * The volume Impulse Tracker mixes a sample at, which the format writes as a step away from it either way.
     */
    private static final double SAMPLE_VOLUME_SCALE = 3.1 / 20.0;
    private static final int SAMPLE_VOLUME_BELOW = 52;
    private static final int SAMPLE_VOLUME_ABOVE = 51;

    private Mo3File file;
    private String message = "";
    private boolean amigaNotesOnly = true;

    public Mo3Module() {
    }

    private Mo3Module(String fileName) {
        super(fileName);
    }

    /**
     * Reads a module out of the bytes of an MO3 file, ready to be handed to a mixer.
     */
    static Mo3Module of(String fileName, byte[] bytes) throws IOException {
        final Mo3Module module = new Mo3Module(fileName);
        module.load(bytes);
        return module;
    }

    Mo3File file() {
        return file;
    }

    private void load(byte[] bytes) throws IOException {
        file = Mo3Reader.read(bytes);
        final Mo3Song song = file.song();

        setModType(modType());
        setModID(MOD_ID);
        setTrackerName(file.kind().tracker());
        setSongName(file.name());
        message = file.message();
        setSongFlags(songFlags());

        setNChannels(song.channels());
        setNPattern(song.patterns());
        setNInstruments(file.hasInstruments() ? song.instruments() : instrumentsOfSampleBasedTracker());
        setNSamples(song.samples());
        setTempo(song.speed() == 0 ? DEFAULT_SPEED : song.speed());
        setBPMSpeed(song.tempo() == 0 ? DEFAULT_TEMPO : song.tempo());
        setBaseVolume(baseVolume());
        setMixingPreAmp(mixingPreAmp());
        setSongRestart(song.restart());

        readChannels();
        readArrangement();
        readPatterns();
        if (isAmigaLike()) {
            setSongFlags(getSongFlags() | ModConstants.SONG_AMIGALIMITS);
        }
        setInstrumentContainer(new InstrumentsContainer(this,
                file.hasInstruments() ? song.instruments() : 0, song.samples()));
    }

    /**
     * ProTracker and Scream Tracker reach their samples without instruments, and their players still count one
     * instrument per sample.
     */
    private int instrumentsOfSampleBasedTracker() {
        return file.kind() == Mo3Kind.IMPULSE_TRACKER ? 0 : file.song().samples();
    }

    /**
     * Whether the module is one ProTracker itself could have played, which decides both how it is tuned and
     * how far its slides may go.
     */
    @Override
    public boolean isAmigaLike() {
        return file.kind() == Mo3Kind.PROTRACKER && getNChannels() <= AMIGA_CHANNELS && amigaNotesOnly;
    }

    private int modType() {
        return switch (file.kind()) {
            case IMPULSE_TRACKER -> ModConstants.MODTYPE_IT;
            case SCREAM_TRACKER -> ModConstants.MODTYPE_S3M;
            case PROTRACKER, MULTITRACKER -> ModConstants.MODTYPE_MOD;
            case FAST_TRACKER -> ModConstants.MODTYPE_XM;
        };
    }

    private int songFlags() {
        final Mo3Song song = file.song();
        int flags = ModConstants.SONG_ISSTEREO;
        if (song.has(Mo3Song.LINEAR_SLIDES)) {
            flags |= ModConstants.SONG_LINEARSLIDES;
        }
        if (song.has(Mo3Song.EXTENDED_FILTER_RANGE)) {
            flags |= ModConstants.SONG_EXFILTERRANGE;
        }
        if (file.hasInstruments()) {
            flags |= ModConstants.SONG_USEINSTRUMENTS;
        }
        if (file.kind() == Mo3Kind.SCREAM_TRACKER) {
            if (song.has(Mo3Song.S3M_AMIGA_LIMITS)) {
                flags |= ModConstants.SONG_AMIGALIMITS;
            }
            if (song.has(Mo3Song.S3M_FAST_SLIDES)) {
                flags |= ModConstants.SONG_FASTVOLSLIDES;
            }
        }
        if (file.kind() == Mo3Kind.IMPULSE_TRACKER) {
            if (!song.has(Mo3Song.IT_OLD_EFFECTS)) {
                flags |= ModConstants.SONG_ITOLDEFFECTS;
            }
            if (!song.has(Mo3Song.IT_COMPATIBLE_GXX)) {
                flags |= ModConstants.SONG_ITCOMPATMODE;
            }
        }
        return flags;
    }

    /**
     * Impulse Tracker keeps a global volume of up to a hundred and twenty-eight and Scream Tracker one of up
     * to sixty-four; the rest play at the loudest the mixer has.
     */
    private int baseVolume() {
        final int volume = file.song().globalVolume();
        return switch (file.kind()) {
            case IMPULSE_TRACKER -> Math.min(volume, ModConstants.MAXGLOBALVOLUME / 2) * 2;
            case SCREAM_TRACKER -> Math.min(volume, ModConstants.MAXSAMPLEVOLUME) * 4;
            default -> ModConstants.MAXGLOBALVOLUME;
        };
    }

    /**
     * How loud the samples are mixed, which the format writes as a step either side of what Impulse Tracker
     * settled on rather than as the volume itself.
     */
    private int mixingPreAmp() {
        final int step = file.song().sampleVolume();
        final int preAmp = step < 0
                ? step + SAMPLE_VOLUME_BELOW
                : (int) Math.exp(step * SAMPLE_VOLUME_SCALE) + SAMPLE_VOLUME_ABOVE;
        return Math.clamp(preAmp, 1, LOUDEST_MIX);
    }

    private void readChannels() {
        final Mo3Song song = file.song();
        channelVolume = new int[Mo3Song.CHANNELS_IN_HEADER];
        panningValue = new int[Mo3Song.CHANNELS_IN_HEADER];
        for (int channel = 0; channel < Mo3Song.CHANNELS_IN_HEADER; channel++) {
            channelVolume[channel] = file.kind() == Mo3Kind.IMPULSE_TRACKER
                    ? Math.min(song.channelVolume()[channel], ModConstants.MAXSAMPLEVOLUME)
                    : ModConstants.MAXSAMPLEVOLUME;
            panningValue[channel] = panning(song.channelPanning()[channel]);
        }
    }

    private static int panning(int stored) {
        if (stored == Mo3Song.PANNING_SURROUND) {
            return ModConstants.CHANNEL_IS_SURROUND;
        }
        return stored == Mo3Song.PANNING_FULL_RIGHT ? FULL_RIGHT : stored;
    }

    /**
     * Impulse Tracker and Scream Tracker keep two orders that name no pattern, one to skip over and one to
     * stop at; ProTracker and Fast Tracker have patterns of those numbers, so there they are ordinary orders.
     */
    private void readArrangement() {
        setSongLength(file.orders().length);
        allocArrangement(getSongLength());
        final int[] arrangement = getArrangement();
        for (int order = 0; order < arrangement.length; order++) {
            arrangement[order] = order(file.orders()[order]);
        }
        removeEndOfArrangement();
    }

    private int order(int stored) {
        if (!file.hasOrderSeparators()) {
            return stored;
        }
        return switch (stored) {
            case Mo3File.ORDER_STOP -> ModConstants.INVALID_PAT_INDEX;
            case Mo3File.ORDER_SKIP -> ModConstants.IGNORE_PAT_INDEX;
            default -> stored;
        };
    }

    private void readPatterns() {
        final PatternContainer patterns = new PatternContainer(this, getNPattern());
        for (int index = 0; index < getNPattern(); index++) {
            final Mo3Pattern pattern = file.patterns().get(index);
            patterns.createPattern(index, pattern.rows(), getNChannels());
            for (int channel = 0; channel < getNChannels(); channel++) {
                readTrack(patterns, index, channel, pattern);
            }
        }
        setPatternContainer(patterns);
    }

    private void readTrack(PatternContainer patterns, int index, int channel, Mo3Pattern pattern) {
        final int track = pattern.trackFor(channel);
        final Mo3Event[] rows = track < file.tracks().size()
                ? Mo3Track.rows(file.tracks().get(track), pattern.rows(), file.kind())
                : new Mo3Event[pattern.rows()];
        for (int row = 0; row < rows.length; row++) {
            fill(patterns.createPatternElement(index, row, channel), rows[row]);
        }
    }

    private void fill(PatternElement element, Mo3Event event) {
        if (event == null) {
            return;
        }
        if (event.note() > 0 && (event.note() < FIRST_AMIGA_NOTE || event.note() > LAST_AMIGA_NOTE)) {
            amigaNotesOnly = false;
        }
        element.setNoteIndex(event.note());
        element.setPeriod(period(event.note()));
        element.setInstrument(event.instrument());
        element.setEffekt(event.effect());
        element.setEffektOp(event.effectOp());
        element.setVolumeEffekt(event.volumeEffect());
        element.setVolumeEffektOp(event.volumeEffectOp());
    }

    /**
     * The mixer wants the period beside the note, and the notes that are not notes carry themselves there.
     */
    private static int period(int note) {
        if (note > 0 && note <= ModConstants.noteValues.length) {
            return ModConstants.noteValues[note - 1];
        }
        return note == Mo3Event.NO_NOTE ? 0 : note;
    }

    @Override
    public BasicModMixer getModMixer(int sampleRate, int doISP, int doAmigaEmulation, int doNoLoops,
            int maxNNAChannels) {
        return file.kind().isScreamTrackerFamily()
                ? new ScreamTrackerMixer(this, sampleRate, doISP, doAmigaEmulation, doNoLoops, maxNNAChannels)
                : new ProTrackerMixer(this, sampleRate, doISP, doAmigaEmulation, doNoLoops, maxNNAChannels);
    }

    @Override
    public String[] getFileExtensionList() {
        return EXTENSIONS;
    }

    @Override
    public int getFrequencyTable() {
        final boolean linear = (getSongFlags() & ModConstants.SONG_LINEARSLIDES) != 0;
        return switch (file.kind()) {
            case IMPULSE_TRACKER -> linear ? ModConstants.IT_LINEAR_TABLE : ModConstants.IT_AMIGA_TABLE;
            case SCREAM_TRACKER -> ModConstants.STM_S3M_TABLE;
            case FAST_TRACKER -> linear ? ModConstants.XM_LINEAR_TABLE : ModConstants.XM_AMIGA_TABLE;
            default -> isAmigaLike() ? ModConstants.AMIGA_TABLE : ModConstants.XM_AMIGA_TABLE;
        };
    }

    /**
     * Fast Tracker keeps no panning of its own in an MO3, so its channels are spread the way the Amiga spread
     * them, which is what its player does with a module of its own.
     */
    @Override
    public int getPanningValue(int channel) {
        if (file.kind() == Mo3Kind.FAST_TRACKER) {
            return channel % AMIGA_PANNING_PERIOD != 0 ? ModConstants.OLD_PANNING_RIGHT : ModConstants.OLD_PANNING_LEFT;
        }
        return panningValue[channel];
    }

    @Override
    public int getChannelVolume(int channel) {
        return channelVolume[channel];
    }

    @Override
    public int getPanningSeparation() {
        return file.kind() == Mo3Kind.IMPULSE_TRACKER ? file.song().panSeparation() : PANNING_SEPARATION;
    }

    @Override
    public String getSongMessage() {
        return message.isEmpty() ? null : message;
    }

    @Override
    public MidiMacros getMidiConfig() {
        return null;
    }

    @Override
    public boolean getFT2Tremolo() {
        return false;
    }

    @Override
    public boolean getModSpeedIsTicks() {
        return false;
    }

    @Override
    public boolean supportsAmigaFilter() {
        return file.kind() == Mo3Kind.PROTRACKER;
    }

    @Override
    public boolean checkLoadingPossible(ModfileInputStream inputStream) throws IOException {
        final byte[] magic = new byte[MAGIC.length];
        inputStream.seek(0);
        inputStream.read(magic);
        inputStream.seek(0);
        return Arrays.equals(magic, MAGIC);
    }

    @Override
    protected de.quippy.javamod.multimedia.mod.loader.Module getNewInstance(String fileName) {
        return new Mo3Module(fileName);
    }

    @Override
    protected void loadModFileInternal(ModfileInputStream inputStream) throws IOException {
        final byte[] bytes = new byte[(int) inputStream.getLength()];
        inputStream.seek(0);
        inputStream.read(bytes);
        load(bytes);
    }
}

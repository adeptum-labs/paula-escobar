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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

/**
 * Reads the modules of MED and OctaMED. A file is a header of offsets into itself: one to the song, one to the
 * table of blocks and one to the table of instruments, with a fourth to the block of later additions that
 * carries the name, the annotation and what each instrument holds and decays for.
 *
 * <p>MMD0 packs a line into three bytes and MMD1 into four; MMDC packs the same lines as MMD0 does and then
 * runs the whole block through a simple counted compression.
 */
final class MedReader {

    private static final String MMD = "MMD";
    private static final int VERSION_AT = 3;
    private static final char FIRST_VERSION = '0';
    private static final char COMPRESSED = 'C';
    private static final int MMD0 = 0;
    private static final int MMD1 = 1;
    private static final int MMD2 = 2;
    private static final int MMD3 = 3;

    private static final int SONG_OFFSET_AT = 8;
    private static final int BLOCKS_OFFSET_AT = 16;
    private static final int SAMPLES_OFFSET_AT = 24;
    private static final int EXPANSION_OFFSET_AT = 32;

    private static final int SAMPLE_SETTINGS = 63;
    private static final int PLAY_SEQUENCE_LENGTH = 256;
    private static final int TRACK_VOLUMES = 16;
    private static final int MOST_BLOCKS = 255;
    private static final int MOST_LINES = 3200;
    private static final int MOST_TRACKS = 16;
    private static final int MOST_MIXED_TRACKS = 64;
    private static final int PLAY_SEQUENCE_NAME_LENGTH = 32;

    private static final int SYNTHETIC = -1;
    private static final int HYBRID = -2;
    private static final int SAMPLE = 0;
    private static final int MIX_MODE_SAMPLE = 7;
    private static final int SIXTEEN_BIT = 0x10;
    private static final int AURA_SIXTEEN_BIT = 0x18;
    private static final int STEREO = 0x20;
    private static final int TYPE_MASK = ~(SIXTEEN_BIT | AURA_SIXTEEN_BIT | STEREO);

    private static final int HOLD_AND_DECAY_SIZE = 2;
    private static final int FINETUNE_SIZE = 4;
    private static final int INSTRUMENT_NAME_LENGTH = 40;
    private static final int LONGEST_ANNOTATION = 0x10000;

    private static final int MMD0_NOTE_MASK = 0x3F;
    private static final int MMD1_NOTE_MASK = 0x7F;
    private static final int MMD0_INSTRUMENT_MASK = 0x3F;
    private static final int MMD0_COMMAND_MASK = 0x0F;
    private static final int MMD0_INSTRUMENT_HIGH = 0x80;
    private static final int MMD0_INSTRUMENT_MIDDLE = 0x40;

    private static final int PACKED_RUN = 0x80;
    private static final int PACKED_WRAP = 256;

    private static final int MIX_SETTINGS_LENGTH = 5;
    private static final int SECTIONED_RESERVED = 223;
    private static final int MIX_MODE_OCTAVES = 24;
    private static final int SYNTH_OCTAVES = 24;
    private static final int SYNTH_RESERVED = 3;
    private static final int SYNTH_TABLE_LENGTH = 128;
    private static final int MOST_WAVEFORMS = 64;
    private static final int NO_WAVEFORMS = 0xFFFF;
    private static final int DEFAULT_TEMPO = 33;
    private static final int DEFAULT_SPEED = 6;
    private static final int FULL_VOLUME = 64;

    private MedReader() {
    }

    static boolean looksLikeMed(byte[] head) {
        if (head.length <= VERSION_AT || head[0] != 'M' || head[1] != 'M' || head[2] != 'D') {
            return false;
        }
        final int version = version(head[VERSION_AT]);
        return head[VERSION_AT] == COMPRESSED || version >= MMD0 && version <= MMD1;
    }

    static MedFile read(byte[] file) throws IOException {
        final MedBytes bytes = new MedBytes(file);
        final String identifier = bytes.text(VERSION_AT + 1);
        if (!identifier.startsWith(MMD)) {
            throw new IOException("Not an OctaMED module");
        }
        final char kind = identifier.charAt(VERSION_AT);
        final boolean compressed = kind == COMPRESSED;
        final int version = compressed ? MMD0 : version((byte) kind);
        if (version < MMD0 || version > MMD3) {
            throw new IOException("OctaMED module of a kind this cannot read: " + identifier);
        }

        bytes.seek(SONG_OFFSET_AT);
        final int songAt = bytes.u32();
        bytes.seek(BLOCKS_OFFSET_AT);
        final int blocksAt = bytes.u32();
        bytes.seek(SAMPLES_OFFSET_AT);
        final int samplesAt = bytes.u32();
        bytes.seek(EXPANSION_OFFSET_AT);
        final int expansionAt = bytes.u32();

        bytes.seek(songAt);
        final Settings[] settings = settings(bytes);
        final Song song = version >= MMD2 ? sectionedSong(bytes) : song(bytes);
        final Expansion expansion = expansionAt == 0 ? Expansion.NONE : expansion(bytes, expansionAt, song.samples);

        final List<MedBlock> blocks = blocks(bytes, blocksAt, song, version, compressed);
        final List<MedInstrument> instruments = instruments(bytes, samplesAt, song, settings, expansion, version);
        return new MedFile(expansion.name, expansion.annotation, version, song.toSong(), blocks, instruments);
    }

    private static int version(byte identifier) {
        return identifier - FIRST_VERSION;
    }

    /**
     * What the song says about each of its sixty-three instrument slots, which is where the loop, the volume
     * and the transpose live rather than with the sample itself.
     */
    private static Settings[] settings(MedBytes bytes) throws IOException {
        final Settings[] settings = new Settings[SAMPLE_SETTINGS];
        for (int slot = 0; slot < SAMPLE_SETTINGS; slot++) {
            final int loopStart = bytes.u16() << 1;
            final int loopLength = bytes.u16() << 1;
            final int midiChannel = bytes.u8();
            bytes.skip(1);
            settings[slot] = new Settings(loopStart, loopLength, midiChannel, bytes.u8(), bytes.s8());
        }
        return settings;
    }

    private static Song song(MedBytes bytes) throws IOException {
        final int blocks = bytes.u16();
        final int length = bytes.u16();
        if (blocks > MOST_BLOCKS || length > PLAY_SEQUENCE_LENGTH) {
            throw new IOException("OctaMED song of " + blocks + " blocks and " + length + " positions");
        }
        final int[] playSeq = new int[length];
        for (int position = 0; position < PLAY_SEQUENCE_LENGTH; position++) {
            final int block = bytes.u8();
            if (position < length) {
                playSeq[position] = block;
            }
        }
        final int tempo = bytes.u16();
        final int transpose = bytes.s8();
        final int flags = bytes.u8();
        final int flags2 = bytes.u8();
        final int speed = bytes.u8();
        final int[] trackVolumes = new int[TRACK_VOLUMES];
        for (int track = 0; track < TRACK_VOLUMES; track++) {
            trackVolumes[track] = bytes.u8();
        }
        final int masterVolume = bytes.u8();
        final int samples = bytes.u8();
        if (samples > SAMPLE_SETTINGS) {
            throw new IOException("OctaMED song of " + samples + " instruments");
        }
        return new Song(blocks, playSeq, tempo, speed, transpose, flags, flags2, masterVolume, trackVolumes,
                new int[0], samples);
    }


    /**
     * MMD2 and MMD3 keep their song differently: the positions are sections, each naming one of a table of
     * play sequences, and the play sequences hold block numbers as words rather than bytes. Only the first
     * sequence is played, which is what libxmp does and what all but a handful of modules mean.
     */
    private static Song sectionedSong(MedBytes bytes) throws IOException {
        final int blocks = bytes.u16();
        final int sections = bytes.u16();
        final int sequenceTableAt = bytes.u32();
        bytes.skip(Integer.BYTES);
        final int trackVolumesAt = bytes.u32();
        final int tracks = bytes.u16();
        final int sequences = bytes.u16();
        final int trackPansAt = bytes.u32();
        bytes.skip(Integer.BYTES);
        bytes.skip(Short.BYTES);
        final int channels = bytes.u16();
        bytes.skip(MIX_SETTINGS_LENGTH);
        bytes.skip(SECTIONED_RESERVED);
        final int tempo = bytes.u16();
        final int transpose = bytes.s8();
        final int flags = bytes.u8();
        final int flags2 = bytes.u8();
        final int speed = bytes.u8();
        bytes.skip(TRACK_VOLUMES);
        final int masterVolume = bytes.u8();
        final int samples = bytes.u8();
        if (blocks > MOST_BLOCKS || sections > PLAY_SEQUENCE_LENGTH || samples > SAMPLE_SETTINGS) {
            throw new IOException("OctaMED song of " + blocks + " blocks and " + samples + " instruments");
        }
        final int width = Math.max(tracks, channels);
        return new Song(blocks, playSequence(bytes, sequenceTableAt, sequences), tempo, speed, transpose, flags,
                flags2, masterVolume, volumes(bytes, trackVolumesAt, width, FULL_VOLUME),
                volumes(bytes, trackPansAt, width, 0), samples);
    }

    private static int[] playSequence(MedBytes bytes, int tableAt, int sequences) throws IOException {
        if (tableAt == 0 || sequences == 0) {
            return new int[0];
        }
        bytes.seek(tableAt);
        final int sequenceAt = bytes.u32();
        if (sequenceAt == 0) {
            return new int[0];
        }
        bytes.seek(sequenceAt);
        bytes.skip(PLAY_SEQUENCE_NAME_LENGTH);
        bytes.skip(Integer.BYTES * 2);
        final int length = bytes.u16();
        if (length > MOST_BLOCKS) {
            throw new IOException("OctaMED play sequence of " + length + " positions");
        }
        final int[] playSeq = new int[length];
        for (int position = 0; position < length; position++) {
            playSeq[position] = bytes.u16();
        }
        return playSeq;
    }

    /**
     * A table of a byte per track, which a song is free not to carry at all.
     */
    private static int[] volumes(MedBytes bytes, int at, int tracks, int fallback) throws IOException {
        final int[] volumes = new int[tracks];
        Arrays.fill(volumes, fallback);
        if (at == 0 || tracks == 0 || !bytes.has(0)) {
            return volumes;
        }
        bytes.seek(at);
        for (int track = 0; track < tracks && bytes.has(1); track++) {
            volumes[track] = bytes.u8();
        }
        return volumes;
    }

    /**
     * The later additions: the song's own name, the annotation the musician left, and a row per instrument
     * saying how long it holds a note and how fast it fades once let go.
     */
    private static Expansion expansion(MedBytes bytes, int expansionAt, int samples) throws IOException {
        bytes.seek(expansionAt);
        bytes.skip(Integer.BYTES);
        final int holdsAt = bytes.u32();
        final int holdEntries = bytes.u16();
        final int holdSize = bytes.u16();
        final int annotationAt = bytes.u32();
        final int annotationLength = bytes.u32();
        final int namesAt = bytes.u32();
        final int nameEntries = bytes.u16();
        final int nameSize = bytes.u16();
        bytes.skip(Integer.BYTES * 4);
        final int songNameAt = bytes.u32();
        final int songNameLength = bytes.u32();

        final Hold[] holds = holds(bytes, holdsAt, holdEntries, holdSize, samples);
        final String[] names = names(bytes, namesAt, nameEntries, nameSize, samples);
        return new Expansion(text(bytes, songNameAt, songNameLength),
                text(bytes, annotationAt, Math.min(annotationLength, LONGEST_ANNOTATION)), holds, names,
                holdSize);
    }

    private static Hold[] holds(MedBytes bytes, int at, int entries, int size, int samples) throws IOException {
        final Hold[] holds = new Hold[samples];
        Arrays.fill(holds, Hold.NONE);
        if (at == 0 || size < HOLD_AND_DECAY_SIZE) {
            return holds;
        }
        bytes.seek(at);
        for (int slot = 0; slot < samples && slot < entries; slot++) {
            final int row = bytes.position();
            final int hold = bytes.u8();
            final int decay = bytes.u8();
            int finetune = 0;
            if (size >= FINETUNE_SIZE) {
                bytes.skip(1);
                finetune = bytes.s8();
            }
            holds[slot] = new Hold(hold, decay, finetune);
            bytes.seek(row + size);
        }
        return holds;
    }

    private static String[] names(MedBytes bytes, int at, int entries, int size, int samples) throws IOException {
        final String[] names = new String[samples];
        Arrays.fill(names, "");
        if (at == 0 || size == 0) {
            return names;
        }
        bytes.seek(at);
        for (int slot = 0; slot < samples && slot < entries; slot++) {
            final int row = bytes.position();
            names[slot] = bytes.text(Math.min(size, INSTRUMENT_NAME_LENGTH));
            bytes.seek(row + size);
        }
        return names;
    }

    private static String text(MedBytes bytes, int at, int length) throws IOException {
        if (at == 0 || length <= 0) {
            return "";
        }
        bytes.seek(at);
        return bytes.text(Math.min(length, bytes.length() - at));
    }

    private static List<MedBlock> blocks(MedBytes bytes, int blocksAt, Song song, int version,
                                         boolean compressed) throws IOException {
        bytes.seek(blocksAt);
        final int[] offsets = new int[song.blocks];
        for (int block = 0; block < song.blocks; block++) {
            offsets[block] = bytes.u32();
        }
        final List<MedBlock> blocks = new ArrayList<>(song.blocks);
        for (final int offset : offsets) {
            blocks.add(offset == 0 ? empty() : block(bytes, offset, song, version, compressed));
        }
        return blocks;
    }

    private static MedBlock empty() {
        return new MedBlock(1, 1, new MedEntry[]{MedEntry.EMPTY});
    }

    private static MedBlock block(MedBytes bytes, int at, Song song, int version, boolean compressed)
            throws IOException {
        bytes.seek(at);
        final int tracks;
        final int lines;
        if (version >= MMD1) {
            tracks = bytes.u16();
            lines = bytes.u16() + 1;
            bytes.skip(Integer.BYTES);
        } else {
            tracks = bytes.u8();
            lines = bytes.u8() + 1;
        }
        final int mostTracks = version >= MMD2 ? MOST_MIXED_TRACKS : MOST_TRACKS;
        if (lines > MOST_LINES || tracks > mostTracks || tracks == 0) {
            throw new IOException("OctaMED block of " + tracks + " tracks and " + lines + " lines");
        }
        final int width = version >= MMD1 ? 4 : 3;
        final byte[] packed = compressed ? unpack(bytes, tracks * lines * width) : bytes.bytes(tracks * lines * width);
        final MedEntry[] entries = new MedEntry[tracks * lines];
        for (int index = 0; index < entries.length; index++) {
            entries[index] = version >= MMD1 ? mmd1Entry(packed, index * width, song)
                    : mmd0Entry(packed, index * width, song);
        }
        return new MedBlock(tracks, lines, entries);
    }

    /**
     * MMDC writes a block as runs: a byte below the halfway mark counts the bytes that follow it verbatim, and
     * one above counts how many zeroes to leave in their place.
     */
    private static byte[] unpack(MedBytes bytes, int size) throws IOException {
        final byte[] block = new byte[size];
        int written = 0;
        while (written < size) {
            final int run = bytes.u8();
            if ((run & PACKED_RUN) != 0) {
                written += PACKED_WRAP - run;
                continue;
            }
            final int length = Math.min(run + 1, size - written);
            System.arraycopy(bytes.bytes(length), 0, block, written, length);
            written += length;
        }
        return block;
    }

    private static MedEntry mmd0Entry(byte[] block, int at, Song song) {
        final int first = block[at] & 0xFF;
        final int second = block[at + 1] & 0xFF;
        final int instrument = second >> 4 | (first & MMD0_INSTRUMENT_HIGH) >> 3
                | (first & MMD0_INSTRUMENT_MIDDLE) >> 1;
        return new MedEntry(note(first & MMD0_NOTE_MASK, song), instrument & MMD0_INSTRUMENT_MASK,
                second & MMD0_COMMAND_MASK, block[at + 2] & 0xFF);
    }

    private static MedEntry mmd1Entry(byte[] block, int at, Song song) {
        return new MedEntry(note(block[at] & MMD1_NOTE_MASK, song), block[at + 1] & MMD0_INSTRUMENT_MASK,
                block[at + 2] & 0xFF, block[at + 3] & 0xFF);
    }

    /**
     * The song's own transpose is folded in as the line is read, so the sequencer never has to know of it.
     */
    private static int note(int written, Song song) {
        if (written == 0) {
            return 0;
        }
        final int transposed = written + song.transpose;
        return transposed > 0 ? transposed : 0;
    }

    private static List<MedInstrument> instruments(MedBytes bytes, int samplesAt, Song song, Settings[] settings,
                                                   Expansion expansion, int version) throws IOException {
        bytes.seek(samplesAt);
        final int[] offsets = new int[song.samples];
        for (int slot = 0; slot < song.samples; slot++) {
            offsets[slot] = bytes.u32();
        }
        final List<MedInstrument> instruments = new ArrayList<>(song.samples);
        for (int slot = 0; slot < song.samples; slot++) {
            instruments.add(instrument(bytes, offsets[slot], settings[slot], expansion, slot, version));
        }
        return instruments;
    }

    /**
     * A song mixed in software sounds its samples two octaves lower than one played by the hardware, which
     * the later versions write into every plain instrument and any version writes into a mix-mode one.
     */
    private static int mixModeTranspose(int kind, int version) {
        return version >= MMD3 && kind == SAMPLE || kind == MIX_MODE_SAMPLE ? -MIX_MODE_OCTAVES : 0;
    }

    private static MedInstrument instrument(MedBytes bytes, int at, Settings settings, Expansion expansion,
                                            int slot, int version) throws IOException {
        final String name = expansion.nameOf(slot);
        final Hold hold = expansion.holdOf(slot);
        if (at == 0 || settings.midiChannel != 0) {
            return silent(name, settings, hold);
        }
        bytes.seek(at);
        final int length = bytes.u32();
        final int type = bytes.s16();
        final int octaves = MedTables.octavesOfType(type);
        if (octaves > 0) {
            return multiOctave(bytes, length, octaves, name, settings, hold);
        }
        if (type == SYNTHETIC) {
            return synthetic(bytes, at, name, settings, hold);
        }
        if (type == HYBRID) {
            return hybrid(bytes, at, name, settings, hold);
        }
        final int kind = type & TYPE_MASK;
        if (kind != SAMPLE && kind != MIX_MODE_SAMPLE) {
            return silent(name, settings, hold);
        }
        final boolean wide = (type & SIXTEEN_BIT) != 0;
        final int frames = wide ? length / 2 : length;
        if (!bytes.has(wide ? frames * 2 : frames)) {
            throw new IOException("OctaMED instrument longer than the module");
        }
        final MedLayer layer = new MedLayer(sample(bytes, frames, wide), settings.loopStart, settings.loopLength);
        return new MedInstrument(name, List.of(layer), 1, settings.volume,
                settings.transpose + mixModeTranspose(kind, version), hold.finetune, hold.hold, hold.decay,
                0, null);
    }

    private static MedInstrument silent(String name, Settings settings, Hold hold) {
        return new MedInstrument(name, List.of(), 0, settings.volume, settings.transpose, hold.finetune,
                hold.hold, hold.decay, settings.midiChannel, null);
    }

    /**
     * A synthetic instrument carries no sample but the machinery to make one: a bank of short waveforms and
     * two sequences, one stepping the volume and one swapping the waveform under the note, each at its own
     * speed. The waveforms are reached by offsets counted from the head of the instrument.
     */
    private static MedInstrument synthetic(MedBytes bytes, int at, String name, Settings settings, Hold hold)
            throws IOException {
        final SynthHeader header = synthHeader(bytes);
        if (header == null) {
            return silent(name, settings, hold);
        }
        final short[][] waveforms = new short[header.count][];
        final List<MedLayer> layers = new ArrayList<>(header.count);
        for (int waveform = 0; waveform < header.count; waveform++) {
            bytes.seek(at + header.offsets[waveform]);
            waveforms[waveform] = waveform(bytes);
            layers.add(new MedLayer(waveforms[waveform], 0, waveforms[waveform].length));
        }
        return new MedInstrument(name, layers, 1, settings.volume, settings.transpose - SYNTH_OCTAVES,
                hold.finetune, hold.hold, hold.decay, 0, header.toSynth(waveforms));
    }

    /**
     * A hybrid is a synthetic instrument whose first waveform is a whole sample rather than a short shape, so
     * the sequence steps the volume of a sampled sound and can still swap the waveforms behind it.
     */
    private static MedInstrument hybrid(MedBytes bytes, int at, String name, Settings settings, Hold hold)
            throws IOException {
        final SynthHeader header = synthHeader(bytes);
        if (header == null || header.count < 1) {
            return silent(name, settings, hold);
        }
        bytes.seek(at + header.offsets[0]);
        final int length = bytes.u32();
        if (bytes.s16() != SAMPLE || !bytes.has(length)) {
            return silent(name, settings, hold);
        }
        final short[][] waveforms = new short[header.count][];
        waveforms[0] = sample(bytes, length, false);
        final List<MedLayer> layers = new ArrayList<>(header.count);
        layers.add(new MedLayer(waveforms[0], settings.loopStart, settings.loopLength));
        for (int waveform = 1; waveform < header.count; waveform++) {
            bytes.seek(at + header.offsets[waveform]);
            waveforms[waveform] = waveform(bytes);
            layers.add(new MedLayer(waveforms[waveform], 0, waveforms[waveform].length));
        }
        return new MedInstrument(name, layers, 1, settings.volume, settings.transpose, hold.finetune,
                hold.hold, hold.decay, 0, header.toSynth(waveforms));
    }

    private static short[] waveform(MedBytes bytes) throws IOException {
        final int frames = bytes.u16() * 2;
        if (!bytes.has(frames)) {
            throw new IOException("OctaMED waveform longer than the module");
        }
        return sample(bytes, frames, false);
    }

    /**
     * What a synthetic and a hybrid instrument have in common: the two sequences, their speeds, and where the
     * waveforms lie relative to the head of the instrument.
     */
    private static SynthHeader synthHeader(MedBytes bytes) throws IOException {
        final int defaultDecay = bytes.u8();
        bytes.skip(SYNTH_RESERVED);
        bytes.skip(Short.BYTES * 2);
        final int volumeTableLength = bytes.u16();
        final int waveformTableLength = bytes.u16();
        final int volumeSpeed = bytes.u8();
        final int waveformSpeed = bytes.u8();
        final int count = bytes.u16();
        final byte[] volumeTable = bytes.bytes(SYNTH_TABLE_LENGTH);
        final byte[] waveformTable = bytes.bytes(SYNTH_TABLE_LENGTH);
        if (count == NO_WAVEFORMS || count < 1 || count > MOST_WAVEFORMS
                || volumeTableLength > SYNTH_TABLE_LENGTH || waveformTableLength > SYNTH_TABLE_LENGTH) {
            return null;
        }
        final int[] offsets = new int[count];
        for (int waveform = 0; waveform < count; waveform++) {
            offsets[waveform] = bytes.u32();
        }
        return new SynthHeader(count, offsets, volumeSpeed, waveformSpeed, defaultDecay,
                Arrays.copyOf(volumeTable, volumeTableLength),
                Arrays.copyOf(waveformTable, waveformTableLength));
    }

    private record SynthHeader(int count, int[] offsets, int volumeSpeed, int waveformSpeed, int defaultDecay,
                               byte[] volumeTable, byte[] waveformTable) {

        MedSynthInstrument toSynth(short[][] waveforms) {
            return new MedSynthInstrument(volumeSpeed, waveformSpeed, defaultDecay, volumeTable, waveformTable,
                    waveforms);
        }
    }

    /**
     * A multi-octave instrument is one run of bytes holding an octave of samples at a time, each twice the
     * length of the one below it, and its loop grows with them.
     */
    private static MedInstrument multiOctave(MedBytes bytes, int length, int octaves, String name,
                                             Settings settings, Hold hold) throws IOException {
        final List<MedLayer> layers = new ArrayList<>(octaves);
        int frames = length / ((1 << octaves) - 1);
        int loopStart = settings.loopStart;
        int loopLength = settings.loopLength;
        for (int octave = 0; octave < octaves; octave++) {
            if (!bytes.has(frames)) {
                throw new IOException("OctaMED instrument longer than the module");
            }
            layers.add(new MedLayer(sample(bytes, frames, false), loopStart, loopLength));
            frames <<= 1;
            loopStart <<= 1;
            loopLength <<= 1;
        }
        return new MedInstrument(name, layers, octaves, settings.volume, settings.transpose, hold.finetune,
                hold.hold, hold.decay, 0, null);
    }

    /**
     * Samples are kept as signed sixteen-bit frames whatever width the file wrote them in, so that everything
     * past the reader mixes the same way.
     */
    private static short[] sample(MedBytes bytes, int frames, boolean wide) throws IOException {
        final short[] sample = new short[frames];
        for (int frame = 0; frame < frames; frame++) {
            sample[frame] = wide ? (short) bytes.u16() : (short) (bytes.s8() << Byte.SIZE);
        }
        return sample;
    }

    private record Settings(int loopStart, int loopLength, int midiChannel, int volume, int transpose) {
    }

    private record Hold(int hold, int decay, int finetune) {

        static final Hold NONE = new Hold(0, 0, 0);
    }

    private record Song(int blocks, int[] playSeq, int tempo, int speed, int transpose, int flags, int flags2,
                        int masterVolume, int[] trackVolumes, int[] trackPans, int samples) {

        MedSong toSong() {
            return new MedSong(playSeq, tempo == 0 ? DEFAULT_TEMPO : tempo, speed == 0 ? DEFAULT_SPEED : speed,
                    transpose, flags, flags2, masterVolume == 0 ? FULL_VOLUME : masterVolume, trackVolumes,
                    trackPans);
        }
    }

    private record Expansion(String name, String annotation, Hold[] holds, String[] names, int holdSize) {

        static final Expansion NONE = new Expansion("", "", new Hold[0], new String[0], 0);

        Hold holdOf(int slot) {
            return slot < holds.length ? holds[slot] : Hold.NONE;
        }

        String nameOf(int slot) {
            return slot < names.length ? names[slot] : "";
        }
    }
}

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
 * The DBM0 format is read the way loader.c of libdigibooster3 reads it,
 * Copyright © 2014 Grzegorz Kraszewski, licensed under the two-clause BSD
 * licence and used here under the GNU General Public License.
 */

package com.adeptum.paula.module.digibooster;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads a DigiBooster module, a run of big-endian chunks behind a {@code DBM0} header. Everything the chunks
 * hold is checked against what the module said about itself in its INFO chunk, so the player can trust its
 * indices.
 */
final class DbmReader {

    private static final String MAGIC = "DBM0";
    private static final int HEADER_LENGTH = 8;
    private static final int CHUNK_HEADER_LENGTH = 8;
    private static final int ID_LENGTH = 4;
    private static final String NAME = "NAME";
    private static final String INFO = "INFO";
    private static final String SONG = "SONG";
    private static final String INST = "INST";
    private static final String PATT = "PATT";
    private static final String SMPL = "SMPL";
    private static final String VENV = "VENV";
    private static final String PENV = "PENV";
    private static final String DSPE = "DSPE";
    private static final int MODULE_NAME_LENGTH = 44;
    private static final int SONG_NAME_LENGTH = 44;
    private static final int INSTRUMENT_NAME_LENGTH = 30;
    private static final int MAX_COUNT = 255;
    private static final int MAX_TRACKS = 254;
    private static final int MAX_ENVELOPE_POSITION = 2048;
    private static final int MAX_VOLUME = 64;
    private static final int MAX_PANNING = 128;
    private static final int MIN_C3_FREQUENCY = 2000;
    private static final int MAX_C3_FREQUENCY = 192000;
    private static final int ENVELOPE_ENABLED = 0x01;
    private static final int ENVELOPE_SUSTAIN_A = 0x02;
    private static final int ENVELOPE_LOOP = 0x04;
    private static final int ENVELOPE_SUSTAIN_B = 0x08;
    private static final int HAVE_NOTE = 0x01;
    private static final int HAVE_INSTRUMENT = 0x02;
    private static final int HAVE_COMMAND_1 = 0x04;
    private static final int HAVE_PARAMETER_1 = 0x08;
    private static final int HAVE_COMMAND_2 = 0x10;
    private static final int HAVE_PARAMETER_2 = 0x20;
    private static final int ECHO_OFF = 0;
    private static final int PANNING_SCALE_DIGIBOOSTER_2 = 2;
    private static final int BYTE_BITS = 8;

    private final byte[] file;
    private int position;
    private int chunkEnd;
    private boolean informed;

    private String name = "";
    private int creatorVersion;
    private int creatorRevision;
    private int tracks;
    private int instrumentCount;
    private int sampleCount;
    private int songCount;
    private int patternCount;
    private final List<DbmSong> songs = new ArrayList<>();
    private final List<DbmInstrument> instruments = new ArrayList<>();
    private final List<short[]> samples = new ArrayList<>();
    private final List<DbmPattern> patterns = new ArrayList<>();
    private final List<DbmEnvelope> volumeEnvelopes = new ArrayList<>();
    private final List<DbmEnvelope> panningEnvelopes = new ArrayList<>();
    private DbmEcho echo;

    private DbmReader(byte[] file) {
        this.file = file;
        this.chunkEnd = file.length;
    }

    static boolean isDbm(byte[] head) {
        return head.length >= ID_LENGTH && MAGIC.equals(new String(head, 0, ID_LENGTH, StandardCharsets.US_ASCII));
    }

    static DbmFile read(byte[] file) throws IOException {
        return new DbmReader(file).module();
    }

    private DbmFile module() throws IOException {
        readHeader();
        while (position + CHUNK_HEADER_LENGTH <= file.length) {
            readChunk();
        }
        return new DbmFile(name, creatorVersion, creatorRevision, tracks, verifiedSongs(), verifiedInstruments(),
                samples, verifiedPatterns(), volumeEnvelopes, panningEnvelopes, echo == null ? DbmEcho.off(tracks) : echo);
    }

    private void readHeader() throws IOException {
        if (file.length < HEADER_LENGTH || !isDbm(file)) {
            throw corrupt("not a DigiBooster module");
        }
        position = ID_LENGTH;
        creatorVersion = unsigned8();
        creatorRevision = binaryCodedDecimal(unsigned8());
        position = HEADER_LENGTH;
        if (creatorVersion != DbmFile.DIGIBOOSTER_2 && creatorVersion != DbmFile.DIGIBOOSTER_3) {
            throw new IOException("Unsupported DigiBooster version " + creatorVersion);
        }
    }

    private void readChunk() throws IOException {
        final String id = ascii(ID_LENGTH);
        final int size = signed32();
        if (size < 0 || position + size > file.length) {
            throw corrupt("chunk " + id + " runs past the end of the file");
        }
        final int start = position;
        chunkEnd = start + size;
        readChunkBody(id);
        position = chunkEnd;
        chunkEnd = file.length;
    }

    private void readChunkBody(String id) throws IOException {
        switch (id) {
            case NAME -> name = string(MODULE_NAME_LENGTH);
            case INFO -> readInfo();
            case SONG -> readSongs();
            case INST -> readInstruments();
            case PATT -> readPatterns();
            case SMPL -> readSamples();
            case VENV -> volumeEnvelopes.addAll(readEnvelopes(false));
            case PENV -> panningEnvelopes.addAll(readEnvelopes(true));
            case DSPE -> readEcho();
            default -> { }
        }
    }

    private void readInfo() throws IOException {
        instrumentCount = counted(unsigned16(), "instruments");
        sampleCount = counted(unsigned16(), "samples");
        songCount = counted(unsigned16(), "songs");
        patternCount = unsigned16();
        tracks = unsigned16();
        if (patternCount == 0) {
            throw corrupt("no patterns");
        }
        if (tracks == 0 || tracks > MAX_TRACKS || (tracks & 1) == 1) {
            throw corrupt(tracks + " tracks");
        }
        informed = true;
    }

    private void readSongs() throws IOException {
        requireInfo(SONG);
        for (int song = 0; song < songCount; song++) {
            final String songName = string(SONG_NAME_LENGTH);
            final int[] playList = new int[unsigned16()];
            for (int order = 0; order < playList.length; order++) {
                playList[order] = unsigned16();
            }
            songs.add(new DbmSong(songName, playList));
        }
    }

    private void readInstruments() throws IOException {
        requireInfo(INST);
        for (int instrument = 0; instrument < instrumentCount; instrument++) {
            instruments.add(readInstrument());
        }
    }

    /**
     * Samples are numbered from one in the file; an instrument that names none is left pointing before the
     * first sample and picks up the empty sample when the module is verified.
     */
    private DbmInstrument readInstrument() throws IOException {
        final String instrumentName = string(INSTRUMENT_NAME_LENGTH);
        final int sample = unsigned16() - 1;
        final int volume = unsigned16();
        final int c3Frequency = signed32();
        final int loopStart = signed32();
        final int loopLength = signed32();
        final int panning = signed16();
        final int flags = unsigned16();
        final int looping = loopLength == 0 ? flags & ~DbmInstrument.LOOP_MASK : flags;
        final boolean loops = (looping & DbmInstrument.LOOP_MASK) != DbmInstrument.NO_LOOP;
        return new DbmInstrument(instrumentName, sample, volume, panning, c3Frequency, loops ? loopStart : 0,
                loops ? loopLength : 0, looping, DbmEnvelope.NONE, DbmEnvelope.NONE);
    }

    private void readPatterns() throws IOException {
        requireInfo(PATT);
        for (int pattern = 0; pattern < patternCount; pattern++) {
            patterns.add(readPattern());
        }
    }

    /**
     * Pattern data is packed as a stream of track numbers, each followed by the fields the entry actually holds;
     * a track number of zero ends the row. A stream that stops in the middle of an entry leaves the rest of the
     * pattern empty, the way the players of the day read it.
     */
    private DbmPattern readPattern() throws IOException {
        final int rows = unsigned16();
        final int packedLength = signed32();
        if (rows == 0 || packedLength <= 0) {
            throw corrupt("empty pattern");
        }
        final int packedEnd = position + packedLength;
        if (packedEnd > chunkEnd) {
            throw corrupt("pattern data runs past its chunk");
        }
        final DbmEntry[] entries = new DbmEntry[rows * tracks];
        Arrays.fill(entries, DbmEntry.EMPTY);
        int row = 0;
        while (position < packedEnd && row < rows) {
            final int track = unsigned8();
            if (track == 0) {
                row++;
            } else if (track > tracks) {
                throw corrupt("track " + track + " in a " + tracks + " track module");
            } else {
                entries[row * tracks + track - 1] = readEntry(packedEnd);
            }
        }
        position = packedEnd;
        return new DbmPattern(rows, entries);
    }

    private DbmEntry readEntry(int packedEnd) throws IOException {
        final int fields = packed(packedEnd);
        final int noteAndOctave = (fields & HAVE_NOTE) == 0 ? 0 : packed(packedEnd);
        final int instrument = (fields & HAVE_INSTRUMENT) == 0 ? 0 : packed(packedEnd);
        final int command1 = (fields & HAVE_COMMAND_1) == 0 ? 0 : packed(packedEnd);
        final int parameter1 = (fields & HAVE_PARAMETER_1) == 0 ? 0 : packed(packedEnd);
        final int command2 = (fields & HAVE_COMMAND_2) == 0 ? 0 : packed(packedEnd);
        final int parameter2 = (fields & HAVE_PARAMETER_2) == 0 ? 0 : packed(packedEnd);
        return new DbmEntry(noteAndOctave >> 4, noteAndOctave & 0x0F, instrument, command1, parameter1, command2, parameter2);
    }

    private int packed(int packedEnd) throws IOException {
        return position < packedEnd ? unsigned8() : 0;
    }

    private void readSamples() throws IOException {
        requireInfo(SMPL);
        for (int sample = 0; sample < sampleCount; sample++) {
            samples.add(readSample());
        }
    }

    /**
     * Samples come as 8, 16 or 32 bit frames and are all kept as 16 bit ones.
     */
    private short[] readSample() throws IOException {
        skip(3);
        final int width = unsigned8() & 0x07;
        final int frames = signed32();
        if (frames < 0) {
            throw corrupt("a sample of " + frames + " frames");
        }
        final short[] data = new short[frames];
        for (int frame = 0; frame < frames; frame++) {
            data[frame] = switch (width) {
                case 1 -> (short) (signed8() << BYTE_BITS);
                case 2 -> (short) signed16();
                case 4 -> (short) (signed32() >> Short.SIZE);
                default -> throw corrupt(width + " bytes per sample frame");
            };
        }
        return data;
    }

    private List<DbmEnvelope> readEnvelopes(boolean panning) throws IOException {
        requireInfo(panning ? PENV : VENV);
        final int count = unsigned16();
        if (count > MAX_COUNT) {
            throw corrupt(count + " envelopes");
        }
        final List<DbmEnvelope> envelopes = new ArrayList<>(count);
        for (int envelope = 0; envelope < count; envelope++) {
            envelopes.add(readEnvelope(panning));
        }
        return envelopes;
    }

    /**
     * The points of a switched off envelope are left at zero, and its loop and sustain points unset, whatever
     * the file holds for them. Where both sustain points are set, the lower one comes first.
     */
    private DbmEnvelope readEnvelope(boolean panning) throws IOException {
        final int instrument = unsigned16();
        final int flags = unsigned8();
        final int sections = Math.min(unsigned8(), DbmEnvelope.MAX_SECTIONS);
        final int enteredSustainA = unsigned8();
        final int enteredLoopFirst = unsigned8();
        final int enteredLoopLast = unsigned8();
        final int enteredSustainB = unsigned8();
        final boolean enabled = (flags & ENVELOPE_ENABLED) != 0;
        final int sustainA = enabled && (flags & ENVELOPE_SUSTAIN_A) != 0 ? point(enteredSustainA, sections) : DbmEnvelope.NONE;
        final int sustainB = enabled && (flags & ENVELOPE_SUSTAIN_B) != 0 ? point(enteredSustainB, sections) : DbmEnvelope.NONE;
        final boolean loops = enabled && (flags & ENVELOPE_LOOP) != 0;
        if (loops && (enteredLoopLast > sections || enteredLoopFirst >= enteredLoopLast)) {
            throw corrupt("an envelope looping from " + enteredLoopFirst + " to " + enteredLoopLast);
        }
        final boolean sustained = sustainA != DbmEnvelope.NONE && sustainB != DbmEnvelope.NONE;
        final int[] positions = new int[DbmEnvelope.MAX_SECTIONS + 1];
        final int[] values = new int[DbmEnvelope.MAX_SECTIONS + 1];
        for (int point = 0; point < positions.length; point++) {
            final int at = signed16();
            final int value = envelopeValue(signed16(), panning);
            if (enabled && point <= sections) {
                positions[point] = envelopePosition(at);
                values[point] = checked(value, panning);
            }
        }
        return new DbmEnvelope(instrument < 1 || instrument > MAX_COUNT ? DbmEnvelope.NONE : instrument, sections,
                loops ? enteredLoopFirst : DbmEnvelope.NONE, loops ? enteredLoopLast : DbmEnvelope.NONE,
                sustained ? Math.min(sustainA, sustainB) : sustainA, sustained ? Math.max(sustainA, sustainB) : sustainB,
                positions, values);
    }

    private int envelopeValue(int value, boolean panning) {
        return panning && creatorVersion == DbmFile.DIGIBOOSTER_2 ? (value << PANNING_SCALE_DIGIBOOSTER_2) - MAX_PANNING : value;
    }

    private int envelopePosition(int at) throws IOException {
        if (at < 0 || at > MAX_ENVELOPE_POSITION) {
            throw corrupt("an envelope point at " + at);
        }
        return at;
    }

    private int checked(int value, boolean panning) throws IOException {
        final int lowest = panning ? -MAX_PANNING : 0;
        final int highest = panning ? MAX_PANNING : MAX_VOLUME;
        if (value < lowest || value > highest) {
            throw corrupt("an envelope value of " + value);
        }
        return value;
    }

    private int point(int point, int sections) throws IOException {
        if (point > sections) {
            throw corrupt("an envelope point " + point + " beyond its " + sections + " sections");
        }
        return point;
    }

    /**
     * A track is echoed when its mask byte is clear.
     */
    private void readEcho() throws IOException {
        requireInfo(DSPE);
        final int masked = unsigned16();
        if (masked != tracks) {
            throw corrupt("an echo mask for " + masked + " of " + tracks + " tracks");
        }
        final boolean[] echoed = new boolean[tracks];
        for (int track = 0; track < tracks; track++) {
            echoed[track] = unsigned8() == ECHO_OFF;
        }
        echo = new DbmEcho(level(), level(), level(), level(), echoed);
    }

    /**
     * The echo levels are stored a word each, of which only the low byte carries the level.
     */
    private int level() throws IOException {
        skip(1);
        return unsigned8();
    }

    private List<DbmSong> verifiedSongs() throws IOException {
        if (songs.size() != songCount) {
            throw corrupt(songs.size() + " of " + songCount + " songs");
        }
        for (final DbmSong song : songs) {
            for (final int pattern : song.playList()) {
                if (pattern >= patternCount) {
                    throw corrupt("a playlist entry for pattern " + pattern);
                }
            }
        }
        return songs;
    }

    private List<DbmPattern> verifiedPatterns() throws IOException {
        if (patterns.size() != patternCount) {
            throw corrupt(patterns.size() + " of " + patternCount + " patterns");
        }
        return patterns;
    }

    private List<DbmInstrument> verifiedInstruments() throws IOException {
        if (instruments.size() != instrumentCount || samples.size() != sampleCount) {
            throw corrupt(instruments.size() + " instruments and " + samples.size() + " samples");
        }
        final int[] volumeEnvelope = envelopeOfInstrument(volumeEnvelopes);
        final int[] panningEnvelope = envelopeOfInstrument(panningEnvelopes);
        final List<DbmInstrument> verified = new ArrayList<>(instrumentCount);
        for (int instrument = 0; instrument < instrumentCount; instrument++) {
            verified.add(withSample(instruments.get(instrument)).withEnvelopes(volumeEnvelope[instrument], panningEnvelope[instrument]));
        }
        return verified;
    }

    /**
     * Modules in the wild point instruments at samples that are not there; they play the empty sample instead.
     */
    private DbmInstrument withSample(DbmInstrument instrument) throws IOException {
        final int sample = instrument.sample() < 0 || instrument.sample() >= sampleCount ? 0 : instrument.sample();
        final int frames = samples.get(sample).length;
        if (instrument.c3Frequency() < MIN_C3_FREQUENCY || instrument.c3Frequency() > MAX_C3_FREQUENCY) {
            throw corrupt("an instrument tuned to " + instrument.c3Frequency() + " Hz");
        }
        if (frames == 0) {
            return instrument.withSampleAndLoop(sample, 0, 0, instrument.flags() & ~DbmInstrument.LOOP_MASK);
        }
        if (instrument.loopStart() >= frames || instrument.loopStart() + instrument.loopLength() > frames) {
            throw corrupt("a loop past the end of a " + frames + " frame sample");
        }
        return instrument.withSampleAndLoop(sample, instrument.loopStart(), instrument.loopLength(), instrument.flags());
    }

    private int[] envelopeOfInstrument(List<DbmEnvelope> envelopes) throws IOException {
        final int[] of = new int[instrumentCount];
        Arrays.fill(of, DbmEnvelope.NONE);
        for (int envelope = 0; envelope < envelopes.size(); envelope++) {
            final int instrument = envelopes.get(envelope).instrument();
            if (instrument == DbmEnvelope.NONE) {
                continue;
            }
            if (instrument > instrumentCount) {
                throw corrupt("an envelope for instrument " + instrument);
            }
            of[instrument - 1] = envelope;
        }
        return of;
    }

    private void requireInfo(String chunk) throws IOException {
        if (!informed) {
            throw corrupt("a " + chunk + " chunk before the module said what it holds");
        }
    }

    private int counted(int count, String what) throws IOException {
        if (count == 0 || count > MAX_COUNT) {
            throw corrupt(count + " " + what);
        }
        return count;
    }

    private static int binaryCodedDecimal(int digits) {
        return (digits >> 4) * 10 + (digits & 0x0F);
    }

    private String ascii(int length) throws IOException {
        return new String(take(length), StandardCharsets.US_ASCII);
    }

    /**
     * Names are padded to their field with zeroes, but a name filling the field has none.
     */
    private String string(int length) throws IOException {
        final byte[] bytes = take(length);
        int end = 0;
        while (end < length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, StandardCharsets.ISO_8859_1);
    }

    private void skip(int length) throws IOException {
        require(length);
        position += length;
    }

    private byte[] take(int length) throws IOException {
        require(length);
        final byte[] bytes = Arrays.copyOfRange(file, position, position + length);
        position += length;
        return bytes;
    }

    private int unsigned8() throws IOException {
        require(1);
        return file[position++] & 0xFF;
    }

    private int signed8() throws IOException {
        require(1);
        return file[position++];
    }

    private int unsigned16() throws IOException {
        return (unsigned8() << BYTE_BITS) | unsigned8();
    }

    private int signed16() throws IOException {
        return (short) unsigned16();
    }

    private int signed32() throws IOException {
        return (unsigned16() << Short.SIZE) | unsigned16();
    }

    private void require(int length) throws IOException {
        if (position + length > chunkEnd || position + length > file.length) {
            throw corrupt("a chunk that ends in the middle of its contents");
        }
    }

    private static IOException corrupt(String what) {
        return new IOException("Corrupt DigiBooster module: " + what);
    }
}

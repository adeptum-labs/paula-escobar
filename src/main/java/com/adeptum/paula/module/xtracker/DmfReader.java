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
 * The layout and the sample packing follow Load_dmf.cpp of OpenMPT,
 * Copyright © 2004-2026 the OpenMPT project developers and Copyright ©
 * 1997-2003 Olivier Lapicque, licensed under the three-clause BSD licence
 * and used here under the GNU General Public License.
 */

package com.adeptum.paula.module.xtracker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an X-Tracker module: a 66-byte header, then chunks of a four-letter name and a length, of which the
 * order list, the patterns, the sample headers and the sample data are read. Early versions of the tracker wrote
 * some lengths wrong, and those are put right the way OpenMPT puts them right.
 */
final class DmfReader {

    private static final byte[] MAGIC = {'D', 'D', 'M', 'F'};
    private static final int HEADER_LENGTH = 66;
    private static final int TRACKER_LENGTH = 8;
    private static final int TITLE_LENGTH = 30;
    private static final int COMPOSER_LENGTH = 20;
    private static final int DATE_LENGTH = 3;
    private static final int LAST_VERSION = 10;
    private static final int ID_LENGTH = 4;
    private static final int CHUNK_HEADER = 8;
    private static final String SEQUENCE = "SEQU";
    private static final String PATTERNS = "PATT";
    private static final String SAMPLE_INFO = "SMPI";
    private static final String SAMPLE_DATA = "SMPD";
    private static final int THIRD_VERSION_SEQUENCE_GAP = 2;
    private static final int FOURTH_VERSION_SEQUENCE_GAP = 4;
    private static final int FIRST_LOOP_START_VERSION = 3;
    private static final int FIRST_LOOP_END_VERSION = 4;
    private static final int FIRST_RELIABLE_SAMPLE_DATA_VERSION = 8;
    private static final int MAX_TRACKS = 32;
    private static final int PATTERN_LIST_HEADER = 3;
    private static final int OLD_PATTERN_HEADER = 9;
    private static final int PATTERN_HEADER = 8;
    private static final int OLD_PATTERN_HEADER_GAP = 2;
    private static final int FIRST_PLAIN_PATTERN_HEADER_VERSION = 3;
    private static final int FIRST_BEAT_VERSION = 6;
    private static final int BEAT_SHIFT = 4;
    private static final int COUNTER = 0x80;
    private static final int GLOBAL_COMMAND = 0x3F;
    private static final int INSTRUMENT = 0x40;
    private static final int NOTE = 0x20;
    private static final int VOLUME = 0x10;
    private static final int INSTRUMENT_EFFECT = 0x08;
    private static final int NOTE_EFFECT = 0x04;
    private static final int VOLUME_EFFECT = 0x02;
    private static final int ENTRY_FIELDS = 0x7E;
    private static final int FIRST_NAMED_LENGTH_VERSION = 2;
    private static final int OLD_NAME_LENGTH = 30;
    private static final int SAMPLE_HEADER = 16;
    private static final int FIRST_LIBRARY_NAME_VERSION = 8;
    private static final int LIBRARY_NAME_LENGTH = 8;
    private static final int FILLER = 6;
    private static final int FIRST_VERSION_FILLER = 2;
    private static final long LONGEST_SAMPLE = 0x4000000;
    private static final int LOOPED = 0x01;
    private static final int SIXTEEN_BIT = 0x02;
    private static final int PACKING = 0x0C;
    private static final int HUFFMAN = 0x04;
    private static final int SMALLEST_PACKED_BLOCK = 5;
    private static final int BYTE = 0xFF;
    private static final int WORD = 0xFFFF;

    private DmfReader() {
    }

    static DmfFile read(byte[] bytes) throws IOException {
        final ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        require(in, HEADER_LENGTH);
        final byte[] magic = bytes(in, MAGIC.length);
        final int version = unsigned(in.get());
        if (!Arrays.equals(magic, MAGIC) || version < 1 || version > LAST_VERSION) {
            throw new IOException("not an X-Tracker module");
        }
        skip(in, TRACKER_LENGTH);
        final String title = DmfText.decode(bytes(in, TITLE_LENGTH));
        final String composer = DmfText.decode(bytes(in, COMPOSER_LENGTH));
        skip(in, DATE_LENGTH);
        final Map<String, ByteBuffer> chunks = chunks(in, version);
        final ByteBuffer patternChunk = chunks.get(PATTERNS);
        if (patternChunk == null) {
            throw new IOException("the module has no patterns");
        }
        require(patternChunk, PATTERN_LIST_HEADER);
        final int patternCount = patternChunk.getShort() & WORD;
        final int tracks = unsigned(patternChunk.get());
        if (tracks < 1 || tracks > MAX_TRACKS) {
            throw new IOException("more tracks than X-Tracker has, or none");
        }
        final List<DmfPattern> patterns = new ArrayList<>(patternCount);
        for (int number = 0; number < patternCount; number++) {
            patterns.add(pattern(patternChunk, version, tracks));
        }
        final int[] orders = orders(chunks.get(SEQUENCE), version, patternCount);
        final List<DmfSample> samples = samples(chunks.get(SAMPLE_INFO), chunks.get(SAMPLE_DATA), version);
        return new DmfFile(version, title, composer, tracks, orders, patterns, samples);
    }

    /**
     * The chunks by name, each a view of its own bytes, the first of a name kept. A chunk running past the file
     * is cut at its end; a closing mark too short to carry a length ends the walk.
     */
    private static Map<String, ByteBuffer> chunks(ByteBuffer in, int version) throws IOException {
        final Map<String, ByteBuffer> chunks = new HashMap<>();
        while (in.remaining() >= CHUNK_HEADER) {
            final String id = new String(bytes(in, ID_LENGTH), StandardCharsets.ISO_8859_1);
            final long declared = Integer.toUnsignedLong(in.getInt());
            final long length = id.equals(SAMPLE_DATA) && version < FIRST_RELIABLE_SAMPLE_DATA_VERSION
                    ? in.remaining() : declared;
            final int taken = (int) Math.min(length, in.remaining());
            chunks.putIfAbsent(id, in.slice(in.position(), taken).order(ByteOrder.LITTLE_ENDIAN));
            in.position(Math.min(in.limit(), in.position() + taken + sequenceGap(id, version)));
        }
        return chunks;
    }

    /**
     * Versions three and four added loop points to the order list without counting them in its length.
     */
    private static int sequenceGap(String id, int version) {
        if (!id.equals(SEQUENCE)) {
            return 0;
        }
        return version == FIRST_LOOP_START_VERSION ? THIRD_VERSION_SEQUENCE_GAP
                : version == FIRST_LOOP_END_VERSION ? FOURTH_VERSION_SEQUENCE_GAP : 0;
    }

    private static int[] orders(ByteBuffer sequence, int version, int patterns) throws IOException {
        if (sequence == null) {
            throw new IOException("the module has no order list");
        }
        skip(sequence, version >= FIRST_LOOP_END_VERSION ? Short.BYTES * 2
                : version >= FIRST_LOOP_START_VERSION ? Short.BYTES : 0);
        final int[] orders = new int[sequence.remaining() / Short.BYTES];
        for (int at = 0; at < orders.length; at++) {
            orders[at] = sequence.getShort() & WORD;
            if (orders[at] >= patterns) {
                throw new IOException("an order names a pattern the module lacks");
            }
        }
        if (orders.length == 0) {
            throw new IOException("the order list is empty");
        }
        return orders;
    }

    /**
     * A pattern's header and its packed rows. A pattern declaring more tracks than the song has is read for the
     * song's tracks only, as OpenMPT reads it.
     */
    private static DmfPattern pattern(ByteBuffer chunk, int version, int songTracks) throws IOException {
        final boolean old = version < FIRST_PLAIN_PATTERN_HEADER_VERSION;
        require(chunk, old ? OLD_PATTERN_HEADER : PATTERN_HEADER);
        final int tracks = Math.min(unsigned(chunk.get()), songTracks);
        final int storedBeat;
        if (old) {
            skip(chunk, OLD_PATTERN_HEADER_GAP);
            storedBeat = 0;
        } else {
            storedBeat = unsigned(chunk.get()) >> BEAT_SHIFT;
        }
        final int rows = Math.max(1, chunk.getShort() & WORD);
        final long length = Integer.toUnsignedLong(chunk.getInt());
        if (length > chunk.remaining()) {
            throw new IOException("a pattern runs past its chunk");
        }
        final ByteBuffer data = chunk.slice(chunk.position(), (int) length);
        chunk.position(chunk.position() + (int) length);
        return rows(data, tracks, rows, version < FIRST_BEAT_VERSION ? 0 : storedBeat);
    }

    /**
     * Every track, the global one first, counts down the rows it passes over before its next entry. Data that
     * runs out in the middle of a row reads as zeros, and the rows after it are empty.
     */
    private static DmfPattern rows(ByteBuffer data, int tracks, int rows, int beat) {
        final DmfGlobalEntry[] global = new DmfGlobalEntry[rows];
        final DmfTrackEntry[][] entries = new DmfTrackEntry[rows][tracks];
        final int[] passing = new int[tracks + 1];
        for (int row = 0; row < rows && data.hasRemaining(); row++) {
            if (passing[0] > 0) {
                passing[0]--;
            } else {
                global[row] = globalEntry(data, passing);
            }
            for (int track = 0; track < tracks; track++) {
                if (passing[track + 1] > 0) {
                    passing[track + 1]--;
                } else {
                    entries[row][track] = trackEntry(data, passing, track + 1);
                }
            }
        }
        return new DmfPattern(tracks, beat, global, entries);
    }

    private static DmfGlobalEntry globalEntry(ByteBuffer data, int[] passing) {
        final int info = next(data);
        if ((info & COUNTER) != 0) {
            passing[0] = next(data);
        }
        final int command = info & GLOBAL_COMMAND;
        return command == 0 ? null : new DmfGlobalEntry(command, next(data));
    }

    private static DmfTrackEntry trackEntry(ByteBuffer data, int[] passing, int counter) {
        final int info = next(data);
        if ((info & COUNTER) != 0) {
            passing[counter] = next(data);
        }
        if ((info & ENTRY_FIELDS) == 0) {
            return null;
        }
        final int instrument = field(data, info, INSTRUMENT);
        final int note = field(data, info, NOTE);
        final int volume = field(data, info, VOLUME);
        final int instrumentEffect = field(data, info, INSTRUMENT_EFFECT);
        final int instrumentData = (info & INSTRUMENT_EFFECT) != 0 ? next(data) : 0;
        final int noteEffect = field(data, info, NOTE_EFFECT);
        final int noteData = (info & NOTE_EFFECT) != 0 ? next(data) : 0;
        final int volumeEffect = field(data, info, VOLUME_EFFECT);
        final int volumeData = (info & VOLUME_EFFECT) != 0 ? next(data) : 0;
        return new DmfTrackEntry(instrument, note, volume, instrumentEffect, instrumentData, noteEffect, noteData,
                volumeEffect, volumeData);
    }

    private static int field(ByteBuffer data, int info, int flag) {
        return (info & flag) != 0 ? next(data) : DmfTrackEntry.NONE;
    }

    private static int next(ByteBuffer data) {
        return data.hasRemaining() ? unsigned(data.get()) : 0;
    }

    private static List<DmfSample> samples(ByteBuffer info, ByteBuffer data, int version) throws IOException {
        if (info == null) {
            return List.of();
        }
        require(info, 1);
        final int count = unsigned(info.get());
        final List<DmfSample> samples = new ArrayList<>(count);
        for (int number = 0; number < count; number++) {
            samples.add(sample(info, data, version));
        }
        return samples;
    }

    private static DmfSample sample(ByteBuffer info, ByteBuffer data, int version) throws IOException {
        require(info, 1);
        final int nameLength = version < FIRST_NAMED_LENGTH_VERSION ? OLD_NAME_LENGTH : unsigned(info.get());
        final String name = DmfText.decode(bytes(info, nameLength));
        require(info, SAMPLE_HEADER);
        final long length = Integer.toUnsignedLong(info.getInt());
        final long loopStart = Integer.toUnsignedLong(info.getInt());
        final long loopEnd = Integer.toUnsignedLong(info.getInt());
        final int c3Frequency = info.getShort() & WORD;
        final int volume = unsigned(info.get());
        final int flags = unsigned(info.get());
        if (length > LONGEST_SAMPLE) {
            throw new IOException("a sample longer than any X-Tracker wrote");
        }
        skip(info, (version >= FIRST_LIBRARY_NAME_VERSION ? LIBRARY_NAME_LENGTH : 0)
                + (version >= FIRST_NAMED_LENGTH_VERSION ? FILLER : FIRST_VERSION_FILLER));
        final boolean sixteenBit = (flags & SIXTEEN_BIT) != 0;
        final int width = sixteenBit ? Short.BYTES : 1;
        final short[] sound = sound(stored(block(data), flags, (int) length), sixteenBit, (int) (length / width));
        final int end = (flags & LOOPED) != 0 ? (int) Math.min(loopEnd / width, sound.length) : 0;
        final int start = (int) Math.min(loopStart / width, end);
        return new DmfSample(name, sound, start, end, c3Frequency, volume, sixteenBit);
    }

    /**
     * The next sample's block from the data chunk; a module whose data chunk has run out leaves its last samples
     * without sound.
     */
    private static byte[] block(ByteBuffer data) throws IOException {
        if (data == null || data.remaining() < Integer.BYTES) {
            return new byte[0];
        }
        final long length = Integer.toUnsignedLong(data.getInt());
        if (length > data.remaining()) {
            throw new IOException("a sample's data runs past its chunk");
        }
        return bytes(data, (int) length);
    }

    /**
     * The sample's bytes: as stored, unpacked, or none for the packings nothing in X-Tracker 1.03 writes.
     */
    private static byte[] stored(byte[] block, int flags, int length) {
        return switch (flags & PACKING) {
            case 0 -> block;
            case HUFFMAN -> block.length >= SMALLEST_PACKED_BLOCK ? DmfUnpacker.unpack(block, length) : new byte[0];
            default -> new byte[0];
        };
    }

    private static short[] sound(byte[] bytes, boolean sixteenBit, int frames) {
        final short[] sound = new short[frames];
        for (int frame = 0; frame < frames; frame++) {
            if (sixteenBit) {
                final int at = frame * Short.BYTES;
                sound[frame] = at + 1 < bytes.length ? (short) (bytes[at] & BYTE | bytes[at + 1] << Byte.SIZE) : 0;
            } else {
                sound[frame] = frame < bytes.length ? (short) (bytes[frame] << Byte.SIZE) : 0;
            }
        }
        return sound;
    }

    private static byte[] bytes(ByteBuffer in, int count) throws IOException {
        require(in, count);
        final byte[] bytes = new byte[count];
        in.get(bytes);
        return bytes;
    }

    private static void skip(ByteBuffer in, int count) throws IOException {
        require(in, count);
        in.position(in.position() + count);
    }

    private static void require(ByteBuffer in, int count) throws IOException {
        if (in.remaining() < count) {
            throw new IOException("the file is cut short");
        }
    }

    private static int unsigned(byte value) {
        return value & BYTE;
    }
}

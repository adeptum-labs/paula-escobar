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

package com.adeptum.paula.testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Builds minimal but valid modules to play in tests: a four-channel ProTracker module and a two-track
 * DigiBooster Pro one, each with a single looping square-wave sample, one pattern and one note, and an
 * AHX and a HivelyTracker module playing one synthesised square instead, the ProTracker one again
 * as an MO3, and a Composer 669 module of two patterns.
 */
public final class TestModules {

    public static final String TITLE = "Paula Test";
    public static final String SAMPLE_NAME = "square";

    public static final String SONG_NAME = "Paula Song";
    public static final int C3_FREQUENCY = 8363;
    public static final int DBM_TRACKS = 2;
    public static final int DBM_ROWS = 4;
    public static final int DBM_ORDERS = 2;
    public static final int ECHO_DELAY = 0x30;
    public static final int ECHO_FEEDBACK = 0x60;
    public static final int ECHO_MIX = 0x70;
    public static final int ECHO_CROSS = 0x80;

    public static final String HIVELY_TITLE = "Paula Hively";
    public static final String HIVELY_INSTRUMENT = "square";
    public static final int HIVELY_POSITIONS = 1;
    public static final int HIVELY_TRACK_LENGTH = 4;
    public static final int HIVELY_CHANNELS = 8;
    public static final int HIVELY_MIX_GAIN = 100;
    public static final int HIVELY_STEREO = 2;

    public static final String FLEX_MARK = "FLEX";
    public static final int FLEX_BLOCK_LENGTH = 152;
    public static final int FLEX_CHANNELS = 8;

    /**
     * Where each of the six settings begins, in the order the effects are named in the tracker: the reverb's
     * decay and level, then the delay's decay, time, ping-pong and level.
     */
    public static final int[] FLEX_EFFECTS_AT = {124, 132, 136, 140, 144, 148};

    public static final int FLEX_REVERB_DECAY = 40;
    public static final int FLEX_REVERB_LEVEL = 63;
    public static final int FLEX_DELAY_DECAY = 35;
    public static final int FLEX_DELAY_TIME = 50;
    public static final int FLEX_DELAY_PING_PONG = 64;
    public static final int FLEX_DELAY_LEVEL = 38;

    private static final int FLEX_REVERB_DECAY_RECORD_AT = 124;
    private static final int FLEX_VALUE_IN_RECORD = 3;
    private static final int FLEX_SAMPLES = 31;
    private static final int FLEX_SAMPLE_ENTRY_LENGTH = 30;
    private static final int FLEX_FIRST_SAMPLE_LENGTH_AT = 20 + 22;
    private static final String FLEX_MARK_8CHN = "8CHN";
    private static final int ROWS = 64;
    private static final int BYTES_PER_NOTE = 4;

    private static final int HEADER_LENGTH = 1084;
    private static final int PATTERN_LENGTH = 64 * 4 * 4;
    private static final int SAMPLE_LENGTH = 64;
    private static final int LONG_SAMPLE_LENGTH = 1000;
    private static final int SHORT_LOOP_LENGTH = 256;
    private static final int ROW_LENGTH = 16;
    private static final int PERIOD_C2 = 428;
    private static final int DBM_LENGTH = 1024;
    public static final int MED_PLAY_SEQUENCE_AT = 52 + 63 * 8 + 4;
    public static final int MED_TRACKS = 2;
    public static final int MED_LINES = 4;
    public static final int MED_NOTE = 13;
    public static final int MED_VOLUME = 64;
    public static final int MED_TEMPO = 33;
    public static final int MED_SPEED = 6;
    public static final int MED_MASTER_VOLUME = 64;

    private static final int MED_HEADER_LENGTH = 52;
    private static final int MED_EXPANSION_LENGTH = 52;
    private static final int MED_SAMPLE_LENGTH = 64;

    private static final int AHX_HEADER_LENGTH = 14;
    private static final int HVL_HEADER_LENGTH = 16;
    private static final int BLANK_FIRST_TRACK = 0x80;
    private static final int HIVELY_TRACK_COUNT = 1;
    private static final int HIVELY_INSTRUMENTS = 1;
    private static final int HIVELY_SUBSONGS = 0;
    private static final int HVL_EMPTY_STEP = 0x3f;

    public static final int MO3_CHANNELS = 4;
    public static final int MO3_ORDERS = 2;
    public static final int MO3_ROWS = 64;
    public static final int MO3_SAMPLES = 1;
    public static final int MO3_SPEED = 6;
    public static final int MO3_TEMPO = 125;

    /**
     * Where the channel count sits in the music chunk: after the song name and the empty message, each of
     * which ends in a zero. The order count is the word after it and the restart position the word after that.
     */
    public static final int MO3_CHANNELS_AT = TITLE.length() + 2;
    public static final int MO3_ORDERS_AT = MO3_CHANNELS_AT + 1;
    public static final int MO3_RESTART_AT = MO3_ORDERS_AT + Short.BYTES;

    public static final String C669_TITLE = "Paula 669";
    public static final String C669_CREDIT = "by Adeptum";
    public static final int C669_PATTERNS = 2;
    public static final int C669_NOTE = 24;
    public static final int C669_SECOND_NOTE = 36;
    public static final int C669_VOLUME = 15;
    public static final int C669_HALF_VOLUME = 8;
    public static final int C669_VOLUME_ROW = 1;
    public static final int C669_VOLUME_CHANNEL = 1;
    public static final int C669_SPEED_ROW = 2;
    public static final int C669_SPEED_COMMAND = 5;
    public static final int C669_NEW_SPEED = 3;
    public static final int[] C669_SPEEDS = {4, 2};
    public static final int[] C669_BREAKS = {63, 3};
    public static final int C669_SAMPLE_LENGTH = 64;
    public static final int C669_SAMPLE_HIGH = 228;
    public static final int C669_SAMPLE_LOW = 28;

    private static final int C669_HEADER_LENGTH = 497;
    private static final int C669_MESSAGE_LINE = 36;
    private static final int C669_LIST_LENGTH = 128;
    private static final int C669_END_OF_ORDERS = 0xFF;
    private static final int C669_SAMPLE_HEADER_LENGTH = 25;
    private static final int C669_NAME_LENGTH = 13;
    private static final int C669_CHANNELS = 8;
    private static final int C669_CELL_LENGTH = 3;
    private static final int C669_PATTERN_LENGTH = ROWS * C669_CHANNELS * C669_CELL_LENGTH;
    private static final int C669_EMPTY = 0xFF;
    private static final int C669_VOLUME_ONLY = 0xFE;

    /**
     * Where the flags sit, past the seven counts, the speed and the tempo; they say which tracker wrote the
     * module and which of its habits the module wants back.
     */
    public static final int MO3_FLAGS_AT = MO3_CHANNELS_AT + 15;

    public static final int MO3_IS_IMPULSE_TRACKER = 0x0100;
    public static final int MO3_IS_FAST_TRACKER = 0;

    private static final int MO3_VERSION = 5;
    private static final int MO3_HEADER_LENGTH = 422;
    private static final int MO3_MUSIC_LENGTH = 2048;
    private static final int MO3_LITERALS_PER_CONTROL_BYTE = 8;
    private static final int MO3_IS_MOD = 0x80;

    /**
     * A flag every MO3 carries, whatever it was packed from.
     */
    public static final int MO3_ALWAYS_SET = 0x20000;
    private static final int MO3_SAMPLE_LOOPS = 0x10;
    private static final int MO3_PANNING_UNSET = 0xFFFF;
    private static final int MO3_MIDDLE_FINETUNE = 128;
    public static final int MO3_INSTRUMENT_MODE_FLAG = 0x0200;
    private static final int MO3_KEYS = 120;
    private static final int MO3_LONGEST_ENVELOPE = 25;
    private static final int MO3_ENVELOPE_POINTS = 2;
    private static final int MO3_ENVELOPE_ON = 0x01;
    private static final int MO3_INSTRUMENT_VOLUME = 128;

    public static final int MO3_ENVELOPE_END = 40;
    public static final int MO3_LOUDEST = 64;
    public static final int MO3_FADE_OUT = 512;
    public static final int MO3_INSTRUMENTS = 1;
    public static final String INSTRUMENT_NAME = "reed";

    public static final int CHIRP_LENGTH = 6000;

    private static final double CHIRP_FROM = 20;
    private static final double CHIRP_RISE = 0.004;
    private static final double CHIRP_SCALE = 200;
    private static final double CHIRP_PEAK = 100;

    public static final int MO3_LEFT = 64;
    public static final int MO3_RIGHT = 192;

    /**
     * Two commands on one row, a note and the instrument to sound it with, then the byte that ends the track.
     */
    private static final byte[] MO3_TRACK = {0x12, 0x01, 0x30, 0x02, 0x00, 0x00};

    private TestModules() {
    }

    public static Path writeProTracker(Path directory) throws IOException {
        return Files.write(directory.resolve("test.mod"), proTracker());
    }

    public static byte[] proTracker() {
        final ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + PATTERN_LENGTH + SAMPLE_LENGTH);
        buffer.put(padded(TITLE, 20));
        buffer.put(padded(SAMPLE_NAME, 22))
                .putShort((short) (SAMPLE_LENGTH / 2))
                .put((byte) 0)
                .put((byte) 64)
                .putShort((short) 0)
                .putShort((short) (SAMPLE_LENGTH / 2));
        buffer.position(950);
        buffer.put((byte) 1).put((byte) 0);
        buffer.position(1080);
        buffer.put("M.K.".getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) (PERIOD_C2 >> 8)).put((byte) PERIOD_C2).put((byte) 0x10).put((byte) 0);
        buffer.position(HEADER_LENGTH + PATTERN_LENGTH);
        for (int i = 0; i < SAMPLE_LENGTH; i++) {
            buffer.put((byte) (i < SAMPLE_LENGTH / 2 ? 100 : -100));
        }
        return buffer.array();
    }

    public static Path writeProTrackerSwappingSamples(Path directory) throws IOException {
        return Files.write(directory.resolve("swap.mod"), proTrackerSwappingSamples());
    }

    /**
     * A ProTracker module whose first channel plays a long sample looping over its first part, then names a
     * short sample without a loop on the next row, which ProTracker swaps in once the loop comes round.
     */
    public static byte[] proTrackerSwappingSamples() {
        final ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + PATTERN_LENGTH + LONG_SAMPLE_LENGTH + SAMPLE_LENGTH);
        buffer.put(padded(TITLE, 20));
        buffer.put(padded("long", 22)).putShort((short) (LONG_SAMPLE_LENGTH / 2)).put((byte) 0).put((byte) 64)
                .putShort((short) 0).putShort((short) (SHORT_LOOP_LENGTH / 2));
        buffer.put(padded("short", 22)).putShort((short) (SAMPLE_LENGTH / 2)).put((byte) 0).put((byte) 64)
                .putShort((short) 0).putShort((short) 1);
        buffer.position(950);
        buffer.put((byte) 1).put((byte) 0);
        buffer.position(1080);
        buffer.put("M.K.".getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) (PERIOD_C2 >> 8)).put((byte) PERIOD_C2).put((byte) 0x10).put((byte) 0);
        buffer.position(HEADER_LENGTH + ROW_LENGTH);
        buffer.put((byte) 0).put((byte) 0).put((byte) 0x20).put((byte) 0);
        buffer.position(HEADER_LENGTH + PATTERN_LENGTH);
        for (int i = 0; i < LONG_SAMPLE_LENGTH + SAMPLE_LENGTH; i++) {
            buffer.put((byte) (i % SAMPLE_LENGTH < SAMPLE_LENGTH / 2 ? 100 : -100));
        }
        return buffer.array();
    }

    public static Path writeDigiBooster(Path directory) throws IOException {
        return Files.write(directory.resolve("test.dbm"), digiBooster());
    }

    /**
     * Builds a minimal DigiBooster Pro module: two tracks, one looping eight-bit sample played by one
     * instrument with a volume envelope, one pattern of four rows and an echo on the first track.
     */
    public static byte[] digiBooster() {
        final ByteBuffer buffer = ByteBuffer.allocate(DBM_LENGTH);
        buffer.put("DBM0".getBytes(StandardCharsets.US_ASCII)).put((byte) 2).put((byte) 0x21).putShort((short) 0);
        chunk(buffer, "NAME", padded(TITLE, 44));
        chunk(buffer, "INFO", words(1, 1, 1, 1, DBM_TRACKS));
        chunk(buffer, "SONG", song());
        chunk(buffer, "INST", instrument());
        chunk(buffer, "PATT", pattern());
        chunk(buffer, "SMPL", sample());
        chunk(buffer, "VENV", envelope());
        chunk(buffer, "DSPE", echo());
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private static byte[] song() {
        return ByteBuffer.allocate(44 + 2 + 2 * DBM_ORDERS).put(padded(SONG_NAME, 44)).putShort((short) DBM_ORDERS)
                .putShort((short) 0).putShort((short) 0).array();
    }

    private static byte[] instrument() {
        return ByteBuffer.allocate(50).put(padded(SAMPLE_NAME, 30)).putShort((short) 1).putShort((short) 64)
                .putInt(C3_FREQUENCY).putInt(0).putInt(SAMPLE_LENGTH / 2).putShort((short) 0).putShort((short) 1).array();
    }

    /**
     * One entry on the second track of the first row: a C-4 played by the first instrument.
     */
    private static byte[] pattern() {
        final byte[] packed = {2, 0x03, 0x40, 1, 0, 0, 0, 0};
        return ByteBuffer.allocate(6 + packed.length).putShort((short) DBM_ROWS).putInt(packed.length).put(packed).array();
    }

    private static byte[] sample() {
        final ByteBuffer sample = ByteBuffer.allocate(8 + SAMPLE_LENGTH).putInt(1).putInt(SAMPLE_LENGTH);
        for (int i = 0; i < SAMPLE_LENGTH; i++) {
            sample.put((byte) (i < SAMPLE_LENGTH / 2 ? 100 : -100));
        }
        return sample.array();
    }

    private static byte[] envelope() {
        final ByteBuffer envelope = ByteBuffer.allocate(2 + 136).putShort((short) 1);
        envelope.putShort((short) 1).put((byte) 0x05).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 2).put((byte) 0);
        envelope.putShort((short) 0).putShort((short) 64).putShort((short) 10).putShort((short) 32).putShort((short) 20).putShort((short) 0);
        return envelope.array();
    }

    private static byte[] echo() {
        return ByteBuffer.allocate(2 + DBM_TRACKS + 8).putShort((short) DBM_TRACKS).put((byte) 0).put((byte) 1)
                .putShort((short) ECHO_DELAY).putShort((short) ECHO_FEEDBACK).putShort((short) ECHO_MIX).putShort((short) ECHO_CROSS).array();
    }

    public static Path writeHively(Path directory) throws IOException {
        return Files.write(directory.resolve("paula.ahx"), hively());
    }

    /**
     * Builds a minimal AHX module: four channels, one position playing a single track whose first row
     * sounds a C-1 on the one instrument, a square swept from a performance list, and no subsongs. Its
     * first track is the blank one AHX leaves out of the file entirely.
     */
    public static byte[] hively() {
        final byte[] positions = bytes(1, 0, 0, 0, 0, 0, 0, 0);
        final byte[] track = bytes(0x04, 0x10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        final byte[] instrument = concat(hivelyInstrument(), bytes(0x01, 0x80, 0, 0));
        final byte[] body = concat(positions, track, instrument);
        final int nameOffset = AHX_HEADER_LENGTH + body.length;

        return concat(bytes('T', 'H', 'X', 1, nameOffset >> 8, nameOffset,
                BLANK_FIRST_TRACK, HIVELY_POSITIONS, 0, 0, HIVELY_TRACK_LENGTH, HIVELY_TRACK_COUNT,
                HIVELY_INSTRUMENTS, HIVELY_SUBSONGS), body, names(HIVELY_TITLE, HIVELY_INSTRUMENT));
    }

    public static Path writeHivelyTracker(Path directory) throws IOException {
        return Files.write(directory.resolve("paula.hvl"), hivelyTracker());
    }

    /**
     * The same song as {@link #hively()} in HivelyTracker's own format: eight channels, the mix gain and
     * stereo separation the file carries itself, and the empty rows of a track stored as one byte each.
     */
    public static byte[] hivelyTracker() {
        final byte[] positions = bytes(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        final byte[] track = bytes(1, 1, 0, 0, 0, HVL_EMPTY_STEP, HVL_EMPTY_STEP, HVL_EMPTY_STEP);
        final byte[] instrument = concat(hivelyInstrument(), bytes(0, 3, 0, 0, 0));
        final byte[] body = concat(positions, track, instrument);
        final int nameOffset = HVL_HEADER_LENGTH + body.length;

        return concat(bytes('H', 'V', 'L', 1, nameOffset >> 8, nameOffset,
                BLANK_FIRST_TRACK, HIVELY_POSITIONS, (HIVELY_CHANNELS - 4) << 2, 0, HIVELY_TRACK_LENGTH,
                HIVELY_TRACK_COUNT, HIVELY_INSTRUMENTS, HIVELY_SUBSONGS, HIVELY_MIX_GAIN, HIVELY_STEREO),
                body, names(HIVELY_TITLE, HIVELY_INSTRUMENT));
    }

    /**
     * The twenty-two instrument bytes both formats share: full volume, the shortest square, an envelope
     * that attacks and decays in a frame each, and a performance list of one entry stepped every frame.
     */
    private static byte[] hivelyInstrument() {
        return bytes(64, 3, 1, 64, 1, 64, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0x20, 0x3f, 1, 0, 1, 1);
    }


    public static Path writeComposer669(Path directory) throws IOException {
        return Files.write(directory.resolve("paula.669"), composer669());
    }

    /**
     * A minimal Composer 669 module: two patterns played once each. The first, at speed four, sounds a C-5 at
     * full volume on a looping square, then sets a volume on its own and then a speed of three; the second, at
     * speed two, sounds the octave above and is broken off after its fourth row.
     */
    public static byte[] composer669() {
        return composer669("if");
    }

    public static byte[] composer669(String mark) {
        final ByteBuffer buffer = ByteBuffer.allocate(C669_HEADER_LENGTH + C669_SAMPLE_HEADER_LENGTH
                + C669_PATTERNS * C669_PATTERN_LENGTH + C669_SAMPLE_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(mark.getBytes(StandardCharsets.US_ASCII));
        buffer.put(padded(C669_TITLE, C669_MESSAGE_LINE)).put(padded(C669_CREDIT, C669_MESSAGE_LINE))
                .put(padded("", C669_MESSAGE_LINE));
        buffer.put((byte) 1).put((byte) C669_PATTERNS).put((byte) 0);
        buffer.put(list669(C669_END_OF_ORDERS, 0, 1)).put(list669(0, C669_SPEEDS)).put(list669(0, C669_BREAKS));
        buffer.put(padded(SAMPLE_NAME, C669_NAME_LENGTH)).putInt(C669_SAMPLE_LENGTH).putInt(0)
                .putInt(C669_SAMPLE_LENGTH);
        final byte[] first = emptyPattern669();
        cell669(first, 0, 0, C669_NOTE << 2, C669_VOLUME, C669_EMPTY);
        cell669(first, C669_VOLUME_ROW, C669_VOLUME_CHANNEL, C669_VOLUME_ONLY, C669_HALF_VOLUME, C669_EMPTY);
        cell669(first, C669_SPEED_ROW, 0, C669_EMPTY, C669_EMPTY, C669_SPEED_COMMAND << 4 | C669_NEW_SPEED);
        final byte[] second = emptyPattern669();
        cell669(second, 0, 0, C669_SECOND_NOTE << 2, C669_VOLUME, C669_EMPTY);
        buffer.put(first).put(second);
        for (int i = 0; i < C669_SAMPLE_LENGTH; i++) {
            buffer.put((byte) (i < C669_SAMPLE_LENGTH / 2 ? C669_SAMPLE_HIGH : C669_SAMPLE_LOW));
        }
        return buffer.array();
    }

    private static byte[] list669(int filler, int... values) {
        final byte[] list = new byte[C669_LIST_LENGTH];
        Arrays.fill(list, (byte) filler);
        for (int i = 0; i < values.length; i++) {
            list[i] = (byte) values[i];
        }
        return list;
    }

    private static byte[] emptyPattern669() {
        final byte[] pattern = new byte[C669_PATTERN_LENGTH];
        Arrays.fill(pattern, (byte) C669_EMPTY);
        return pattern;
    }

    private static void cell669(byte[] pattern, int row, int channel, int note, int volume, int effect) {
        final int at = (row * C669_CHANNELS + channel) * C669_CELL_LENGTH;
        pattern[at] = (byte) note;
        pattern[at + 1] = (byte) volume;
        pattern[at + 2] = (byte) effect;
    }

    public static Path writeMed(Path directory) throws IOException {
        return Files.write(directory.resolve("paula.med"), medMmd0());
    }

    /**
     * A minimal OctaMED module in the three-byte-a-line form: two tracks of four lines, the first sounding a
     * C-2 on a looping square wave, played through a play sequence of one block.
     */
    public static byte[] medMmd0() {
        return med(false, medMmd0Block());
    }

    /**
     * The same song written the four-byte way, where the note and the instrument each have a byte of their
     * own instead of sharing bits.
     */
    public static byte[] medMmd1() {
        return med(true, medMmd1Block());
    }

    /**
     * The three-byte form again, with its block run through the counted packing MMDC writes: the lines
     * verbatim, then the silence of the remaining tracks left as a count.
     */
    public static byte[] medCompressed() {
        final byte[] block = medMmd0Block();
        final int header = 2;
        final byte[] lines = Arrays.copyOfRange(block, header, block.length);
        final byte[] packed = concat(Arrays.copyOf(block, header),
                bytes(lines.length - 1), lines);
        return med(false, packed, 'C');
    }

    private static byte[] medMmd0Block() {
        final byte[] rows = new byte[MED_TRACKS * MED_LINES * 3];
        rows[0] = MED_NOTE;
        rows[1] = 1 << 4;
        return concat(bytes(MED_TRACKS, MED_LINES - 1), rows);
    }

    private static byte[] medMmd1Block() {
        final byte[] rows = new byte[MED_TRACKS * MED_LINES * 4];
        rows[0] = MED_NOTE;
        rows[1] = 1;
        return concat(words(MED_TRACKS, MED_LINES - 1), new byte[4], rows);
    }

    private static byte[] med(boolean wide, byte[] block) {
        return med(wide, block, wide ? '1' : '0');
    }

    private static byte[] med(boolean wide, byte[] block, char kind) {
        final byte[] sample = new byte[MED_SAMPLE_LENGTH];
        for (int frame = 0; frame < sample.length; frame++) {
            sample[frame] = (byte) (frame < sample.length / 2 ? 100 : -100);
        }
        final byte[] instrument = concat(words(0, sample.length, 0), sample);
        final byte[] song = medSong();

        final int songAt = MED_HEADER_LENGTH;
        final int blockTableAt = songAt + song.length;
        final int sampleTableAt = blockTableAt + 4;
        final int expansionAt = sampleTableAt + 4;
        final int blockAt = expansionAt + MED_EXPANSION_LENGTH;
        final int instrumentAt = blockAt + block.length;
        final int nameAt = instrumentAt + instrument.length;
        final byte[] name = names(SONG_NAME);

        return concat(
                bytes('M', 'M', 'D', kind), words(0, 0), words(0, songAt),
                words(0, 0), words(0, blockTableAt), words(0, 0), words(0, sampleTableAt),
                words(0, 0), words(0, expansionAt), new byte[16],
                song,
                words(0, blockAt), words(0, instrumentAt),
                medExpansion(nameAt, name.length),
                block, instrument, name);
    }

    private static byte[] medSong() {
        final byte[] settings = new byte[63 * 8];
        settings[2] = (byte) (MED_SAMPLE_LENGTH >> 9);
        settings[3] = (byte) (MED_SAMPLE_LENGTH >> 1);
        settings[6] = MED_VOLUME;
        final byte[] playSeq = new byte[256];
        return concat(settings, words(1, 1), playSeq, words(MED_TEMPO),
                bytes(0, 0, 0, MED_SPEED), new byte[16], bytes(MED_MASTER_VOLUME, 1));
    }

    private static byte[] medExpansion(int nameAt, int nameLength) {
        return concat(new byte[28], new byte[16], words(0, nameAt), words(0, nameLength));
    }

    private static byte[] names(String... names) {
        final ByteArrayOutputStream text = new ByteArrayOutputStream();
        for (final String name : names) {
            text.writeBytes(name.getBytes(StandardCharsets.ISO_8859_1));
            text.write(0);
        }
        return text.toByteArray();
    }

    /**
     * A module whose one sample sweeps in pitch. It is tonal enough for a lossy encoder to keep it well and,
     * unlike a steady tone, not so predictable that packing it losslessly wins, which is what makes an MO3
     * compressor reach for MPEG audio or Ogg Vorbis on it.
     */
    public static byte[] proTrackerChirp() {
        final byte[] sample = chirpSample();
        final ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + PATTERN_LENGTH + sample.length);
        buffer.put(padded(TITLE, 20));
        buffer.put(padded("tone", 22))
                .putShort((short) (sample.length / 2))
                .put((byte) 0)
                .put((byte) 64)
                .putShort((short) 0)
                .putShort((short) (sample.length / 2));
        buffer.position(950);
        buffer.put((byte) 1).put((byte) 0);
        buffer.position(1080);
        buffer.put("M.K.".getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) (PERIOD_C2 >> 8)).put((byte) PERIOD_C2).put((byte) 0x10).put((byte) 0);
        buffer.position(HEADER_LENGTH + PATTERN_LENGTH);
        buffer.put(sample);
        return buffer.array();
    }

    /**
     * The sweep itself, which is what a waveform read back out of a lossy MO3 is measured against.
     */
    public static byte[] chirpSample() {
        final byte[] sample = new byte[CHIRP_LENGTH];
        for (int at = 0; at < sample.length; at++) {
            final double turns = (CHIRP_FROM + at * CHIRP_RISE) * at / CHIRP_SCALE;
            sample[at] = (byte) (int) (CHIRP_PEAK * Math.sin(2 * Math.PI * turns));
        }
        return sample;
    }

    public static Path writeMo3(Path directory) throws IOException {
        return Files.write(directory.resolve("test.mo3"), mo3());
    }

    /**
     * Builds a minimal MO3: a four-channel ProTracker module of one pattern, one note on the first channel and
     * one looping square-wave sample kept uncompressed, packed into a control stream that only ever copies.
     */
    public static byte[] mo3() {
        return mo3(mo3Music());
    }

    /**
     * The same file around a music chunk of the caller's own, so a test can say what the module claims about
     * itself and see it refused.
     */
    public static byte[] mo3(byte[] music) {
        final byte[] packed = mo3Packed(music);
        final ByteBuffer file = ByteBuffer.allocate(12 + packed.length + SAMPLE_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        file.put("MO3".getBytes(StandardCharsets.US_ASCII)).put((byte) MO3_VERSION);
        file.putInt(music.length).putInt(packed.length).put(packed);
        for (int at = 0; at < SAMPLE_LENGTH; at++) {
            file.put((byte) (at < SAMPLE_LENGTH / 2 ? 100 : -100));
        }
        return file.array();
    }

    /**
     * The music chunk as the reader sees it once unpacked.
     */
    public static byte[] mo3Music() {
        final ByteBuffer music = ByteBuffer.allocate(MO3_MUSIC_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        music.put(TITLE.getBytes(StandardCharsets.US_ASCII)).put((byte) 0).put((byte) 0);
        mo3Header(music);
        music.put((byte) 0).put((byte) 0);
        for (int channel = 0; channel < MO3_CHANNELS; channel++) {
            music.putShort((short) (channel == 0 ? 0 : 1));
        }
        music.putShort((short) MO3_ROWS);
        music.putInt(MO3_TRACK.length).put(MO3_TRACK);
        music.putInt(1).put((byte) 0);
        mo3Instrument(music);
        mo3Sample(music);
        return Arrays.copyOf(music.array(), music.position());
    }

    private static void mo3Header(ByteBuffer music) {
        music.put((byte) MO3_CHANNELS).putShort((short) MO3_ORDERS).putShort((short) 0).putShort((short) 1);
        music.putShort((short) 2).putShort((short) MO3_INSTRUMENTS).putShort((short) MO3_SAMPLES);
        music.put((byte) MO3_SPEED).put((byte) MO3_TEMPO).putInt(MO3_IS_MOD | MO3_ALWAYS_SET | MO3_INSTRUMENT_MODE_FLAG);
        music.put((byte) 64).put((byte) 0).put((byte) 0);
        for (int channel = 0; channel < 64; channel++) {
            music.put((byte) 0);
        }
        for (int channel = 0; channel < 64; channel++) {
            music.put((byte) (channel % 4 == 1 || channel % 4 == 2 ? MO3_RIGHT : MO3_LEFT));
        }
        music.put(new byte[MO3_HEADER_LENGTH - 150]);
    }

    /**
     * One instrument sounding the one sample on every key, with a volume envelope of two points falling from
     * the loudest to nothing.
     */
    private static void mo3Instrument(ByteBuffer music) {
        music.put(INSTRUMENT_NAME.getBytes(StandardCharsets.US_ASCII)).put((byte) 0).put((byte) 0);
        music.putInt(0);
        for (int key = 0; key < MO3_KEYS; key++) {
            music.putShort((short) key).putShort((short) 0);
        }
        mo3Envelope(music, MO3_ENVELOPE_ON);
        mo3Envelope(music, 0);
        mo3Envelope(music, 0);
        music.putInt(0);
        music.putShort((short) MO3_FADE_OUT);
        music.putInt(0);
        music.put((byte) MO3_INSTRUMENT_VOLUME).putShort((short) MO3_PANNING_UNSET);
        music.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
        music.putShort((short) 0).putShort((short) 0);
        music.put((byte) 0).put((byte) 0);
    }

    private static void mo3Envelope(ByteBuffer music, int flags) {
        music.put((byte) flags).put((byte) MO3_ENVELOPE_POINTS).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
        music.putShort((short) 0).putShort((short) MO3_LOUDEST);
        music.putShort((short) MO3_ENVELOPE_END).putShort((short) 0);
        for (int point = 2; point < MO3_LONGEST_ENVELOPE; point++) {
            music.putShort((short) 0).putShort((short) 0);
        }
    }

    private static void mo3Sample(ByteBuffer music) {
        music.put(SAMPLE_NAME.getBytes(StandardCharsets.US_ASCII)).put((byte) 0).put((byte) 0);
        music.putInt(MO3_MIDDLE_FINETUNE).put((byte) 0).put((byte) 64).putShort((short) MO3_PANNING_UNSET);
        music.putInt(SAMPLE_LENGTH).putInt(0).putInt(SAMPLE_LENGTH).putShort((short) MO3_SAMPLE_LOOPS);
        music.putInt(0).put((byte) 64);
        music.putInt(0).putInt(0).putInt(0).putShort((short) 0);
    }

    /**
     * A control stream that says nothing but "copy the next byte": the first byte goes through untouched, and
     * a control byte of zero bits announces every eight literals after it.
     */
    private static byte[] mo3Packed(byte[] music) {
        final ByteArrayOutputStream packed = new ByteArrayOutputStream();
        packed.write(music[0]);
        for (int at = 1; at < music.length; at += MO3_LITERALS_PER_CONTROL_BYTE) {
            packed.write(0);
            packed.write(music, at, Math.min(MO3_LITERALS_PER_CONTROL_BYTE, music.length - at));
        }
        return packed.toByteArray();
    }

    private static byte[] bytes(int... values) {
        final byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {
        final ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (final byte[] part : parts) {
            joined.writeBytes(part);
        }
        return joined.toByteArray();
    }

    private static byte[] words(int... values) {
        final ByteBuffer words = ByteBuffer.allocate(values.length * 2);
        for (final int value : values) {
            words.putShort((short) value);
        }
        return words.array();
    }

    private static void chunk(ByteBuffer buffer, String id, byte[] body) {
        buffer.put(id.getBytes(StandardCharsets.US_ASCII)).putInt(body.length).put(body);
    }

    /**
     * An eight-channel ProTracker module with the block FlexTrax appends after the samples, holding the six
     * effect settings in the last seven of its thirty-eight records.
     */
    public static byte[] flexTrax(int... effects) {
        return eightChannel(flexBlock(effects));
    }

    /**
     * The same module saved with the effects switched off, which FlexTrax writes without any block at all.
     */
    public static byte[] flexTraxWithoutEffects() {
        return eightChannel(new byte[0]);
    }

    /**
     * A block whose reverb decay carries a value no slider of the tracker's could have produced.
     */
    public static byte[] flexTraxWithAStrayReverbDecay() {
        final byte[] block = flexBlock(0, FLEX_REVERB_LEVEL, 0, 0, 0, 0);
        block[FLEX_MARK.length() + FLEX_REVERB_DECAY_RECORD_AT] = 0x20;
        return eightChannel(block);
    }

    /**
     * The same module with every sample it does not use given the length of one word that ProTracker gives an
     * empty one, and no data stored for any of them.
     */
    public static byte[] flexTraxWithUnusedSamples(int... effects) {
        final byte[] module = eightChannel(flexBlock(effects));
        for (int sample = 1; sample < FLEX_SAMPLES; sample++) {
            module[FLEX_FIRST_SAMPLE_LENGTH_AT + sample * FLEX_SAMPLE_ENTRY_LENGTH + 1] = 1;
        }
        return module;
    }

    private static byte[] eightChannel(byte[] trailer) {
        final int patternLength = ROWS * FLEX_CHANNELS * BYTES_PER_NOTE;
        final ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + patternLength + SAMPLE_LENGTH + trailer.length);
        buffer.put(padded(TITLE, 20));
        buffer.put(padded(SAMPLE_NAME, 22))
                .putShort((short) (SAMPLE_LENGTH / 2))
                .put((byte) 0)
                .put((byte) 64)
                .putShort((short) 0)
                .putShort((short) (SAMPLE_LENGTH / 2));
        buffer.position(950);
        buffer.put((byte) 1).put((byte) 0);
        buffer.position(1080);
        buffer.put(FLEX_MARK_8CHN.getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) (PERIOD_C2 >> 8)).put((byte) PERIOD_C2).put((byte) 0x10).put((byte) 0);
        buffer.position(HEADER_LENGTH + patternLength);
        for (int i = 0; i < SAMPLE_LENGTH; i++) {
            buffer.put((byte) (i < SAMPLE_LENGTH / 2 ? 100 : -100));
        }
        buffer.position(HEADER_LENGTH + patternLength + SAMPLE_LENGTH);
        buffer.put(trailer);
        return buffer.array();
    }

    private static byte[] flexBlock(int... effects) {
        final ByteBuffer block = ByteBuffer.allocate(FLEX_MARK.length() + FLEX_BLOCK_LENGTH);
        block.put(FLEX_MARK.getBytes(StandardCharsets.US_ASCII));
        for (int effect = 0; effect < effects.length; effect++) {
            block.put(FLEX_MARK.length() + FLEX_EFFECTS_AT[effect] + FLEX_VALUE_IN_RECORD, (byte) effects[effect]);
        }
        return block.array();
    }

    private static byte[] padded(String text, int length) {
        final byte[] bytes = new byte[length];
        final byte[] source = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, bytes, 0, Math.min(source.length, length));
        return bytes;
    }
}

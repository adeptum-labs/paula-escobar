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
 * AHX and a HivelyTracker module playing one synthesised square instead, and the ProTracker one again
 * as an MO3.
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

    private static final int MO3_VERSION = 5;
    private static final int MO3_HEADER_LENGTH = 422;
    private static final int MO3_MUSIC_LENGTH = 640;
    private static final int MO3_LITERALS_PER_CONTROL_BYTE = 8;
    private static final int MO3_IS_MOD = 0x80;
    private static final int MO3_ALWAYS_SET = 0x20000;
    private static final int MO3_SAMPLE_LOOPS = 0x10;
    private static final int MO3_PANNING_UNSET = 0xFFFF;
    private static final int MO3_MIDDLE_FINETUNE = 128;
    private static final int MO3_LEFT = 64;
    private static final int MO3_RIGHT = 192;

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
        mo3Sample(music);
        return Arrays.copyOf(music.array(), music.position());
    }

    private static void mo3Header(ByteBuffer music) {
        music.put((byte) MO3_CHANNELS).putShort((short) MO3_ORDERS).putShort((short) 0).putShort((short) 1);
        music.putShort((short) 2).putShort((short) 0).putShort((short) MO3_SAMPLES);
        music.put((byte) MO3_SPEED).put((byte) MO3_TEMPO).putInt(MO3_IS_MOD | MO3_ALWAYS_SET);
        music.put((byte) 64).put((byte) 0).put((byte) 0);
        for (int channel = 0; channel < 64; channel++) {
            music.put((byte) 0);
        }
        for (int channel = 0; channel < 64; channel++) {
            music.put((byte) (channel % 4 == 1 || channel % 4 == 2 ? MO3_RIGHT : MO3_LEFT));
        }
        music.put(new byte[MO3_HEADER_LENGTH - 150]);
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

    private static byte[] padded(String text, int length) {
        final byte[] bytes = new byte[length];
        final byte[] source = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, bytes, 0, Math.min(source.length, length));
        return bytes;
    }
}

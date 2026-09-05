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
 * The files are read the way hvl_replay.c of HivelyTracker reads them,
 * Copyright © 2006-2018 Pete Gordon, licensed under the three-clause BSD
 * licence and used here under the GNU General Public License.
 */

package com.adeptum.paula.module.hively;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an AHX or a HivelyTracker module. Both are the same tune behind a different header: a fixed header,
 * the subsong list, the arrangement, the tracks and finally the instruments, whose names live in a run of
 * NUL-terminated strings the header points at. AHX packs a step into three bytes and four channels;
 * HivelyTracker spends five on a step, stores an empty one as a single byte, and carries its own channel
 * count, mix gain and stereo separation.
 */
final class HvlReader {

    static final int STEREO_SEPARATION = 2;

    private static final String AHX_MAGIC = "THX";
    private static final String HVL_MAGIC = "HVL";
    /** The magic plus the version byte behind it, which both formats need before they can be told apart. */
    private static final int SIGNATURE_LENGTH = 4;
    private static final int AHX_VERSIONS = 3;
    private static final int HVL_VERSIONS = 2;
    private static final int AHX_HEADER_LENGTH = 14;
    private static final int HVL_HEADER_LENGTH = 16;
    private static final int AHX_CHANNELS = 4;
    private static final int UNUSED_INSTRUMENT_BYTES = 3;
    private static final int NAME_LENGTH = 127;
    private static final int MAX_POSITIONS = 1000;
    private static final int MAX_TRACK_LENGTH = 64;
    private static final int MAX_INSTRUMENTS = 64;
    private static final int HIGHEST_NOTE = 60;
    private static final int LONGEST_WAVE = 5;
    private static final int WAVEFORMS = 4;
    private static final int EMPTY_STEP = 0x3f;
    private static final int BLANK_FIRST_TRACK = 0x80;
    private static final int TOGGLE_FILTER = 4;
    private static final int MIX_GAIN_SCALE = 256;
    private static final int PERCENT = 100;
    private static final int BYTE_BITS = 8;
    private static final int[] AHX_GAIN = {71, 72, 76, 85, 100};
    private static final HvlInstrument EMPTY_INSTRUMENT = new HvlInstrument("", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            false, 0, new HvlEnvelope(0, 0, 0, 0, 0, 0, 0), new HvlPlaylist(0, List.of()));

    /**
     * One repeated element of the file, read at the position the reader has reached.
     */
    @FunctionalInterface
    private interface Part<T> {

        T read() throws IOException;
    }

    private final byte[] file;
    private int position;
    private int namePosition;
    /** AHX has no misc-flags command; the replayer gates it on a tune version only HivelyTracker files carry. */
    private static final int AHX_TUNE_VERSION = 0;

    private int version;

    private HvlReader(byte[] file) {
        this.file = file;
    }

    static HvlTune read(byte[] file) throws IOException {
        if (hasMagic(file, AHX_MAGIC, AHX_VERSIONS)) {
            return new HvlReader(file).ahx();
        }
        if (hasMagic(file, HVL_MAGIC, HVL_VERSIONS)) {
            return new HvlReader(file).hively();
        }
        throw new IOException("Not an AHX or HivelyTracker module");
    }

    static boolean looksLikeHively(byte[] head) {
        return hasMagic(head, AHX_MAGIC, AHX_VERSIONS) || hasMagic(head, HVL_MAGIC, HVL_VERSIONS);
    }

    private static boolean hasMagic(byte[] head, String magic, int versions) {
        return head.length >= SIGNATURE_LENGTH
                && magic.equals(new String(head, 0, magic.length(), StandardCharsets.US_ASCII))
                && (head[3] & 0xff) < versions;
    }

    private HvlTune ahx() throws IOException {
        skip(AHX_MAGIC.length());
        version = unsigned8();
        final int nameOffset = unsigned16();
        final int flags = unsigned8();
        final int positionCount = (flags & 0x0f) << BYTE_BITS | unsigned8();
        final int restart = unsigned16();
        final int trackLength = unsigned8();
        final int trackCount = unsigned8();
        final int instrumentCount = unsigned8();
        final int subsongCount = unsigned8();
        validate(positionCount, trackLength, instrumentCount);
        final String name = startNames(nameOffset);

        position = AHX_HEADER_LENGTH;
        final int[] subsongs = new int[subsongCount];
        for (int i = 0; i < subsongCount; i++) {
            final int subsong = unsigned16();
            subsongs[i] = subsong >= positionCount ? 0 : subsong;
        }
        final List<HvlPosition> positions = positions(positionCount, AHX_CHANNELS, trackCount);
        final HvlStep[][] tracks = tracks(trackCount, trackLength, blankFirstTrack(flags), this::ahxStep);
        final List<HvlInstrument> instruments = instruments(instrumentCount, this::ahxPlaylistEntry);

        return new HvlTune(name, AHX_TUNE_VERSION, AHX_CHANNELS, positionCount, restart(restart, positionCount),
                speedMultiplier(flags), trackLength, trackCount, subsongs, positions, tracks, instruments,
                AHX_GAIN[STEREO_SEPARATION] * MIX_GAIN_SCALE / PERCENT,
                HvlTables.STEREO_PAN_LEFT[STEREO_SEPARATION], HvlTables.STEREO_PAN_RIGHT[STEREO_SEPARATION],
                STEREO_SEPARATION);
    }

    private HvlTune hively() throws IOException {
        skip(HVL_MAGIC.length());
        version = unsigned8();
        final int nameOffset = unsigned16();
        final int flags = unsigned8();
        final int positionCount = (flags & 0x0f) << BYTE_BITS | unsigned8();
        final int channelByte = unsigned8();
        final int channels = (channelByte >> 2) + AHX_CHANNELS;
        final int restart = (channelByte & 3) << BYTE_BITS | unsigned8();
        final int trackLength = unsigned8();
        final int trackCount = unsigned8();
        final int instrumentCount = unsigned8();
        final int subsongCount = unsigned8();
        final int mixGain = unsigned8() * MIX_GAIN_SCALE / PERCENT;
        final int stereo = unsigned8();
        validate(positionCount, trackLength, instrumentCount);
        if (channels > HvlTune.MAX_CHANNELS) {
            throw invalid(channels + " channels");
        }
        if (stereo >= HvlTables.STEREO_PAN_LEFT.length) {
            throw invalid("a stereo separation of " + stereo);
        }
        final String name = startNames(nameOffset);

        position = HVL_HEADER_LENGTH;
        final int[] subsongs = new int[subsongCount];
        for (int i = 0; i < subsongCount; i++) {
            subsongs[i] = unsigned16();
        }
        final List<HvlPosition> positions = positions(positionCount, channels, trackCount);
        final HvlStep[][] tracks = tracks(trackCount, trackLength, blankFirstTrack(flags), this::hivelyStep);
        final List<HvlInstrument> instruments = instruments(instrumentCount, this::hivelyPlaylistEntry);

        return new HvlTune(name, version, channels, positionCount, restart(restart, positionCount),
                speedMultiplier(flags), trackLength, trackCount, subsongs, positions, tracks, instruments,
                mixGain, HvlTables.STEREO_PAN_LEFT[stereo], HvlTables.STEREO_PAN_RIGHT[stereo], stereo);
    }

    private static void validate(int positionCount, int trackLength, int instrumentCount) throws IOException {
        if (positionCount == 0) {
            throw invalid("no positions");
        }
        if (positionCount > MAX_POSITIONS) {
            throw invalid(positionCount + " positions");
        }
        if (trackLength > MAX_TRACK_LENGTH) {
            throw invalid("tracks of " + trackLength + " steps");
        }
        if (instrumentCount > MAX_INSTRUMENTS) {
            throw invalid(instrumentCount + " instruments");
        }
    }

    private static boolean blankFirstTrack(int flags) {
        return (flags & BLANK_FIRST_TRACK) == BLANK_FIRST_TRACK;
    }

    private static int speedMultiplier(int flags) {
        return ((flags >> 5) & 3) + 1;
    }

    /**
     * A restart past the end plays the last position instead.
     */
    private static int restart(int restart, int positionCount) {
        return restart >= positionCount ? positionCount - 1 : restart;
    }

    private List<HvlPosition> positions(int count, int channels, int trackCount) throws IOException {
        final List<HvlPosition> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final int[] track = new int[HvlTune.MAX_CHANNELS];
            final int[] transpose = new int[HvlTune.MAX_CHANNELS];
            for (int channel = 0; channel < channels; channel++) {
                track[channel] = track(trackCount);
                transpose[channel] = signed8();
            }
            positions.add(new HvlPosition(track, transpose));
        }
        return positions;
    }

    /**
     * The tracks are read into an array the module's own count long, so a position naming one beyond it has
     * nothing to play.
     */
    private int track(int trackCount) throws IOException {
        final int track = unsigned8();
        if (track > trackCount) {
            throw invalid("a position playing track " + track + " of " + trackCount);
        }
        return track;
    }

    private HvlStep[][] tracks(int count, int length, boolean blankFirst, Part<HvlStep> step) throws IOException {
        final HvlStep[][] tracks = new HvlStep[count + 1][length];
        for (int track = 0; track <= count; track++) {
            for (int row = 0; row < length; row++) {
                tracks[track][row] = blankFirst && track == 0 ? HvlStep.EMPTY : step.read();
            }
        }
        return tracks;
    }

    private HvlStep ahxStep() throws IOException {
        final int packed = unsigned8();
        final int instrument = unsigned8();
        final int param = unsigned8();
        return new HvlStep(note((packed >> 2) & 0x3f), (packed & 3) << 4 | instrument >> 4, instrument & 0x0f,
                param, 0, 0);
    }

    private HvlStep hivelyStep() throws IOException {
        final int note = unsigned8();
        if (note == EMPTY_STEP) {
            return HvlStep.EMPTY;
        }
        final int instrument = unsigned8();
        final int effects = unsigned8();
        final int param = unsigned8();
        final int secondParam = unsigned8();
        return new HvlStep(note(note), instrument, effects >> 4, param, effects & 0x0f, secondParam);
    }

    /**
     * Instrument zero is never stored: notes name their instrument from one, so the replayer only needs it to
     * be there.
     */
    private List<HvlInstrument> instruments(int count, Part<HvlPlaylistEntry> entry) throws IOException {
        final List<HvlInstrument> instruments = new ArrayList<>(count + 1);
        instruments.add(EMPTY_INSTRUMENT);
        for (int i = 1; i <= count; i++) {
            instruments.add(instrument(entry));
        }
        return instruments;
    }

    private HvlInstrument instrument(Part<HvlPlaylistEntry> entry) throws IOException {
        final String name = instrumentName();
        final int volume = unsigned8();
        final int wave = unsigned8();
        final HvlEnvelope envelope = new HvlEnvelope(unsigned8(), unsigned8(), unsigned8(), unsigned8(),
                unsigned8(), unsigned8(), unsigned8());
        skip(UNUSED_INSTRUMENT_BYTES);
        final int filterLower = unsigned8();
        final int vibratoDelay = unsigned8();
        final int hardCut = unsigned8();
        final int vibratoSpeed = unsigned8();
        final int squareLower = unsigned8();
        final int squareUpper = unsigned8();
        final int squareSpeed = unsigned8();
        final int filterUpper = unsigned8();
        final int playlistSpeed = unsigned8();
        final int playlistLength = unsigned8();

        final List<HvlPlaylistEntry> entries = new ArrayList<>(playlistLength);
        for (int i = 0; i < playlistLength; i++) {
            entries.add(entry.read());
        }
        return new HvlInstrument(name, volume, waveLength(wave & 0x07), filterLower & 0x7f, filterUpper & 0x3f,
                (wave >> 3) & 0x1f | (filterLower >> 2) & 0x20, squareLower, squareUpper, squareSpeed,
                vibratoDelay, vibratoSpeed, hardCut & 0x0f, (hardCut & 0x80) != 0, (hardCut >> 4) & 0x07,
                envelope, new HvlPlaylist(playlistSpeed, entries));
    }

    private HvlPlaylistEntry ahxPlaylistEntry() throws IOException {
        final int packed = unsigned8();
        final int note = unsigned8();
        final int param = unsigned8();
        final int secondParam = unsigned8();
        final int fx = ahxEffect((packed >> 2) & 7);
        final int secondFx = ahxEffect((packed >> 5) & 7);
        return new HvlPlaylistEntry(note(note & 0x3f), waveform((packed << 1) & 6 | note >> 7),
                ((note >> 6) & 1) != 0, new int[]{fx, secondFx},
                new int[]{withoutFilter(fx, param), withoutFilter(secondFx, secondParam)});
    }

    private HvlPlaylistEntry hivelyPlaylistEntry() throws IOException {
        final int fx = unsigned8();
        final int waveform = unsigned8();
        final int note = unsigned8();
        final int param = unsigned8();
        final int secondParam = unsigned8();
        return new HvlPlaylistEntry(note(note & 0x3f), waveform(waveform & 7), ((note >> 6) & 1) != 0,
                new int[]{fx & 0x0f, (waveform >> 3) & 0x0f}, new int[]{param, secondParam});
    }

    /**
     * The replayer holds a period for the notes up to the highest one, six lengths of wave and four waveforms
     * to name, so a value beyond them is played as the last one the replayer has rather than read off the end
     * of its tables.
     */
    private static int note(int stored) {
        return Math.min(stored, HIGHEST_NOTE);
    }

    private static int waveLength(int stored) {
        return Math.min(stored, LONGEST_WAVE);
    }

    private static int waveform(int stored) {
        return Math.min(stored, WAVEFORMS);
    }

    /**
     * The three bits an AHX performance list spends on an effect cannot hold commands 12 and 15, so it stores
     * those as 6 and 7.
     */
    private static int ahxEffect(int stored) {
        return switch (stored) {
            case 6 -> 12;
            case 7 -> 15;
            default -> stored;
        };
    }

    /**
     * Version 0 predates the filter, so its half of a toggle command means nothing and AHX drops it too.
     */
    private int withoutFilter(int fx, int param) {
        return version == 0 && fx == TOGGLE_FILTER && (param & 0xf0) != 0 ? param & 0x0f : param;
    }

    /**
     * The song name heads the run of strings the instruments take theirs from.
     */
    private String startNames(int offset) throws IOException {
        if (offset >= file.length) {
            throw truncated();
        }
        final String name = text(offset);
        namePosition = offset + name.length() + 1;
        return name;
    }

    /**
     * An instrument beyond the names the file holds simply has none, as the replayer reads them by walking
     * the run of strings rather than by counting.
     */
    private String instrumentName() {
        if (namePosition >= file.length) {
            return "";
        }
        final String name = text(namePosition);
        namePosition += name.length() + 1;
        return name;
    }

    private String text(int offset) {
        final int limit = Math.min(offset + NAME_LENGTH, file.length);
        int end = offset;
        while (end < limit && file[end] != 0) {
            end++;
        }
        return new String(file, offset, end - offset, StandardCharsets.ISO_8859_1);
    }

    private void skip(int length) throws IOException {
        require(length);
        position += length;
    }

    private int unsigned8() throws IOException {
        require(1);
        return file[position++] & 0xff;
    }

    private int signed8() throws IOException {
        require(1);
        return file[position++];
    }

    private int unsigned16() throws IOException {
        return unsigned8() << BYTE_BITS | unsigned8();
    }

    private void require(int length) throws IOException {
        if (position + length > file.length) {
            throw truncated();
        }
    }

    private static IOException truncated() {
        return new IOException("The AHX or HivelyTracker module is short of what it says it holds");
    }

    private static IOException invalid(String what) {
        return new IOException("Invalid AHX or HivelyTracker module: " + what);
    }
}

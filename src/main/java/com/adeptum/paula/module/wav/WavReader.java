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

package com.adeptum.paula.module.wav;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import com.adeptum.paula.module.UnsupportedModuleException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Finds the samples in the three containers plain recorded audio is handed around in: Microsoft's RIFF wave
 * files, Apple's AIFF and its compressed sibling AIFC, and Sun's AU. The first two describe themselves in
 * chunks that may come in any order and with anything in between them, so both are walked chunk by chunk
 * rather than read at fixed offsets; AU says everything it has to say in a header of counted words.
 */
public final class WavReader {

    private static final String RIFF = "RIFF";
    private static final String WAVE = "WAVE";
    private static final String FORM = "FORM";
    private static final String AIFF = "AIFF";
    private static final String AIFC = "AIFC";
    private static final String SND = ".snd";

    private static final int ID_LENGTH = 4;
    private static final int CHUNK_HEADER = 8;
    private static final int FORM_HEADER = 12;
    private static final int SHORT_BYTES = 2;
    private static final int INT_BYTES = 4;
    private static final int BYTE_MASK = 0xFF;

    private static final int FMT_TAG = 0;
    private static final int FMT_CHANNELS = 2;
    private static final int FMT_RATE = 4;
    private static final int FMT_BITS = 14;
    private static final int FMT_SUBFORMAT = 24;
    private static final int FMT_MINIMUM = 16;
    private static final int FMT_EXTENSIBLE = 40;

    private static final int TAG_PCM = 1;
    private static final int TAG_FLOAT = 3;
    private static final int TAG_MU_LAW = 7;
    private static final int TAG_EXTENSIBLE = 0xFFFE;

    private static final int COMM_CHANNELS = 0;
    private static final int COMM_FRAMES = 2;
    private static final int COMM_BITS = 6;
    private static final int COMM_RATE = 8;
    private static final int COMM_COMPRESSION = 18;
    private static final int COMM_MINIMUM = 18;
    private static final int COMM_COMPRESSED = 22;
    private static final int SSND_HEADER = 8;

    /**
     * The eleven bits of exponent bias and the mantissa width of the 80-bit extended float AIFF states its
     * sample rate in.
     */
    private static final int EXTENDED_BIAS = 16383;
    private static final int EXTENDED_MANTISSA = 63;
    private static final int EXTENDED_BYTES = 8;

    private static final int AU_DATA_FROM = 4;
    private static final int AU_DATA_SIZE = 8;
    private static final int AU_ENCODING = 12;
    private static final int AU_RATE = 16;
    private static final int AU_CHANNELS = 20;
    private static final int AU_HEADER = 24;
    private static final long AU_SIZE_UNKNOWN = 0xFFFFFFFFL;

    private static final int AU_MU_LAW = 1;
    private static final int AU_EIGHT_BIT = 2;
    private static final int AU_SIXTEEN_BIT = 3;
    private static final int AU_TWENTY_FOUR_BIT = 4;
    private static final int AU_THIRTY_TWO_BIT = 5;
    private static final int AU_FLOAT = 6;

    private static final int TWENTY_FOUR_BITS = 24;

    private WavReader() {
    }

    public static WavAudio read(Path path, byte[] file) throws UnsupportedModuleException {
        if (file.length < FORM_HEADER) {
            throw new UnsupportedModuleException(path, "not a wave file");
        }
        final String container = text(file, 0);
        final String kind = text(file, CHUNK_HEADER);
        if (RIFF.equals(container) && WAVE.equals(kind)) {
            return riff(path, file);
        }
        if (FORM.equals(container) && (AIFF.equals(kind) || AIFC.equals(kind))) {
            return aiff(path, file);
        }
        if (SND.equals(container)) {
            return au(path, file);
        }
        throw new UnsupportedModuleException(path, "not a wave file");
    }

    private static WavAudio riff(Path path, byte[] file) throws UnsupportedModuleException {
        int rate = 0;
        int channels = 0;
        WavEncoding encoding = null;
        for (final Chunks chunks = new Chunks(file, LITTLE_ENDIAN); chunks.next();) {
            if ("fmt ".equals(chunks.id) && chunks.size >= FMT_MINIMUM) {
                channels = (int) number(file, chunks.body + FMT_CHANNELS, SHORT_BYTES, LITTLE_ENDIAN);
                rate = (int) number(file, chunks.body + FMT_RATE, INT_BYTES, LITTLE_ENDIAN);
                encoding = riffEncoding(path, tagOf(file, chunks),
                        (int) number(file, chunks.body + FMT_BITS, SHORT_BYTES, LITTLE_ENDIAN));
            } else if ("data".equals(chunks.id)) {
                return audio(path, rate, channels, encoding, chunks.body, chunks.size);
            }
        }
        throw new UnsupportedModuleException(path, "no samples in the wave file");
    }

    /**
     * An extensible format hides the real tag in the first two bytes of a subformat GUID, so that a file with
     * more than two channels can say how they are laid out without a tag of its own.
     */
    private static int tagOf(byte[] file, Chunks chunks) {
        final int tag = (int) number(file, chunks.body + FMT_TAG, SHORT_BYTES, LITTLE_ENDIAN);
        return tag == TAG_EXTENSIBLE && chunks.size >= FMT_EXTENSIBLE
                ? (int) number(file, chunks.body + FMT_SUBFORMAT, SHORT_BYTES, LITTLE_ENDIAN)
                : tag;
    }

    private static WavEncoding riffEncoding(Path path, int tag, int bits) throws UnsupportedModuleException {
        return switch (tag) {
            case TAG_PCM -> new WavEncoding(bits, LITTLE_ENDIAN,
                    bits == Byte.SIZE ? WavEncoding.Kind.UNSIGNED : WavEncoding.Kind.SIGNED);
            case TAG_FLOAT -> new WavEncoding(bits, LITTLE_ENDIAN, WavEncoding.Kind.FLOAT);
            case TAG_MU_LAW -> new WavEncoding(Byte.SIZE, LITTLE_ENDIAN, WavEncoding.Kind.MU_LAW);
            default -> throw new UnsupportedModuleException(path, "unplayable wave encoding " + tag);
        };
    }

    private static WavAudio aiff(Path path, byte[] file) throws UnsupportedModuleException {
        int rate = 0;
        int channels = 0;
        int frames = 0;
        int from = 0;
        int bytes = 0;
        WavEncoding encoding = null;
        for (final Chunks chunks = new Chunks(file, BIG_ENDIAN); chunks.next();) {
            if ("COMM".equals(chunks.id) && chunks.size >= COMM_MINIMUM) {
                channels = (int) number(file, chunks.body + COMM_CHANNELS, SHORT_BYTES, BIG_ENDIAN);
                frames = (int) number(file, chunks.body + COMM_FRAMES, INT_BYTES, BIG_ENDIAN);
                rate = extendedRate(file, chunks.body + COMM_RATE);
                encoding = aiffEncoding(path, compressionOf(file, chunks),
                        (int) number(file, chunks.body + COMM_BITS, SHORT_BYTES, BIG_ENDIAN));
            } else if ("SSND".equals(chunks.id) && chunks.size >= SSND_HEADER) {
                final int offset = (int) number(file, chunks.body, INT_BYTES, BIG_ENDIAN);
                from = chunks.body + SSND_HEADER + offset;
                bytes = chunks.size - SSND_HEADER - offset;
            }
        }
        if (from + bytes > file.length || bytes <= 0) {
            throw new UnsupportedModuleException(path, "no samples in the AIFF file");
        }
        return audio(path, rate, channels, encoding, from, Math.min(bytes, framed(frames, channels, encoding)));
    }

    /**
     * Plain AIFF has no compression field and is always uncompressed; only AIFC adds one.
     */
    private static String compressionOf(byte[] file, Chunks chunks) {
        return chunks.size >= COMM_COMPRESSED ? text(file, chunks.body + COMM_COMPRESSION) : "NONE";
    }

    private static WavEncoding aiffEncoding(Path path, String compression, int bits)
            throws UnsupportedModuleException {
        return switch (compression) {
            case "NONE", "twos" -> new WavEncoding(bits, BIG_ENDIAN, WavEncoding.Kind.SIGNED);
            case "sowt" -> new WavEncoding(bits, LITTLE_ENDIAN, WavEncoding.Kind.SIGNED);
            case "raw " -> new WavEncoding(bits, BIG_ENDIAN, WavEncoding.Kind.UNSIGNED);
            case "fl32", "FL32" -> new WavEncoding(Float.SIZE, BIG_ENDIAN, WavEncoding.Kind.FLOAT);
            case "ulaw", "ULAW" -> new WavEncoding(Byte.SIZE, BIG_ENDIAN, WavEncoding.Kind.MU_LAW);
            default -> throw new UnsupportedModuleException(path, "unplayable AIFF compression " + compression);
        };
    }

    private static WavAudio au(Path path, byte[] file) throws UnsupportedModuleException {
        if (file.length < AU_HEADER) {
            throw new UnsupportedModuleException(path, "the AU header is cut short");
        }
        final int from = (int) number(file, AU_DATA_FROM, INT_BYTES, BIG_ENDIAN);
        final long declared = number(file, AU_DATA_SIZE, INT_BYTES, BIG_ENDIAN);
        if (from < AU_HEADER || from > file.length) {
            throw new UnsupportedModuleException(path, "the AU header points past the file");
        }
        return audio(path, (int) number(file, AU_RATE, INT_BYTES, BIG_ENDIAN),
                (int) number(file, AU_CHANNELS, INT_BYTES, BIG_ENDIAN),
                auEncoding(path, (int) number(file, AU_ENCODING, INT_BYTES, BIG_ENDIAN)), from,
                (int) Math.min(declared == AU_SIZE_UNKNOWN ? file.length : declared, file.length - from));
    }

    private static WavEncoding auEncoding(Path path, int encoding) throws UnsupportedModuleException {
        return switch (encoding) {
            case AU_MU_LAW -> new WavEncoding(Byte.SIZE, BIG_ENDIAN, WavEncoding.Kind.MU_LAW);
            case AU_EIGHT_BIT -> new WavEncoding(Byte.SIZE, BIG_ENDIAN, WavEncoding.Kind.SIGNED);
            case AU_SIXTEEN_BIT -> new WavEncoding(Short.SIZE, BIG_ENDIAN, WavEncoding.Kind.SIGNED);
            case AU_TWENTY_FOUR_BIT -> new WavEncoding(TWENTY_FOUR_BITS, BIG_ENDIAN, WavEncoding.Kind.SIGNED);
            case AU_THIRTY_TWO_BIT -> new WavEncoding(Integer.SIZE, BIG_ENDIAN, WavEncoding.Kind.SIGNED);
            case AU_FLOAT -> new WavEncoding(Float.SIZE, BIG_ENDIAN, WavEncoding.Kind.FLOAT);
            default -> throw new UnsupportedModuleException(path, "unplayable AU encoding " + encoding);
        };
    }

    private static WavAudio audio(Path path, int rate, int channels, WavEncoding encoding, int from, int bytes)
            throws UnsupportedModuleException {
        if (encoding == null) {
            throw new UnsupportedModuleException(path, "the audio file never says how it is encoded");
        }
        if (!encoding.isPlayable()) {
            throw new UnsupportedModuleException(path, "unplayable audio of " + encoding.describe());
        }
        if (rate <= 0 || channels <= 0 || bytes < channels * encoding.bytes()) {
            throw new UnsupportedModuleException(path, "no samples in the audio file");
        }
        return new WavAudio(rate, channels, encoding, from, bytes);
    }

    private static int framed(int frames, int channels, WavEncoding encoding) {
        return encoding == null ? 0 : frames * channels * encoding.bytes();
    }

    /**
     * The 80-bit extended float of the Motorola parts AIFF grew up on: a sign, fifteen bits of biased exponent
     * and a mantissa whose leading one is written out rather than implied.
     */
    private static int extendedRate(byte[] file, int at) {
        final int exponent = (int) number(file, at, SHORT_BYTES, BIG_ENDIAN) - EXTENDED_BIAS;
        final long mantissa = number(file, at + SHORT_BYTES, EXTENDED_BYTES, BIG_ENDIAN);
        final int shift = EXTENDED_MANTISSA - exponent;
        return shift < 0 || shift >= Long.SIZE ? 0 : (int) (mantissa >>> shift);
    }

    private static long number(byte[] file, int at, int bytes, ByteOrder order) {
        final boolean big = order == BIG_ENDIAN;
        long value = 0;
        for (int index = 0; index < bytes; index++) {
            value = value << Byte.SIZE | (file[at + (big ? index : bytes - 1 - index)] & BYTE_MASK);
        }
        return value;
    }

    private static String text(byte[] file, int at) {
        return new String(file, at, ID_LENGTH, StandardCharsets.US_ASCII);
    }

    /**
     * Walks the chunks of a RIFF or IFF file, clamping one that claims to run past the end of the file so a
     * truncated download is read as far as it goes instead of throwing.
     */
    private static final class Chunks {

        private final byte[] file;
        private final ByteOrder order;

        private String id;
        private int body;
        private int size;
        private int at = FORM_HEADER;

        private Chunks(byte[] file, ByteOrder order) {
            this.file = file;
            this.order = order;
        }

        private boolean next() {
            if (at + CHUNK_HEADER > file.length) {
                return false;
            }
            id = text(file, at);
            body = at + CHUNK_HEADER;
            size = (int) Math.min(number(file, at + ID_LENGTH, INT_BYTES, order), file.length - body);
            at = body + size + (size & 1);
            return true;
        }
    }
}

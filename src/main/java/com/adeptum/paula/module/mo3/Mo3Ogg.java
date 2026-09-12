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
 * How a shared header is joined to the sample that borrows it follows
 * Load_mo3.cpp of OpenMPT, Copyright © 2004-2026 the OpenMPT project
 * developers and Copyright © 1997-2003 Olivier Lapicque, licensed under the
 * three-clause BSD licence and used here under the GNU General Public
 * License.
 */

package com.adeptum.paula.module.mo3;

import de.quippy.ogg.jogg.Packet;
import de.quippy.ogg.jogg.Page;
import de.quippy.ogg.jogg.StreamState;
import de.quippy.ogg.jogg.SyncState;
import de.quippy.ogg.jorbis.Block;
import de.quippy.ogg.jorbis.Comment;
import de.quippy.ogg.jorbis.DspState;
import de.quippy.ogg.jorbis.Info;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Unpacks a waveform an MO3 kept as Ogg Vorbis.
 *
 * <p>What a Vorbis decoder needs before it can sound anything is the same for every sample encoded in one
 * sitting, so the format lets a run of samples share one copy of it and each carry only its own sound. Playing
 * one of those means putting the shared beginning back in front of the sample's own pages, which the two were
 * never written to sit beside: every page names the stream it belongs to and is checked against a sum over
 * itself, so the borrowed pages are renamed to the borrower's stream and their sums worked out again.</p>
 */
final class Mo3Ogg {

    private static final byte[] PAGE_MAGIC = {'O', 'g', 'g', 'S'};
    private static final int SERIAL_AT = 14;
    private static final int CHECKSUM_AT = 22;
    private static final int SEGMENTS_AT = 26;
    private static final int PAGE_HEADER_LENGTH = 27;

    private static final int HEADER_PACKETS = 3;
    private static final int PAGE_READY = 1;
    private static final int PAGE_HUNGRY = 0;
    private static final int PACKET_HUNGRY = 0;

    private static final float PEAK = 32767.0f;

    /**
     * The sum every Ogg page carries over itself, worked out a byte at a time from the top down.
     */
    private static final int[] CHECKSUMS = checksums();

    private Mo3Ogg() {
    }

    /**
     * Decodes a whole Ogg Vorbis stream into the waveform, filling as much of it as the stream reaches.
     */
    static void unpack(byte[] stream, int from, int length, int[][] waveform) {
        final SyncState sync = new SyncState();
        final StreamState pages = new StreamState();
        final Page page = new Page();
        final Packet packet = new Packet();
        final Info info = new Info();
        final Comment comment = new Comment();

        sync.init();
        info.init();
        comment.init();
        feed(sync, stream, from, length);

        try {
            if (!readHeaders(sync, pages, page, packet, info, comment)) {
                return;
            }
            final DspState dsp = new DspState();
            dsp.synthesis_init(info);
            sound(sync, dsp, new Block(dsp), pages, page, packet, info, waveform);
        } catch (RuntimeException broken) {
            // A stream that will not decode is played as far as it reached and silent after it.
        }
    }

    private static void feed(SyncState sync, byte[] stream, int from, int length) {
        final int index = sync.buffer(length);
        System.arraycopy(stream, from, sync.data, index, length);
        sync.wrote(length);
    }

    private static boolean readHeaders(SyncState sync, StreamState pages, Page page, Packet packet, Info info,
            Comment comment) {
        int read = 0;
        while (read < HEADER_PACKETS) {
            if (sync.pageout(page) != PAGE_READY) {
                return false;
            }
            if (read == 0) {
                pages.init(page.serialno());
            }
            pages.pagein(page);
            while (read < HEADER_PACKETS) {
                final int status = pages.packetout(packet);
                if (status == PACKET_HUNGRY) {
                    break;
                }
                if (status < 0 || info.synthesis_headerin(comment, packet) < 0) {
                    return false;
                }
                read++;
            }
        }
        return true;
    }

    private static void sound(SyncState sync, DspState dsp, Block block, StreamState pages, Page page,
            Packet packet, Info info, int[][] waveform) {
        final float[][][] pcm = new float[1][][];
        final int[] index = new int[info.channels];
        int written = 0;
        while (written < waveform[0].length) {
            final int status = pages.packetout(packet);
            if (status == PACKET_HUNGRY) {
                if (!nextPage(sync, pages, page)) {
                    return;
                }
                continue;
            }
            if (status < 0) {
                continue;
            }
            if (block.synthesis(packet) == 0) {
                dsp.synthesis_blockin(block);
            }
            written = drain(dsp, info, waveform, written, pcm, index);
        }
    }

    private static boolean nextPage(SyncState sync, StreamState pages, Page page) {
        while (true) {
            final int status = sync.pageout(page);
            if (status == PAGE_READY) {
                pages.pagein(page);
                return true;
            }
            if (status == PAGE_HUNGRY) {
                return false;
            }
        }
    }

    private static int drain(DspState dsp, Info info, int[][] waveform, int written, float[][][] pcm,
            int[] index) {
        int frames;
        while ((frames = dsp.synthesis_pcmout(pcm, index)) > 0) {
            final int kept = Math.min(frames, waveform[0].length - written);
            for (int channel = 0; channel < waveform.length; channel++) {
                final float[] values = pcm[0][Math.min(channel, info.channels - 1)];
                final int from = index[Math.min(channel, info.channels - 1)];
                for (int frame = 0; frame < kept; frame++) {
                    waveform[channel][written + frame] = clamped(values[from + frame]);
                }
            }
            written += kept;
            dsp.synthesis_read(frames);
            if (written >= waveform[0].length) {
                break;
            }
        }
        return written;
    }

    private static int clamped(float value) {
        return Math.clamp(Math.round(value * PEAK), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    /**
     * Puts the pages of a shared beginning in front of a sample's own, renamed to the stream those belong to
     * and with the sum each page carries worked out again.
     */
    static byte[] shared(byte[] file, int headerAt, int headerLength, int dataAt, int dataLength) {
        final int serial = serialOf(file, dataAt, dataLength);
        final ByteArrayOutputStream merged = new ByteArrayOutputStream(headerLength + dataLength);
        int at = headerAt;
        final int end = headerAt + Math.min(headerLength, file.length - headerAt);
        while (at < end) {
            final int length = pageLength(file, at, end);
            if (length <= 0) {
                break;
            }
            final byte[] page = Arrays.copyOfRange(file, at, at + length);
            renameStream(page, serial);
            merged.writeBytes(page);
            at += length;
        }
        merged.write(file, dataAt, dataLength);
        return merged.toByteArray();
    }

    private static int serialOf(byte[] file, int at, int length) {
        return pageLength(file, at, at + length) > 0 ? intAt(file, at + SERIAL_AT) : 0;
    }

    private static void renameStream(byte[] page, int serial) {
        putInt(page, SERIAL_AT, serial);
        putInt(page, CHECKSUM_AT, 0);
        putInt(page, CHECKSUM_AT, checksum(page));
    }

    private static int pageLength(byte[] file, int at, int end) {
        if (at + PAGE_HEADER_LENGTH > end || !matchesMagic(file, at)) {
            return 0;
        }
        final int segments = file[at + SEGMENTS_AT] & 0xFF;
        if (at + PAGE_HEADER_LENGTH + segments > end) {
            return 0;
        }
        int payload = 0;
        for (int segment = 0; segment < segments; segment++) {
            payload += file[at + PAGE_HEADER_LENGTH + segment] & 0xFF;
        }
        final int length = PAGE_HEADER_LENGTH + segments + payload;
        return at + length <= end ? length : 0;
    }

    private static boolean matchesMagic(byte[] file, int at) {
        for (int byteAt = 0; byteAt < PAGE_MAGIC.length; byteAt++) {
            if (file[at + byteAt] != PAGE_MAGIC[byteAt]) {
                return false;
            }
        }
        return true;
    }

    private static int checksum(byte[] page) {
        int sum = 0;
        for (final byte value : page) {
            sum = sum << Byte.SIZE ^ CHECKSUMS[(sum >>> 24 ^ value) & 0xFF];
        }
        return sum;
    }

    private static int[] checksums() {
        final int[] table = new int[0x100];
        for (int at = 0; at < table.length; at++) {
            int sum = at << 24;
            for (int bit = 0; bit < Byte.SIZE; bit++) {
                sum = (sum & 0x8000_0000) != 0 ? sum << 1 ^ 0x04c1_1db7 : sum << 1;
            }
            table[at] = sum;
        }
        return table;
    }

    private static int intAt(byte[] file, int at) {
        return file[at] & 0xFF | (file[at + 1] & 0xFF) << 8 | (file[at + 2] & 0xFF) << 16 | file[at + 3] << 24;
    }

    private static void putInt(byte[] file, int at, int value) {
        for (int byteAt = 0; byteAt < Integer.BYTES; byteAt++) {
            file[at + byteAt] = (byte) (value >> byteAt * Byte.SIZE);
        }
    }
}

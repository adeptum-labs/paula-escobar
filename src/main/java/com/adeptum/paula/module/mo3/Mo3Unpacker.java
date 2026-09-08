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
 * The decompression follows Load_mo3.cpp of OpenMPT, Copyright © 2004-2026
 * the OpenMPT project developers and Copyright © 1997-2003 Olivier
 * Lapicque, licensed under the three-clause BSD licence and used here under
 * the GNU General Public License. Those routines came in turn from Laurent
 * Clévy's unmo3 and were relicensed with his permission.
 */

package com.adeptum.paula.module.mo3;

import java.io.EOFException;
import java.io.IOException;

/**
 * Unpacks the music chunk of an MO3 file. The stream carries one control bit per decision, packed into bytes
 * that are read as they run out: a zero bit copies the next byte through, a one bit repeats a string of bytes
 * already written, at an offset and length that are themselves coded in control bits.
 *
 * <p>The offset window reaches back over everything written so far and nothing bounds how far a short stream
 * can expand, so the caller states the unpacked length up front and the stream is refused if it does not
 * reach exactly that.</p>
 */
final class Mo3Unpacker {

    private final Mo3Bits bits;
    private final byte[] unpacked;
    private int written;
    private int stringOffset;

    private Mo3Unpacker(byte[] packed, int from, int unpackedLength) {
        this.bits = new Mo3Bits(packed, from, packed.length - from);
        this.unpacked = new byte[unpackedLength];
    }

    /**
     * Unpacks {@code unpackedLength} bytes starting at {@code from}, refusing a stream that runs out or points
     * outside what it has written.
     */
    static Unpacked unpack(byte[] packed, int from, int unpackedLength) throws IOException {
        final Mo3Unpacker unpacker = new Mo3Unpacker(packed, from, unpackedLength);
        unpacker.run();
        return new Unpacked(unpacker.unpacked, unpacker.bits.position());
    }

    private void run() throws IOException {
        unpacked[written++] = bits.next();
        while (written < unpacked.length) {
            if (!bits.bit()) {
                unpacked[written++] = bits.next();
            } else {
                repeatString();
            }
        }
    }

    private void repeatString() throws IOException {
        int adjust = 0;
        int length = codedLength() - 3;
        if (length < 0) {
            length++;
        } else {
            stringOffset = ~(length << Byte.SIZE | bits.next() & 0xFF);
            length = 0;
            if (stringOffset < -1280) {
                adjust++;
            }
            adjust++;
            if (stringOffset < -32000) {
                adjust++;
            }
        }
        if (stringOffset >= 0 || written + stringOffset < 0) {
            throw new EOFException("The compressed music repeats a string from before the module began");
        }

        length = length << 1 | bits.digit();
        length = length << 1 | bits.digit();
        if (length == 0) {
            length = codedLength() + 2;
        }
        length += adjust;
        if (length <= 0 || length > unpacked.length - written) {
            throw new EOFException("The compressed music repeats more than it says it unpacks to");
        }

        int from = written + stringOffset;
        for (int copied = 0; copied < length; copied++) {
            unpacked[written++] = unpacked[from++];
        }
    }

    /**
     * A length coded in the control stream: a leading one, then the first bit of every pair until a pair ends
     * in a zero.
     */
    private int codedLength() throws IOException {
        int length = 1;
        do {
            length = length << 1 | bits.digit();
        } while (bits.bit());
        return length;
    }

    /**
     * The unpacked music chunk and where the stream that held it ended, which is where the sample data starts
     * in the files that do not say.
     */
    record Unpacked(byte[] music, int end) {
    }
}

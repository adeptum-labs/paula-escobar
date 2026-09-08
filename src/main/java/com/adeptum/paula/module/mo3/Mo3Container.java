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
 * The layout follows Load_mo3.cpp of OpenMPT, Copyright © 2004-2026 the
 * OpenMPT project developers and Copyright © 1997-2003 Olivier Lapicque,
 * licensed under the three-clause BSD licence and used here under the GNU
 * General Public License.
 */

package com.adeptum.paula.module.mo3;

import java.io.IOException;
import java.util.Arrays;

/**
 * The two halves of an MO3 file: the compressed music, which holds everything but the waveforms, and the
 * sample data that follows it uncompressed by this scheme and compressed by its own.
 *
 * <p>From version five the file says how long the compressed half is, so the sample data can be found without
 * unpacking; the older files do not, and there the music has to be unpacked to learn where it ended.</p>
 */
record Mo3Container(int version, byte[] music, int sampleData) {

    private static final byte[] MAGIC = {'M', 'O', '3'};
    private static final int NEWEST_VERSION = 5;
    private static final int STATED_COMPRESSED_SIZE_FROM = 5;

    /**
     * The music of an MO3 is unbounded in how far it can expand, so a file claiming more than half a gigabyte
     * of it is a corruption rather than a module.
     */
    private static final int LARGEST_MUSIC = 0x2000_0000;

    /**
     * The best ratio found in a real module is around twenty to one, so a file claiming to unpack to more than
     * this much of what it has left is asking for room it will never fill.
     */
    private static final int LARGEST_EXPANSION = 64;

    static Mo3Container read(byte[] file) throws IOException {
        final Mo3Bytes bytes = new Mo3Bytes(file);
        if (!Arrays.equals(bytes.bytes(MAGIC.length), MAGIC)) {
            throw new IOException("Not an MO3 module");
        }
        final int version = bytes.u8();
        if (version > NEWEST_VERSION) {
            throw new IOException("MO3 version " + version + " is newer than this reads");
        }
        final int musicSize = bytes.u32();
        if (musicSize == 0 || musicSize >= LARGEST_MUSIC
                || musicSize > (long) LARGEST_EXPANSION * bytes.remaining()) {
            throw new IOException("The MO3 states " + musicSize + " bytes of music, which is no module");
        }

        if (version < STATED_COMPRESSED_SIZE_FROM) {
            final Mo3Unpacker.Unpacked unpacked = Mo3Unpacker.unpack(file, bytes.position(), musicSize);
            return new Mo3Container(version, unpacked.music(), unpacked.end());
        }
        final int compressedSize = bytes.u32();
        if (!bytes.has(compressedSize)) {
            throw new IOException("The MO3 ends in the middle of its compressed music");
        }
        return new Mo3Container(version, Mo3Unpacker.unpack(file, bytes.position(), musicSize).music(),
                bytes.position() + compressedSize);
    }
}

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

package com.adeptum.paula.module.zx;

import com.adeptum.paula.module.UnsupportedModuleException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads a tape file: blocks of a little-endian length, a flag byte, the data and an exclusive-or checksum. The file
 * must divide into blocks exactly, which is what tells it from the raw Commodore tape images that share the
 * extension. A checksum that does not match is noted and let through, as the ROM would refuse the block but a
 * player is better served by what is there.
 */
@Slf4j
final class TapReader {

    private static final int LENGTH_FIELD = 2;
    private static final int FLAG_AND_CHECKSUM = 2;

    private TapReader() {
    }

    static TapFile read(Path path, byte[] bytes) throws UnsupportedModuleException {
        final List<TapBlock> blocks = new ArrayList<>();
        int at = 0;
        while (at < bytes.length) {
            if (at + LENGTH_FIELD > bytes.length) {
                throw notATape(path);
            }
            final int length = (bytes[at] & 0xFF) | (bytes[at + 1] & 0xFF) << 8;
            final int end = at + LENGTH_FIELD + length;
            if (length < FLAG_AND_CHECKSUM || end > bytes.length) {
                throw notATape(path);
            }
            blocks.add(block(bytes, at + LENGTH_FIELD, end, path));
            at = end;
        }
        if (blocks.isEmpty()) {
            throw notATape(path);
        }
        return new TapFile(blocks);
    }

    private static TapBlock block(byte[] bytes, int from, int end, Path path) {
        int checksum = 0;
        for (int i = from; i < end; i++) {
            checksum ^= bytes[i] & 0xFF;
        }
        if (checksum != 0) {
            log.debug("Checksum mismatch in a block of {}", path);
        }
        return new TapBlock(bytes[from] & 0xFF, Arrays.copyOfRange(bytes, from + 1, end - 1));
    }

    private static UnsupportedModuleException notATape(Path path) {
        return new UnsupportedModuleException(path, "not a ZX Spectrum tape");
    }
}

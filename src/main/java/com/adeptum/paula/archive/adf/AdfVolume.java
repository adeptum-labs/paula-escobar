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

package com.adeptum.paula.archive.adf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An AmigaDOS volume held whole in memory. Every block is 512 bytes, and a directory is a hash table of 72
 * slots whose chains are followed to find what it holds; the root block sits at half the disk's block count on
 * every floppy, DD and HD alike. Checksums are not enforced, since real dumps and hand-written boot blocks
 * often carry wrong ones without the disk being any less readable; a block that fails to make sense is passed
 * over instead, the same tolerance the Commodore disk and tape readers already give theirs.
 */
final class AdfVolume {

    private static final int BLOCK = 512;
    private static final int FLAGS_AT = 3;
    private static final int FFS_FLAG = 0x01;

    private static final int TYPE_AT = 0;
    private static final int TYPE_HEADER = 2;

    private static final int HIGH_SEQ_AT = 8;
    private static final int DATA_BLOCKS_AT = 24;
    private static final int DATA_BLOCKS_COUNT = 72;
    private static final int HASH_TABLE_AT = 24;
    private static final int HASH_TABLE_SIZE = 72;
    private static final int HASH_CHAIN_AT = BLOCK - 16;
    private static final int REAL_ENTRY_AT = BLOCK - 44;
    private static final int BYTE_SIZE_AT = BLOCK - 188;
    private static final int NAME_LENGTH_AT = BLOCK - 80;
    private static final int EXTENSION_AT = BLOCK - 8;
    private static final int SEC_TYPE_AT = BLOCK - 4;

    private static final int ST_USERDIR = 2;
    private static final int ST_LINKDIR = 4;
    private static final int ST_FILE = -3;
    private static final int ST_LINKFILE = -4;

    private static final int OFS_HEADER_LENGTH = 24;
    private static final int OFS_DATA_MAX = BLOCK - OFS_HEADER_LENGTH;

    private final byte[] image;
    private final boolean ffs;
    private final int rootBlock;

    AdfVolume(byte[] image) {
        this.image = image;
        this.ffs = (image[FLAGS_AT] & FFS_FLAG) != 0;
        this.rootBlock = image.length / BLOCK / 2;
    }

    List<AdfFile> list() {
        final List<AdfFile> files = new ArrayList<>();
        walk(rootBlock, "", files, new HashSet<>(), new HashSet<>());
        return files;
    }

    /**
     * A file longer than 72 data blocks keeps going in an extension block of the same shape, so a file's
     * content is read as if its header and every extension after it were one long array of pointers.
     */
    byte[] read(AdfFile file) {
        final byte[] content = new byte[file.size()];
        int written = 0;
        for (final int pointer : dataBlocks(file.header())) {
            if (written >= content.length || !inRange(pointer)) {
                break;
            }
            final int at = ffs ? address(pointer, 0) : address(pointer, OFS_HEADER_LENGTH);
            final int chunk = Math.min(ffs ? BLOCK : OFS_DATA_MAX, content.length - written);
            System.arraycopy(image, at, content, written, chunk);
            written += chunk;
        }
        return written == content.length ? content : Arrays.copyOf(content, written);
    }

    /**
     * Two guards are kept apart on purpose: one against a directory being walked twice, the other against a
     * hash chain looping back on an entry already seen in it. The same block number can rightly appear in
     * both roles one after another — discovered as an entry, then walked as the directory it names — and a
     * single set covering both would mistake that legitimate first visit for a repeat.
     */
    private void walk(int directory, String prefix, List<AdfFile> files, Set<Integer> visitedDirs,
            Set<Integer> visitedEntries) {
        if (!inRange(directory) || !visitedDirs.add(directory) || type(directory) != TYPE_HEADER) {
            return;
        }
        for (int slot = 0; slot < HASH_TABLE_SIZE; slot++) {
            int entry = u32(directory, HASH_TABLE_AT + slot * Integer.BYTES);
            while (entry != 0 && inRange(entry) && visitedEntries.add(entry)) {
                visit(entry, prefix, files, visitedDirs, visitedEntries);
                entry = u32(entry, HASH_CHAIN_AT);
            }
        }
    }

    private void visit(int header, String prefix, List<AdfFile> files, Set<Integer> visitedDirs,
            Set<Integer> visitedEntries) {
        if (type(header) != TYPE_HEADER) {
            return;
        }
        final String name = AmigaNames.of(image, address(header, NAME_LENGTH_AT));
        switch (secType(header)) {
            case ST_FILE -> files.add(new AdfFile(prefix + name, header, fileSize(header)));
            case ST_USERDIR -> walk(header, prefix + name + "/", files, visitedDirs, visitedEntries);
            case ST_LINKFILE -> resolveLink(header)
                    .ifPresent(real -> files.add(new AdfFile(prefix + name, real, fileSize(real))));
            case ST_LINKDIR -> resolveLink(header)
                    .ifPresent(real -> walk(real, prefix + name + "/", files, visitedDirs, visitedEntries));
            default -> {
                // a soft link names a path rather than holding data, and anything else is not an entry at all
            }
        }
    }

    private Optional<Integer> resolveLink(int header) {
        final int real = u32(header, REAL_ENTRY_AT);
        return inRange(real) && real != header && type(real) == TYPE_HEADER ? Optional.of(real) : Optional.empty();
    }

    private List<Integer> dataBlocks(int header) {
        final List<Integer> pointers = new ArrayList<>();
        final Set<Integer> visited = new HashSet<>();
        int block = header;
        while (inRange(block) && visited.add(block)) {
            final int used = Math.min(u32(block, HIGH_SEQ_AT), DATA_BLOCKS_COUNT);
            for (int index = 0; index < used; index++) {
                final int slot = DATA_BLOCKS_COUNT - 1 - index;
                final int pointer = u32(block, DATA_BLOCKS_AT + slot * Integer.BYTES);
                if (pointer != 0) {
                    pointers.add(pointer);
                }
            }
            final int extension = u32(block, EXTENSION_AT);
            if (extension == 0) {
                break;
            }
            block = extension;
        }
        return pointers;
    }

    private int fileSize(int header) {
        final long size = u32(header, BYTE_SIZE_AT) & 0xFFFFFFFFL;
        return (int) Math.min(size, image.length);
    }

    private boolean inRange(int block) {
        final long at = (long) block * BLOCK;
        return block >= 0 && at + BLOCK <= image.length;
    }

    private int type(int block) {
        return u32(block, TYPE_AT);
    }

    private int secType(int block) {
        return u32(block, SEC_TYPE_AT);
    }

    private int address(int block, int offset) {
        return block * BLOCK + offset;
    }

    private int u32(int block, int offset) {
        final int at = address(block, offset);
        return (image[at] & 0xFF) << 24 | (image[at + 1] & 0xFF) << 16
                | (image[at + 2] & 0xFF) << 8 | image[at + 3] & 0xFF;
    }
}

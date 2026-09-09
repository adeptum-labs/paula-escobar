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

package com.adeptum.paula.image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Writes a picture of two colours out as a PNG, one bit to the pixel against a palette of its own. Written by
 * hand for the same reason the wave header is: the drawing this player does needs no more than this, and a
 * native image has no graphics of its own to lean on.
 */
public final class Png {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int HEADER_LENGTH = 13;
    private static final int PALETTED = 3;
    private static final int ONE_BIT = 1;
    private static final int PIXELS_PER_BYTE = 8;
    private static final int NO_FILTER = 0;

    private Png() {
    }

    /**
     * The rows of a picture as a PNG, each row one byte to eight pixels with the leftmost pixel in the highest
     * bit. A pixel that is set is drawn in the ink colour and one that is not in the ground colour, both given
     * as {@code 0xRRGGBB}.
     */
    public static byte[] of(byte[][] rows, int width, int ground, int ink) {
        final ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(SIGNATURE);
        chunk(png, "IHDR", ByteBuffer.allocate(HEADER_LENGTH).putInt(width).putInt(rows.length)
                .put((byte) ONE_BIT).put((byte) PALETTED).put(new byte[3]).array());
        chunk(png, "PLTE", new byte[] {red(ground), green(ground), blue(ground), red(ink), green(ink), blue(ink)});
        chunk(png, "IDAT", deflated(rows, width));
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static byte[] deflated(byte[][] rows, int width) {
        final byte[] raw = new byte[rows.length * (1 + (width + PIXELS_PER_BYTE - 1) / PIXELS_PER_BYTE)];
        int at = 0;
        for (final byte[] row : rows) {
            raw[at++] = NO_FILTER;
            System.arraycopy(row, 0, raw, at, row.length);
            at += row.length;
        }
        final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            final ByteArrayOutputStream packed = new ByteArrayOutputStream();
            final byte[] chunk = new byte[8192];
            while (!deflater.finished()) {
                packed.write(chunk, 0, deflater.deflate(chunk));
            }
            return packed.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void chunk(ByteArrayOutputStream png, String name, byte[] content) {
        final byte[] labelled = new byte[4 + content.length];
        System.arraycopy(name.getBytes(StandardCharsets.US_ASCII), 0, labelled, 0, 4);
        System.arraycopy(content, 0, labelled, 4, content.length);
        final CRC32 crc = new CRC32();
        crc.update(labelled);
        png.writeBytes(ByteBuffer.allocate(4).putInt(content.length).array());
        png.writeBytes(labelled);
        png.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static byte red(int colour) {
        return (byte) (colour >> 16);
    }

    private static byte green(int colour) {
        return (byte) (colour >> 8);
    }

    private static byte blue(int colour) {
        return (byte) colour;
    }
}

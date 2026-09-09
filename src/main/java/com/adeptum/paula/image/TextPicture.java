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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Draws the text art a release carries as a picture, so a screen on the network can show what the scene drew
 * rather than nothing at all. The art is set in the code page it was drawn in, in the eight by eight font of
 * the machines it was drawn on, and blown up whole pixels at a time to fill the picture.
 */
public final class TextPicture {

    private static final String FONT = "/cp437-8x8.bin";
    private static final int GLYPH = 8;
    private static final int PIXELS_PER_BYTE = 8;
    private static final int CODE_PAGE_HALF = 0x80;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int MARGIN = 32;
    private static final int GROUND = 0x0A0A12;
    private static final int INK = 0xE8B25A;

    /**
     * The upper half of the code page the art is read in, so a character can be put back where it came from.
     */
    private static final String HIGH_HALF =
            "ÇüéâäàåçêëèïîìÄÅ"
            + "ÉæÆôöòûùÿÖÜ¢£¥₧ƒ"
            + "áíóúñÑªº¿⌐¬½¼¡«»"
            + "░▒▓│┤╡╢╖╕╣║╗╝╜╛┐"
            + "└┴┬├─┼╞╟╚╔╩╦╠═╬╧"
            + "╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀"
            + "αßΓπΣσµτΦΘΩδ∞φε∩"
            + "≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ ";

    private static final byte[] GLYPHS = font();

    private TextPicture() {
    }

    /**
     * The art as a PNG on a canvas of a size a screen is happy with, centred and as large as it will go. Empty
     * art gives nothing to show.
     */
    public static byte[] of(List<String> art) {
        final int columns = art.stream().mapToInt(String::length).max().orElse(0);
        if (columns == 0) {
            return new byte[0];
        }
        final int scale = Math.max(1, Math.min((WIDTH - 2 * MARGIN) / (columns * GLYPH),
                (HEIGHT - 2 * MARGIN) / (art.size() * GLYPH)));
        final int left = (WIDTH - columns * GLYPH * scale) / 2;
        final int top = (HEIGHT - art.size() * GLYPH * scale) / 2;
        final byte[][] rows = new byte[HEIGHT][(WIDTH + PIXELS_PER_BYTE - 1) / PIXELS_PER_BYTE];
        for (int line = 0; line < art.size(); line++) {
            draw(rows, art.get(line), left, top + line * GLYPH * scale, scale);
        }
        return Png.of(rows, WIDTH, GROUND, INK);
    }

    private static void draw(byte[][] rows, String line, int left, int top, int scale) {
        for (int column = 0; column < line.length(); column++) {
            final int glyph = indexOf(line.charAt(column)) * GLYPH;
            for (int y = 0; y < GLYPH; y++) {
                final int bits = GLYPHS[glyph + y] & 0xFF;
                for (int x = 0; x < GLYPH; x++) {
                    if ((bits & 1 << x) != 0) {
                        fill(rows, left + (column * GLYPH + x) * scale, top + y * scale, scale);
                    }
                }
            }
        }
    }

    private static void fill(byte[][] rows, int left, int top, int scale) {
        for (int y = top; y < top + scale && y < rows.length; y++) {
            for (int x = left; x < left + scale && x < WIDTH; x++) {
                rows[y][x / PIXELS_PER_BYTE] |= (byte) (0x80 >> x % PIXELS_PER_BYTE);
            }
        }
    }

    /**
     * Where a character sits in the code page, which is where its glyph sits in the font; anything the code
     * page never held is drawn as a space.
     */
    private static int indexOf(char character) {
        if (character < CODE_PAGE_HALF) {
            return character;
        }
        final int high = HIGH_HALF.indexOf(character);
        return high < 0 ? ' ' : CODE_PAGE_HALF + high;
    }

    private static byte[] font() {
        try (InputStream glyphs = TextPicture.class.getResourceAsStream(FONT)) {
            if (glyphs == null) {
                throw new IOException("The font " + FONT + " is missing from the build");
            }
            return glyphs.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

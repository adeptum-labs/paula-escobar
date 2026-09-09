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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TextPictureTest {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Test
    void drawsNothingForArtThatHasNoCharacters() {
        assertEquals(0, TextPicture.of(List.of()).length);
        assertEquals(0, TextPicture.of(List.of("", "")).length);
    }

    /**
     * Nothing in the player reads a PNG back, so the one reader in the tests stands in for the screen that
     * has to make sense of it.
     */
    @Test
    void writesAPictureAReaderCanMakeSenseOf() throws IOException {
        final byte[] png = TextPicture.of(List.of("Paula", "▓▓▓▓▓"));

        assertArrayEquals(SIGNATURE, Arrays.copyOf(png, SIGNATURE.length), "which a reader knows it by");
        final BufferedImage picture = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(1280, picture.getWidth());
        assertEquals(720, picture.getHeight());
        assertTrue(inked(picture) > 0, "the art was drawn on it");
    }

    /**
     * The art is set in the code page it was drawn in, so the box and block characters of a PC release come
     * out as the shapes they were drawn as rather than as blanks.
     */
    @Test
    void setsTheBlocksOfTheCodePageAndNotOnlyLetters() throws IOException {
        final int blocks = inked(ImageIO.read(new ByteArrayInputStream(TextPicture.of(List.of("███", "███")))));
        final int letters = inked(ImageIO.read(new ByteArrayInputStream(TextPicture.of(List.of("iii", "iii")))));

        assertNotEquals(0, letters, "letters are drawn");
        assertTrue(blocks > letters * 3, "and a solid block covers far more of its cell than a letter does");
    }

    private static int inked(BufferedImage picture) {
        int inked = 0;
        for (int y = 0; y < picture.getHeight(); y++) {
            for (int x = 0; x < picture.getWidth(); x++) {
                inked += picture.getRGB(x, y) == picture.getRGB(0, 0) ? 0 : 1;
            }
        }
        return inked;
    }
}

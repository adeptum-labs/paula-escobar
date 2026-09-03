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

package com.adeptum.paula.demozoo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the text art the scene drew: the file id, information and party files that a release or a party
 * directory carries, in the code page of the machine that made them.
 */
public final class TextArt {

    /**
     * The upper half of the code page the PC scene drew in, from 0x80 up; the lower half is plain ASCII.
     */
    private static final String CODE_PAGE_437 =
            "ÇüéâäàåçêëèïîìÄÅ" +
            "ÉæÆôöòûùÿÖÜ¢£¥₧ƒ" +
            "áíóúñÑªº¿⌐¬½¼¡«»" +
            "░▒▓│┤╡╢╖╕╣║╗╝╜╛┐" +
            "└┴┬├─┼╞╟╚╔╩╦╠═╬╧" +
            "╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
            "αßΓπΣσµτΦΘΩδ∞φε∩" +
            "≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ ";

    private static final Set<String> EXTENSIONS = Set.of("diz", "nfo", "asc");
    private static final int HIGH_HALF = 0x80;
    private static final int BOX_FIRST = 0xB0;
    private static final int BOX_LAST = 0xDF;
    private static final int MOST_LINES = 12;
    private static final int FEWEST_LINES = 2;
    private static final char ESCAPE = 0x1B;

    private TextArt() {
    }

    public static boolean isArtName(String name) {
        final String lowered = name.toLowerCase(Locale.ROOT);
        final int dot = lowered.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(lowered.substring(dot + 1));
    }

    public static Optional<List<String>> read(Path file) {
        try {
            return of(Files.readAllBytes(file));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * A file carrying terminal escapes is left alone: its shape lives in the cursor moves, which a plain block
     * of text cannot hold.
     */
    public static Optional<List<String>> of(byte[] bytes) {
        final String text = decoded(bytes);
        if (text.indexOf(ESCAPE) >= 0) {
            return Optional.empty();
        }
        final List<String> lines = new ArrayList<>(text.lines().map(line -> line.replace('\t', ' ').stripTrailing()).toList());
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        while (!lines.isEmpty() && lines.getFirst().isBlank()) {
            lines.removeFirst();
        }
        return lines.size() < FEWEST_LINES ? Optional.empty() : Optional.of(List.copyOf(lines.subList(0, Math.min(lines.size(), MOST_LINES))));
    }

    /**
     * Art drawn on a PC is full of the box characters of code page 437, while art from an Amiga holds the
     * accented letters of Latin-1 in the same byte range; whichever the file leans towards is what it is read
     * as.
     */
    private static String decoded(byte[] bytes) {
        final StringBuilder text = new StringBuilder(bytes.length);
        final boolean drawsBoxes = drawsBoxes(bytes);
        for (final byte character : bytes) {
            final int value = character & 0xFF;
            text.append(drawsBoxes && value >= HIGH_HALF ? CODE_PAGE_437.charAt(value - HIGH_HALF) : (char) value);
        }
        return text.toString();
    }

    private static boolean drawsBoxes(byte[] bytes) {
        for (final byte character : bytes) {
            final int value = character & 0xFF;
            if (value >= BOX_FIRST && value <= BOX_LAST) {
                return true;
            }
        }
        return false;
    }
}

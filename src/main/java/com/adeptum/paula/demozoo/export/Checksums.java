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
package com.adeptum.paula.demozoo.export;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SHA-256 checksums of catalogues, and the one-line form {@code sha256sum} writes them in.
 */
public final class Checksums {

    private static final String DIGEST = "SHA-256";
    private static final Pattern HEX = Pattern.compile("[0-9a-f]{64}");
    private static final String SEPARATOR = "  ";

    private Checksums() {
        /* Empty on purpose */
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST).digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(DIGEST + " is missing from the runtime", e);
        }
    }

    /**
     * The checksum line for a file, as {@code sha256sum} prints it and {@code sha256sum -c} reads it back.
     */
    public static String line(String sha256, String fileName) {
        return sha256 + SEPARATOR + fileName + "\n";
    }

    /**
     * The checksum at the start of a line, whether it stands alone or is followed by the file it belongs to.
     */
    public static String parse(String text) throws IOException {
        final String stripped = text.strip();
        final int end = stripped.indexOf(' ');
        final String sha256 = (end < 0 ? stripped : stripped.substring(0, end)).toLowerCase(Locale.ROOT);
        if (!HEX.matcher(sha256).matches()) {
            throw new IOException("Not a SHA-256 checksum: " + (stripped.length() > 80 ? stripped.substring(0, 80) : stripped));
        }
        return sha256;
    }
}

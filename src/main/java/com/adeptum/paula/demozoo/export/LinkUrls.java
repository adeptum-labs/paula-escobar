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

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Builds the address of a Demozoo link from the class naming its host and the parameter that host left in the
 * export, following the templates Demozoo itself uses.
 */
public final class LinkUrls {

    private static final String SAFE_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.-~/";
    private static final String HEX_DIGITS = "0123456789ABCDEF";

    private LinkUrls() {
    }

    /**
     * The address of a link, or nothing when the class is unknown or the parameter says nothing. The scene.org
     * templates quote the parameter the way Demozoo's own Python code does; the others take it as it stands.
     */
    public static Optional<String> url(String linkClass, String parameter) {
        if (linkClass == null || parameter == null || parameter.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (linkClass) {
            case "BaseUrl" -> parameter;
            case "SceneOrgFile" -> "https://files.scene.org/view" + quote(parameter);
            case "SceneOrgFolder" -> "https://files.scene.org/browse" + quote(parameter);
            case "ModlandFile" -> "https://ftp.modland.com" + parameter;
            // Demozoo shows AMP's page for the module, which is no file; AMP's download script hands over the module.
            case "AmpModule" -> "https://amp.dascene.net/downmod.php?index=" + parameter;
            case "FujiologyFile" -> "https://fujiology.org" + parameter;
            case "UntergrundFile" -> "https://ftp.untergrund.net" + parameter;
            case "AmigascneFile" -> "http://ftp.amigascne.org/pub/amiga" + parameter;
            case "PaduaOrgFile" -> "ftp://ftp.padua.org/pub/c64" + parameter;
            case "WaybackMachinePage" -> "https://web.archive.org/web/" + parameter;
            case "ModarchiveModule" -> "https://modarchive.org/module.php?" + parameter;
            default -> null;
        });
    }

    /**
     * Percent-encodes the parameter from its ISO-8859-1 bytes, as Python's {@code quote} on an iso-8859-1 encode
     * does. A character beyond that charset, which Python would refuse, is taken from its UTF-8 bytes instead.
     */
    private static String quote(String parameter) {
        final StringBuilder quoted = new StringBuilder(parameter.length() + 16);
        int at = 0;
        while (at < parameter.length()) {
            final int codePoint = parameter.codePointAt(at);
            if (codePoint < 0x100) {
                appendQuoted(quoted, codePoint);
            } else {
                appendUtf8(quoted, codePoint);
            }
            at += Character.charCount(codePoint);
        }
        return quoted.toString();
    }

    private static void appendUtf8(StringBuilder quoted, int codePoint) {
        for (final byte value : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
            appendQuoted(quoted, value & 0xFF);
        }
    }

    private static void appendQuoted(StringBuilder quoted, int value) {
        if (SAFE_CHARACTERS.indexOf(value) >= 0) {
            quoted.append((char) value);
        } else {
            quoted.append('%').append(HEX_DIGITS.charAt(value >> 4)).append(HEX_DIGITS.charAt(value & 0xF));
        }
    }
}

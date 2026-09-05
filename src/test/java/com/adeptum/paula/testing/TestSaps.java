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

package com.adeptum.paula.testing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Builds a minimal but valid type B SAP file: the init routine sets a pure tone at full volume on the first
 * POKEY channel, the player routine does nothing, so rendering yields a steady tone for the tagged two seconds.
 */
public final class TestSaps {

    public static final String NAME = "Paula Test Tune";
    public static final String AUTHOR = "Paula";
    public static final String DATE = "2026";
    public static final int SUBTUNES = 2;
    public static final Duration LENGTH = Duration.ofSeconds(2);

    private static final String HEADER = "SAP\r\nAUTHOR \"" + AUTHOR + "\"\r\nNAME \"" + NAME + "\"\r\nDATE \"" + DATE
            + "\"\r\nSONGS " + SUBTUNES + "\r\nDEFSONG 0\r\nTYPE B\r\nINIT 2000\r\nPLAYER 2010\r\nTIME 00:02\r\nTIME 00:03\r\n";
    private static final byte[] SEGMENT = {(byte) 0xFF, (byte) 0xFF, 0x00, 0x20, 0x10, 0x20};
    private static final byte[] CODE = {
        (byte) 0xA9, 0x40, (byte) 0x8D, 0x00, (byte) 0xD2,
        (byte) 0xA9, (byte) 0xAF, (byte) 0x8D, 0x01, (byte) 0xD2,
        (byte) 0xA9, 0x03, (byte) 0x8D, 0x0F, (byte) 0xD2,
        0x60,
        0x60};

    private TestSaps() {
    }

    public static byte[] sap() {
        return ByteBuffer.allocate(HEADER.length() + SEGMENT.length + CODE.length)
                .put(HEADER.getBytes(StandardCharsets.US_ASCII)).put(SEGMENT).put(CODE).array();
    }

    public static Path writeSap(Path directory) throws IOException {
        return Files.write(directory.resolve("test.sap"), sap());
    }
}

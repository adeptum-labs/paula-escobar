/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jp.gr.java_conf.dangan.util.lha.LhaHeader;
import jp.gr.java_conf.dangan.util.lha.LhaOutputStream;

/**
 * Builds small archives in memory; entry order follows the map's iteration order.
 */
public final class TestArchives {

    private TestArchives() {
    }

    public static byte[] zip(Map<String, byte[]> entries) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    public static byte[] lha(Map<String, byte[]> entries, String method) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (LhaOutputStream lha = new LhaOutputStream(bytes)) {
            for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
                final LhaHeader header = new LhaHeader(entry.getKey());
                header.setCompressMethod(method);
                lha.putNextEntry(header);
                lha.write(entry.getValue());
                lha.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}

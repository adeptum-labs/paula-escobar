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

package com.adeptum.paula.archive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Unwraps the module inside an Unreal music package. What the game keeps around a tune is a whole one of the
 * four tracker formats, unchanged, so the package is found by its tag and the tune by its own.
 *
 * <p>Reaching the tune the long way round means a name table whose entries change shape halfway through the
 * versions, an import table, an export table and the variable-length integer every one of their fields is
 * written as, all to recover a range of bytes that the tune's own signature gives away. Whatever follows the
 * module is left on the end, which costs nothing: a tracker reads only as far as its own headers describe,
 * as it already does for what the gzip and XPK readers hand it.
 */
public final class UmxExtractor implements ArchiveExtractor {

    private static final byte[] MAGIC = {(byte) 0xC1, (byte) 0x83, 0x2A, (byte) 0x9E};
    private static final int HEADER_LENGTH = 0x24;

    private static final List<Payload> PAYLOADS = List.of(
            new Payload("IMPM", 0, "it"),
            new Payload("Extended Module: ", 0, "xm"),
            new Payload("SCRM", 44, "s3m"),
            new Payload("M.K.", 1080, "mod"),
            new Payload("M!K!", 1080, "mod"),
            new Payload("4CHN", 1080, "mod"),
            new Payload("6CHN", 1080, "mod"),
            new Payload("8CHN", 1080, "mod"),
            new Payload("FLT4", 1080, "mod"),
            new Payload("FLT8", 1080, "mod"));

    private record Payload(String tag, int precedes, String extension) {

        byte[] bytes() {
            return tag.getBytes(StandardCharsets.US_ASCII);
        }
    }

    @Override
    public boolean matches(byte[] head) {
        return Archives.startsWith(head, MAGIC);
    }

    /**
     * The package holds one tune, but which of the four it is cannot be told from the package's name, so it is
     * offered under the name of the format found rather than through the single-file path.
     */
    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        final byte[] file = Files.readAllBytes(archive);
        final int start = start(file);
        final Payload payload = start < 0 ? null : payloadAt(file, start);
        if (payload == null) {
            throw new IOException("No module inside " + archive.getFileName());
        }
        final String name = stem(archive.getFileName().toString()) + '.' + payload.extension();
        if (wanted.test(name)) {
            Files.write(Archives.target(into, name), Arrays.copyOfRange(file, start, file.length));
        }
    }

    private static int start(byte[] file) {
        int earliest = -1;
        for (final Payload payload : PAYLOADS) {
            final int at = indexOf(file, payload.bytes(), HEADER_LENGTH + payload.precedes());
            if (at >= 0 && (earliest < 0 || at - payload.precedes() < earliest)) {
                earliest = at - payload.precedes();
            }
        }
        return earliest;
    }

    private static Payload payloadAt(byte[] file, int start) {
        for (final Payload payload : PAYLOADS) {
            final int at = start + payload.precedes();
            if (at >= 0 && startsAt(file, payload.bytes(), at)) {
                return payload;
            }
        }
        return null;
    }

    private static int indexOf(byte[] file, byte[] tag, int from) {
        for (int at = from; at + tag.length <= file.length; at++) {
            if (startsAt(file, tag, at)) {
                return at;
            }
        }
        return -1;
    }

    private static boolean startsAt(byte[] file, byte[] tag, int at) {
        if (at < 0 || at + tag.length > file.length) {
            return false;
        }
        return Arrays.equals(file, at, at + tag.length, tag, 0, tag.length);
    }

    private static String stem(String name) {
        final int dot = name.lastIndexOf('.');
        return dot < 1 ? name : name.substring(0, dot);
    }
}

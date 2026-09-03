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

package com.adeptum.paula.module.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Id3Test {

    private static final int V1_LENGTH = 128;

    @Test
    void findsNothingInAFileWithoutTags() {
        final Id3 tags = Id3.of(new byte[512]);

        assertTrue(tags.isEmpty());
        assertEquals("", tags.title());
        assertEquals(0, Id3.audioStart(new byte[512]), "the audio starts at the first byte");
    }

    @Test
    void readsTheOlderTagFromTheEndOfTheFile() {
        final Id3 tags = Id3.of(version1("Cream of the Earth", "Jeroen Tel", "Turbo Outrun", "1989"));

        assertEquals("Cream of the Earth", tags.title());
        assertEquals("Jeroen Tel", tags.artist());
        assertEquals("Turbo Outrun", tags.album());
        assertEquals("1989", tags.year());
    }

    @Test
    void readsTheNewerTagFromTheHeadOfTheFile() throws IOException {
        final byte[] file = version2("TIT2", "Elysium", "TPE1", "Jester");

        final Id3 tags = Id3.of(file);

        assertEquals("Elysium", tags.title());
        assertEquals("Jester", tags.artist());
        assertTrue(Id3.audioStart(file) > 10, "the audio starts past the tag, at " + Id3.audioStart(file));
    }

    @Test
    void prefersTheNewerTagWhereBothAreThere() throws IOException {
        final byte[] head = version2("TIT2", "Second Reality", "TPE1", "Purple Motion");
        final byte[] tail = version1("Old Title", "Old Artist", "Old Album", "1993");
        final byte[] file = join(head, tail);

        final Id3 tags = Id3.of(file);

        assertEquals("Second Reality", tags.title());
        assertEquals("Purple Motion", tags.artist());
        assertEquals("Old Album", tags.album(), "what the newer tag leaves out the older one still answers");
    }

    private static byte[] version1(String title, String artist, String album, String year) {
        final byte[] tag = new byte[V1_LENGTH];
        System.arraycopy("TAG".getBytes(StandardCharsets.ISO_8859_1), 0, tag, 0, 3);
        write(tag, 3, title);
        write(tag, 33, artist);
        write(tag, 63, album);
        write(tag, 93, year);
        return join(new byte[256], tag);
    }

    /**
     * An ID3v2.3 tag holding the given frames, each a text frame with the byte that names its encoding.
     */
    private static byte[] version2(String... framesAndText) throws IOException {
        final ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (int i = 0; i < framesAndText.length; i += 2) {
            final byte[] text = framesAndText[i + 1].getBytes(StandardCharsets.ISO_8859_1);
            frames.write(framesAndText[i].getBytes(StandardCharsets.ISO_8859_1));
            frames.write(new byte[] {0, 0, (byte) ((text.length + 1) >> 8), (byte) (text.length + 1)});
            frames.write(new byte[] {0, 0});
            frames.write(0);
            frames.write(text);
        }
        final byte[] body = frames.toByteArray();
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write("ID3".getBytes(StandardCharsets.ISO_8859_1));
        file.write(new byte[] {3, 0, 0});
        file.write(new byte[] {(byte) (body.length >> 21 & 0x7F), (byte) (body.length >> 14 & 0x7F),
                (byte) (body.length >> 7 & 0x7F), (byte) (body.length & 0x7F)});
        file.write(body);
        return file.toByteArray();
    }

    private static void write(byte[] tag, int at, String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(bytes, 0, tag, at, bytes.length);
    }

    private static byte[] join(byte[] head, byte[] tail) {
        final byte[] joined = new byte[head.length + tail.length];
        System.arraycopy(head, 0, joined, 0, head.length);
        System.arraycopy(tail, 0, joined, head.length, tail.length);
        return joined;
    }
}

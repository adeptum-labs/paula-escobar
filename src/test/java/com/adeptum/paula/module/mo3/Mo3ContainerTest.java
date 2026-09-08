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

package com.adeptum.paula.module.mo3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The fixture is the module {@code TestModules.proTracker()} writes, packed by MO3ENC 2.4.2.2 of Un4seen
 * Developments at its best quality, so the compressor that made it is the one the format is named after. What
 * it unpacks to is checked against the module UNMO3 2.4.2 rebuilds from it, which is byte for byte the one it
 * was made from bar the restart position the format does not keep.
 */
class Mo3ContainerTest {

    private static final String FIXTURE = "/mo3/paula-test.mo3";
    private static final int MUSIC_LENGTH = 1799;
    private static final int SAMPLE_DATA_AT = 106;
    private static final long MUSIC_FINGERPRINT = 4006040106497838262L;
    private static final int VERSION_AT = 3;
    private static final int MUSIC_SIZE_AT = 4;
    private static final int NEWER_THAN_ANY = 6;

    @Test
    void readsWhatTheFileSaysAboutItself() throws IOException {
        final Mo3Container container = Mo3Container.read(fixture());

        assertEquals(5, container.version());
        assertEquals(MUSIC_LENGTH, container.music().length);
        assertEquals(SAMPLE_DATA_AT, container.sampleData(), "the sample data follows the compressed music");
    }

    @Test
    void unpacksTheMusicTheCompressorPacked() throws IOException {
        assertEquals(MUSIC_FINGERPRINT, fingerprint(Mo3Container.read(fixture()).music()));
    }

    @Test
    void unpacksTheSongNameOntoTheFrontOfTheMusic() throws IOException {
        final byte[] music = Mo3Container.read(fixture()).music();

        assertTrue(new String(music, 0, 10, StandardCharsets.ISO_8859_1).startsWith("Paula Test"),
                "the music chunk opens with the song name");
    }

    @Test
    void refusesAFileThatIsNotAnMo3() throws IOException {
        final byte[] file = fixture();
        file[0] = 'X';

        assertRefused(file);
    }

    @Test
    void refusesAVersionNewerThanItReads() throws IOException {
        final byte[] file = fixture();
        file[VERSION_AT] = NEWER_THAN_ANY;

        assertRefused(file);
    }

    @Test
    void refusesAMusicSizeNoModuleWouldHave() throws IOException {
        final byte[] file = fixture();
        Arrays.fill(file, MUSIC_SIZE_AT, MUSIC_SIZE_AT + Integer.BYTES, (byte) 0);

        assertRefused(file);
    }

    @Test
    void refusesAFileThatEndsInsideItsCompressedMusic() throws IOException {
        final byte[] file = fixture();

        assertRefused(Arrays.copyOf(file, file.length / 2));
    }

    @Test
    void refusesAFileWithNoRoomForAHeader() {
        assertRefused(new byte[] {'M', 'O', '3'});
    }

    private static void assertRefused(byte[] file) {
        final IOException refused = assertThrows(IOException.class, () -> Mo3Container.read(file));

        assertNotNull(refused.getMessage(), "the refusal says what was wrong with the file");
    }

    private static long fingerprint(byte[] music) {
        long hash = 0xcbf29ce484222325L;
        for (byte value : music) {
            hash = (hash ^ (value & 0xFF)) * 0x100000001b3L;
        }
        return hash;
    }

    private static byte[] fixture() throws IOException {
        try (InputStream fixture = Mo3ContainerTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(fixture, FIXTURE);
            return fixture.readAllBytes();
        }
    }
}

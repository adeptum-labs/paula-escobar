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

package com.adeptum.paula.module.protracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProTrackerLoaderTest {

    private static final Path PATH = Path.of("song.mod");

    private final ProTrackerLoader loader = new ProTrackerLoader();

    @Test
    void supportsModExtensionAndAmigaPrefix() {
        assertTrue(loader.supports(Path.of("tune.MOD")));
        assertTrue(loader.supports(Path.of("mod.tune")));
        assertFalse(loader.supports(Path.of("tune.xm")));
    }

    @Test
    void parsesTitleChannelsLengthAndSampleNames() throws UnsupportedModuleException {
        final byte[] data = header("M.K.");
        put(data, 0, "Space Debris");
        put(data, ProTrackerLoader.TITLE_LENGTH, "kick");
        put(data, ProTrackerLoader.TITLE_LENGTH + ProTrackerLoader.SAMPLE_HEADER_LENGTH, "snare");
        data[ProTrackerLoader.SONG_LENGTH_OFFSET] = 42;

        final ModuleMetadata meta = ProTrackerLoader.parseHeader(PATH, data);

        assertEquals("Space Debris", meta.title());
        assertEquals(4, meta.channels());
        assertEquals(42, meta.songLength());
        assertEquals(ProTrackerLoader.SAMPLE_COUNT, meta.instruments().size());
        assertEquals("kick", meta.instruments().get(0));
        assertEquals("snare", meta.instruments().get(1));
        assertEquals("", meta.instruments().get(2));
    }

    @Test
    void readsChannelCountFromMagic() throws UnsupportedModuleException {
        assertEquals(8, ProTrackerLoader.parseHeader(PATH, header("8CHN")).channels());
    }

    @Test
    void rejectsUnknownMagic() {
        assertThrows(UnsupportedModuleException.class, () -> ProTrackerLoader.parseHeader(PATH, header("XXXX")));
    }

    @Test
    void rejectsTruncatedFile() {
        assertThrows(UnsupportedModuleException.class, () -> ProTrackerLoader.parseHeader(PATH, new byte[100]));
    }

    private static byte[] header(String magic) {
        final byte[] data = new byte[ProTrackerLoader.HEADER_LENGTH];
        put(data, ProTrackerLoader.MAGIC_OFFSET, magic);
        return data;
    }

    private static void put(byte[] data, int offset, String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(bytes, 0, data, offset, bytes.length);
    }
}

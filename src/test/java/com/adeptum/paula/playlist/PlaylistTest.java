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

package com.adeptum.paula.playlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaylistTest {

    private static final Track A = new LocalTrack(Path.of("a.mod"));
    private static final Track B = new LocalTrack(Path.of("b.mod"));

    private final Playlist playlist = new Playlist(List.of(A, B));

    @Test
    void startsAtFirstEntry() {
        assertEquals(A, playlist.current());
        assertEquals(1, playlist.position());
        assertEquals(2, playlist.size());
    }

    @Test
    void stepsForwardAndBackWithoutWrapping() {
        assertTrue(playlist.next());
        assertEquals(B, playlist.current());
        assertFalse(playlist.next());
        assertTrue(playlist.previous());
        assertFalse(playlist.previous());
        assertEquals(A, playlist.current());
    }

    @Test
    void rejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> new Playlist(List.of()));
    }
}

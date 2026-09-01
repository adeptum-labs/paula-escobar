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

import com.adeptum.paula.demozoo.CompoEntry;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrackTest {

    @Test
    void localTracksAreLabelledByFileName() {
        assertEquals("space.mod", new LocalTrack(Path.of("dir", "space.mod")).label());
    }

    @Test
    void demozooTracksAreLabelledByCompoPlacingTitleAndAuthor() {
        final CompoEntry entry = new CompoEntry(1, "1", 7, "Funkyeeh", "Theseus", Set.of(29));
        assertEquals("Assembly 1995 · 4 Channel Music  #1 Funkyeeh by Theseus",
                new DemozooTrack(entry, "Assembly 1995 · 4 Channel Music").label());
    }
}

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

package com.adeptum.paula.module.med;

import java.util.List;

/**
 * An OctaMED module as the file holds it: the song and the order it plays its blocks in, the blocks themselves
 * and the instruments they are played with. Samples are kept as signed 16-bit frames whatever width the file
 * stored them in.
 */
record MedFile(String name, String comment, int version, MedSong song, List<MedBlock> blocks,
               List<MedInstrument> instruments) {

    MedFile {
        blocks = List.copyOf(blocks);
        instruments = List.copyOf(instruments);
    }

    MedBlock block(int number) {
        return number >= 0 && number < blocks.size() ? blocks.get(number) : null;
    }

    MedInstrument instrument(int number) {
        return number >= 1 && number <= instruments.size() ? instruments.get(number - 1) : null;
    }

    /**
     * The widest block decides how many voices the player has to mix, since a song may walk from a block of
     * four tracks into one of eight.
     */
    int tracks() {
        return blocks.stream().mapToInt(MedBlock::tracks).max().orElse(0);
    }
}

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

package com.adeptum.paula.module.digibooster;

import java.util.List;

/**
 * A DigiBooster module as the file holds it: the songs and their playlists, the patterns they play and the
 * instruments and samples they play them with. Samples are kept as signed 16-bit frames whatever width the file
 * stored them in.
 */
record DbmFile(String name, int creatorVersion, int creatorRevision, int tracks, List<DbmSong> songs,
               List<DbmInstrument> instruments, List<short[]> samples, List<DbmPattern> patterns,
               List<DbmEnvelope> volumeEnvelopes, List<DbmEnvelope> panningEnvelopes, DbmEcho echo) {

    static final int DIGIBOOSTER_2 = 2;
    static final int DIGIBOOSTER_3 = 3;

    DbmFile {
        songs = List.copyOf(songs);
        instruments = List.copyOf(instruments);
        samples = List.copyOf(samples);
        patterns = List.copyOf(patterns);
        volumeEnvelopes = List.copyOf(volumeEnvelopes);
        panningEnvelopes = List.copyOf(panningEnvelopes);
    }

    String creator() {
        return "DigiBooster " + (creatorVersion == DIGIBOOSTER_2 ? "Pro " : "") + creatorVersion + "." + creatorRevision;
    }

    short[] sampleFor(DbmInstrument instrument) {
        return samples.get(instrument.sample());
    }
}

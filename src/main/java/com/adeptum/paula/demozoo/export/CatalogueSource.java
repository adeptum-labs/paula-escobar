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

package com.adeptum.paula.demozoo.export;

import com.adeptum.paula.playback.Progress;
import java.io.IOException;
import java.util.Optional;

/**
 * Where the music catalogue comes from.
 */
public interface CatalogueSource {

    MusicCatalogue catalogue() throws IOException;

    /**
     * Looks for a newer catalogue at the next chance rather than on the usual schedule.
     */
    void checkSoon();

    /**
     * What getting the catalogue ready is busy with, while it is.
     */
    Optional<Progress.Step> preparing();

    /**
     * A catalogue that is simply there and never changes.
     */
    static CatalogueSource fixed(MusicCatalogue catalogue) {
        return new CatalogueSource() {
            @Override
            public MusicCatalogue catalogue() {
                return catalogue;
            }

            @Override
            public void checkSoon() {
                // Nothing newer will ever come.
            }

            @Override
            public Optional<Progress.Step> preparing() {
                return Optional.empty();
            }
        };
    }
}

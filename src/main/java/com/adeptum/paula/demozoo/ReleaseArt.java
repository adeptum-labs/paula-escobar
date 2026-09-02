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

package com.adeptum.paula.demozoo;

import java.util.List;
import java.util.Optional;

/**
 * The text art a release carries: the file id or information file that party archives are packed with, which
 * often holds a hand drawn banner for the party and its competition.
 */
public interface ReleaseArt {

    ReleaseArt NONE = new ReleaseArt() {

        @Override
        public Optional<List<String>> of(int productionId) {
            return Optional.empty();
        }
    };

    Optional<List<String>> of(int productionId);

    /**
     * Brings down the files of a release so its art can be read, for a competition being looked at rather than
     * played. Returns at once; the art turns up on a later look.
     */
    default void fetch(CompoEntry entry) {
    }
}

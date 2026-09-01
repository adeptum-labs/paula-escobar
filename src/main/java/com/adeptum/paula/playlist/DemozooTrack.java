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

import com.adeptum.paula.demozoo.CompoEntry;

/**
 * A competition entry from Demozoo; the compo label names the party and competition it was placed in.
 */
public record DemozooTrack(CompoEntry entry, String compoLabel) implements Track {

    @Override
    public String label() {
        return compoLabel + "  #" + entry.placing() + " " + entry.title() + " by " + entry.author();
    }
}

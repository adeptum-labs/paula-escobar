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

package com.adeptum.paula.demozoo;

import java.util.List;
import java.util.Optional;

/**
 * A release with its file downloads, the external pages (ModArchive, Pouët, YouTube) Demozoo links to, and a
 * picture of it where Demozoo holds one. Music is rarely pictured, so the picture is usually missing.
 */
public record Production(int id, String title, List<Link> downloads, List<Link> externals, String picture) {

    public Optional<String> pictured() {
        return Optional.ofNullable(picture);
    }
}

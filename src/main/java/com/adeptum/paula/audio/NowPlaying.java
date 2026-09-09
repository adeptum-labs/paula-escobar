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

package com.adeptum.paula.audio;

import java.time.Duration;
import java.util.List;
import lombok.Builder;

/**
 * What is playing, for a sink that shows or announces it rather than only sounding it: a device on the network
 * puts this on the screen it is plugged into.
 *
 * <p>The length is what a screen draws its progress against and is missing for a song that runs until it is
 * stopped; the position is where the sound handed over begins, which is anywhere but the start only after a
 * seek. The picture is the one whoever offered the song holds of it, which for music is seldom anything;
 * the art is the text the release itself carries, which is drawn into a picture where there is no other.</p>
 */
@Builder(toBuilder = true)
public record NowPlaying(String title, String artist, String album, Duration length, Duration position,
        String picture, List<String> art) {

    public NowPlaying {
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        album = album == null ? "" : album;
        art = art == null ? List.of() : List.copyOf(art);
        position = position == null ? Duration.ZERO : position;
    }
}

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

package com.adeptum.paula.ui;

import java.time.Duration;
import lombok.Builder;
import com.adeptum.paula.module.Module;
import com.adeptum.paula.playback.PlaybackState;

/**
 * What the player screen shows. The module is absent until a track has loaded and the status line is absent
 * unless something is being loaded or went wrong.
 */
@Builder
public record PlayerView(
        Module module,
        String trackLabel,
        PlaybackState state,
        Duration position,
        int track,
        int trackCount,
        String status) {
}

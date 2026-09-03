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

package com.adeptum.paula.ui;

import java.time.Duration;
import java.util.List;
import lombok.Builder;
import com.adeptum.paula.module.Module;
import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.PlaybackState;
import com.adeptum.paula.ui.visual.Waterfall;

/**
 * What the player screen shows. The module is absent until a track has loaded, the status line is absent unless
 * something is being loaded or went wrong, and the visual fields are absent when nothing plays.
 */
@Builder
public record PlayerView(
        Module module,
        String trackLabel,
        PlaybackState state,
        Duration position,
        Duration length,
        int track,
        int trackCount,
        String status,
        double[] spectrum,
        double[] peaks,
        double vuLeft,
        double vuRight,
        List<ChannelState> channels,
        double[] mixed,
        double[] stereo,
        Visual visual,
        Waterfall waterfall) {

    public PlayerView {
        channels = channels == null ? List.of() : channels;
        spectrum = spectrum == null ? new double[0] : spectrum;
        peaks = peaks == null ? new double[spectrum.length] : peaks;
        mixed = mixed == null ? new double[0] : mixed;
        stereo = stereo == null ? new double[0] : stereo;
        visual = visual == null ? Visual.SPECTRUM : visual;
    }
}

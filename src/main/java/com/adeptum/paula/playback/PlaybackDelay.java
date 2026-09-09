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

package com.adeptum.paula.playback;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * What the screen showed a moment ago, kept so it can be shown again when that moment is heard.
 *
 * <p>A device on the network plays seconds behind the player. The sound itself is kept in the tap, but the
 * scopes and the position are read off the mixer as it stands, so each reading is kept here against the
 * frame it was taken at, and the one that goes with the sound being heard is looked up by that.</p>
 */
public final class PlaybackDelay {

    private final Deque<Moment> moments = new ArrayDeque<>();
    private final long keptFrames;

    public PlaybackDelay(long keptFrames) {
        this.keptFrames = keptFrames;
    }

    /**
     * A reading of the mixer as it stood when this many frames had been written.
     */
    public record Moment(long frame, List<ChannelState> channels, Duration position) {
    }

    public synchronized void record(long frame, List<ChannelState> channels, Duration position) {
        moments.addLast(new Moment(frame, channels, position));
        while (!moments.isEmpty() && moments.peekFirst().frame() < frame - keptFrames) {
            moments.pollFirst();
        }
    }

    /**
     * The reading taken as that frame was written, or the nearest before it; the newest when nothing was.
     */
    public synchronized Moment at(long frame) {
        Moment found = null;
        for (final Moment moment : moments) {
            if (moment.frame() > frame) {
                break;
            }
            found = moment;
        }
        return found != null ? found : moments.isEmpty() ? null : moments.peekFirst();
    }
}

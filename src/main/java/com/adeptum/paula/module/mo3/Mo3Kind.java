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

package com.adeptum.paula.module.mo3;

/**
 * The tracker an MO3 was packed from. The format keeps no trace of the original file, only a flag saying which
 * of the five it held, and that decides how the notes, the effects and the tuning are meant.
 *
 * <p>The five fall into two families. Impulse Tracker and Scream Tracker count their effects one way and their
 * sample rates in hertz; ProTracker, Fast Tracker and MultiTracker count theirs the other way and tune by
 * finetune and transpose.</p>
 */
enum Mo3Kind {

    IMPULSE_TRACKER("Impulse Tracker", true),
    SCREAM_TRACKER("Scream Tracker 3", true),
    PROTRACKER("ProTracker", false),
    MULTITRACKER("MultiTracker", false),
    FAST_TRACKER("Fast Tracker 2", false);

    private final String tracker;
    private final boolean screamTrackerFamily;

    Mo3Kind(String tracker, boolean screamTrackerFamily) {
        this.tracker = tracker;
        this.screamTrackerFamily = screamTrackerFamily;
    }

    static Mo3Kind of(int flags) {
        if ((flags & Mo3Song.IS_IT) != 0) {
            return IMPULSE_TRACKER;
        }
        if ((flags & Mo3Song.IS_S3M) != 0) {
            return SCREAM_TRACKER;
        }
        if ((flags & Mo3Song.IS_MOD) != 0) {
            return PROTRACKER;
        }
        return (flags & Mo3Song.IS_MTM) != 0 ? MULTITRACKER : FAST_TRACKER;
    }

    String tracker() {
        return tracker;
    }

    /**
     * Whether the effects are counted as Impulse Tracker and Scream Tracker count them, which is also the
     * mixer the module wants.
     */
    boolean isScreamTrackerFamily() {
        return screamTrackerFamily;
    }
}

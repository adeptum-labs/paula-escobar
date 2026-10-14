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

package com.adeptum.paula.module.digibooster;

/**
 * Finds how loud a song may be played, as the autoboost of DigiBooster 3's own renderer does: the level that
 * cannot clip is far too quiet for a module of many tracks, which seldom all play loud at once. The song is
 * mixed once at a low rate, a slice at a time, and the loudest it gets sets the highest boost, in whole
 * decibels, that stays a decibel short of clipping, a margin for the peaks the low rate misses.
 */
final class DbmAutoBoost {

    private static final int SAMPLE_RATE = 11025;
    private static final int SLICE_FRAMES = 4096;
    private static final int STEREO = 2;
    private static final int MAX_DECIBELS = 48;
    private static final int MARGIN_DECIBELS = 1;
    private static final long LONGEST_SONG_FRAMES = 10L * 60 * SAMPLE_RATE;

    private final DbmEngine engine;
    private final int tracks;
    private final short[] scratch = new short[SLICE_FRAMES * STEREO];
    private long frames;
    private int loudest;
    private boolean done;

    DbmAutoBoost(DbmFile module) {
        this.engine = new DbmEngine(module, SAMPLE_RATE, 0);
        this.tracks = module.tracks();
    }

    /**
     * Mixes on for about as long as given, at least a slice, and says whether the whole song has been heard.
     */
    boolean advance(long nanos) {
        final long start = System.nanoTime();
        while (!done) {
            final int mixed = engine.mix(scratch, SLICE_FRAMES);
            loudest = Math.max(loudest, engine.loudest(mixed));
            frames += mixed;
            done = mixed < SLICE_FRAMES || frames >= LONGEST_SONG_FRAMES;
            if (System.nanoTime() - start >= nanos) {
                break;
            }
        }
        return done;
    }

    int decibels() {
        int decibels = MAX_DECIBELS;
        while (decibels > 0 && DbmEngine.boostLimit(decibels + MARGIN_DECIBELS, tracks) < loudest) {
            decibels--;
        }
        return decibels;
    }
}

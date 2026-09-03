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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.playlist.LocalTrack;
import com.adeptum.paula.playlist.Track;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackLoaderTest {

    private static final Track A = new LocalTrack(Path.of("a.mod"));
    private static final Track B = new LocalTrack(Path.of("b.mod"));

    private final TrackLoader loader = new TrackLoader(Runnable::run);

    @Test
    void resolvesOnTheExecutorAndReportsOnce() {
        loader.request(A, track -> Path.of("resolved", track.label()));

        assertEquals(Optional.of(new TrackLoader.Loaded(A, Path.of("resolved/a.mod"))), loader.poll());
        assertEquals(Optional.empty(), loader.poll());
        assertFalse(loader.loading());
    }

    @Test
    void onlyTheLatestRequestIsReported() {
        loader.request(A, track -> Path.of("a"));
        loader.request(B, track -> Path.of("b"));

        assertEquals(Optional.of(new TrackLoader.Loaded(B, Path.of("b"))), loader.poll());
        assertEquals(Optional.empty(), loader.poll());
    }

    @Test
    void failuresAreReportedNotThrown() {
        loader.request(A, track -> {
            throw new IOException("boom");
        });

        final TrackLoader.Failed failed = assertInstanceOf(TrackLoader.Failed.class, loader.poll().orElseThrow());
        assertEquals(A, failed.track());
        assertEquals("boom", failed.error().getMessage());
    }

    @Test
    void unexpectedRuntimeErrorsBecomeFailures() {
        loader.request(A, track -> {
            throw new IllegalStateException("bug");
        });

        final TrackLoader.Failed failed = assertInstanceOf(TrackLoader.Failed.class, loader.poll().orElseThrow());
        assertTrue(failed.error().getMessage().contains("bug"));
    }

    @Test
    void loadingIsTrueWhileAResultIsPending() {
        final TrackLoader stalled = new TrackLoader(runnable -> { });
        stalled.request(A, track -> Path.of("a"));

        assertTrue(stalled.loading());
        assertEquals(Optional.empty(), stalled.poll());
    }

    /**
     * What the last track was busy with must not be left on the screen while the next one loads, nor after the
     * result has been handed over.
     */
    @Test
    void forgetsWhatItWasBusyWithBetweenTracks() {
        final TrackLoader loader = new TrackLoader(Runnable::run);
        loader.request(A, track -> {
            loader.progress().report("Unpacking a.zip");
            return Path.of("a");
        });

        assertEquals(Optional.of("Unpacking a.zip"), loader.progress().step(), "while it works");
        loader.poll();
        assertEquals(Optional.empty(), loader.progress().step(), "and not once it is done");

        loader.request(B, track -> Path.of("b"));
        assertEquals(Optional.empty(), loader.progress().step(), "nor carried into the next track");
    }
}

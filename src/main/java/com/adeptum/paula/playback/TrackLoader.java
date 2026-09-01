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

package com.adeptum.paula.playback;

import com.adeptum.paula.playlist.Track;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Resolves tracks to files off the interface thread. Only the newest request is ever reported, so a user who
 * skips ahead while a download runs never hears the stale one start.
 */
public final class TrackLoader implements AutoCloseable {

    public sealed interface Result permits Loaded, Failed {
        Track track();
    }

    public record Loaded(Track track, Path path) implements Result {
    }

    public record Failed(Track track, IOException error) implements Result {
    }

    public interface Resolver {
        Path resolve(Track track) throws IOException;
    }

    private static final String THREAD_NAME = "paula-loader";

    private final Executor executor;
    private CompletableFuture<Result> pending;

    public TrackLoader(Executor executor) {
        this.executor = executor;
    }

    public static TrackLoader background() {
        return new TrackLoader(DaemonExecutors.singleThread(THREAD_NAME));
    }

    /**
     * A newer request replaces the pending one; the old resolution still runs to completion so its download
     * lands in the cache.
     */
    public void request(Track track, Resolver resolver) {
        pending = CompletableFuture.supplyAsync(() -> resolve(track, resolver), executor);
    }

    public boolean loading() {
        return pending != null;
    }

    public Optional<Result> poll() {
        if (pending == null || !pending.isDone()) {
            return Optional.empty();
        }
        final Result result = pending.join();
        pending = null;
        return Optional.of(result);
    }

    private static Result resolve(Track track, Resolver resolver) {
        try {
            return new Loaded(track, resolver.resolve(track));
        } catch (IOException e) {
            return new Failed(track, e);
        } catch (RuntimeException e) {
            return new Failed(track, new IOException(e.toString(), e));
        }
    }

    @Override
    public void close() {
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }
}

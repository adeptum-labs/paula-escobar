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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background workers that never keep the process alive once the interface has quit.
 */
public final class DaemonExecutors {

    private DaemonExecutors() {
    }

    public static ExecutorService singleThread(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }
}

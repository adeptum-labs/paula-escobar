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

package com.adeptum.paula.module.sap;

import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Pulls 16-bit PCM out of ASAP's POKEY emulation.
 */
public final class SapRenderer implements Renderer {

    public SapRenderer(Path source, byte[] file, int song, Duration length, int sampleRate) {
        throw new UnsupportedOperationException("TODO: drive ASAP");
    }

    @Override
    public int render(short[] interleavedStereo) {
        return 0;
    }

    @Override
    public Duration position() {
        return Duration.ZERO;
    }

    @Override
    public void seek(Duration target) {
    }
}

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

package com.adeptum.paula.module.ay;

import java.util.OptionalLong;

/**
 * Plays a recording back. Every frame holds the whole picture rather than what changed, so going anywhere in
 * the tune costs nothing and needs no catching up.
 */
final class RegisterStream implements AySource {

    private final RegisterFrames recording;

    private int at;

    RegisterStream(RegisterFrames recording) {
        this.recording = recording;
    }

    @Override
    public boolean nextFrame(int[] registers) {
        if (at >= recording.count()) {
            return false;
        }
        recording.frame(at++, registers);
        return true;
    }

    @Override
    public void rewindTo(long frame) {
        at = (int) Math.clamp(frame, 0, recording.count());
    }

    @Override
    public OptionalLong frames() {
        return OptionalLong.of(recording.count());
    }
}

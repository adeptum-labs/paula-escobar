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

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;
import java.util.List;

/**
 * One recording of the AY's registers, ready to play.
 */
public record AyModule(Path source, ModuleMetadata metadata, RegisterFrames frames) implements Module {

    private static final String INTERRUPTS = "interrupts";

    static AyModule of(Path source, RegisterFrames frames) {
        return new AyModule(source, metadata(frames), frames);
    }

    /**
     * Whichever machine the recording came off, the chip is the same; the clock it ran at is what tells them
     * apart, and the Atari's is the one the Yamaha part was made for.
     */
    @Override
    public Renderer createRenderer(int sampleRate) {
        final AyChip.Voicing voicing = frames.clockRate() == RegisterFrames.ATARI_CLOCK
                ? AyChip.Voicing.YM
                : AyChip.Voicing.AY;
        return new AyRenderer(new RegisterStream(frames), voicing, frames.clockRate(),
                frames.framesPerSecond(), sampleRate);
    }

    private static ModuleMetadata metadata(RegisterFrames frames) {
        return ModuleMetadata.builder()
                .title(frames.title())
                .format(AyLoader.FORMAT)
                .channels(AyChip.CHANNELS)
                .songLength(frames.count())
                .lengthUnit(INTERRUPTS)
                .credits(frames.author().isBlank() ? List.of() : List.of(frames.author()))
                .build();
    }
}

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

package com.adeptum.paula.module.flextrax;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.playback.Renderer;
import java.nio.file.Path;

/**
 * A FlexTrax module: the tracker module underneath, which plays as any other, with the reverb and the delay
 * the Falcon's DSP laid over the mix put back on top of it.
 */
public final class FlexModule implements Module {

    private final Module module;
    private final FlexEffects effects;

    public FlexModule(Module module, FlexEffects effects) {
        this.module = module;
        this.effects = effects;
    }

    @Override
    public Path source() {
        return module.source();
    }

    @Override
    public ModuleMetadata metadata() {
        return module.metadata();
    }

    @Override
    public Renderer createRenderer(int sampleRate) {
        return new FlexRenderer(module.createRenderer(sampleRate), effects, sampleRate);
    }
}

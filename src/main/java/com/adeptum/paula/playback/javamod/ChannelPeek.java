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

package com.adeptum.paula.playback.javamod;

import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import de.quippy.javamod.multimedia.mod.mixer.ChannelMemory;
import java.lang.reflect.Field;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the per-channel state JavaMod keeps protected in its mixer. The jar seals its packages, so reflection is
 * the only way in; the field is registered for the native image in reflect-config.json. When the field cannot be
 * reached the scopes are simply missing rather than the player.
 */
@Slf4j
final class ChannelPeek {

    private static final Field CHANNEL_MEMORY = channelMemoryField();

    private ChannelPeek() {
    }

    static ChannelMemory[] channels(BasicModMixer mixer) {
        if (CHANNEL_MEMORY == null) {
            return null;
        }
        try {
            return (ChannelMemory[]) CHANNEL_MEMORY.get(mixer);
        } catch (IllegalAccessException | ClassCastException e) {
            return null;
        }
    }

    private static Field channelMemoryField() {
        try {
            final Field field = BasicModMixer.class.getDeclaredField("channelMemory");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException e) {
            log.warn("JavaMod channel state is not accessible, channel scopes are off: {}", e.toString());
            return null;
        }
    }
}

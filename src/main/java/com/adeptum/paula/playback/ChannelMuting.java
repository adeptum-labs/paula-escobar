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

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Turns clicks on the channel scopes into mutes. A click silences the channel under it or brings it back; a
 * shift click or a second click in quick succession leaves that channel alone sounding, and asking again for
 * the one already alone brings the rest back.
 */
public final class ChannelMuting {

    private static final long DOUBLE_CLICK_MILLIS = 400;
    private static final int NO_CHANNEL = 0;

    private final LongSupplier clock;
    private int lastChannel;
    private long lastClick;

    public ChannelMuting() {
        this(System::currentTimeMillis);
    }

    ChannelMuting(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * The second click of a pair arrives after the first has already toggled the channel, so that toggle is put
     * back before the pair is read as a solo. A pair ends there, leaving a third click to start another one.
     */
    public void click(Renderer renderer, int channel, boolean shift) {
        final long now = clock.getAsLong();
        final boolean twice = channel == lastChannel && now - lastClick < DOUBLE_CLICK_MILLIS;
        lastChannel = twice ? NO_CHANNEL : channel;
        lastClick = now;
        if (twice) {
            toggle(renderer, channel);
        }
        if (twice || shift) {
            solo(renderer, channel);
        } else {
            toggle(renderer, channel);
        }
    }

    private static void toggle(Renderer renderer, int channel) {
        renderer.mute(channel, !muted(renderer, channel));
    }

    private static void solo(Renderer renderer, int channel) {
        final List<ChannelState> channels = renderer.channels();
        final boolean alone = channels.stream().allMatch(state -> state.muted() != (state.number() == channel));
        channels.forEach(state -> renderer.mute(state.number(), !alone && state.number() != channel));
    }

    private static boolean muted(Renderer renderer, int channel) {
        return renderer.channels().stream().anyMatch(state -> state.number() == channel && state.muted());
    }
}

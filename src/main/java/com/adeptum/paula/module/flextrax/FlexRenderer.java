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

import com.adeptum.paula.playback.ChannelState;
import com.adeptum.paula.playback.Renderer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Lays the two effects over what the tracker module underneath has mixed. Both are fed the dry frame and
 * neither hears the other, as on the chip, and what they sound is added to it. The channels the player draws
 * its scopes from are the ones underneath, which is right: those show what each channel plays, and the
 * effects sit on the mix rather than on any one of them.
 *
 * <p>Seeking leaves a tail that belongs to a part of the song no longer playing, so both effects start
 * afresh.</p>
 */
final class FlexRenderer implements Renderer {

    private static final int STEREO = 2;

    private final Renderer renderer;
    private final FlexEffects effects;
    private final int sampleRate;
    private final int[] wet = new int[STEREO];

    private FlexDelay delay;
    private FlexReverb reverb;

    FlexRenderer(Renderer renderer, FlexEffects effects, int sampleRate) {
        this.renderer = renderer;
        this.effects = effects;
        this.sampleRate = sampleRate;
        silence();
    }

    @Override
    public int render(short[] interleavedStereo) {
        final int frames = renderer.render(interleavedStereo);
        for (int frame = 0; frame < frames; frame++) {
            final int at = frame * STEREO;
            final int left = interleavedStereo[at];
            final int right = interleavedStereo[at + 1];

            delay.wet(left, right, wet);
            int sounding = left + wet[0];
            int soundingRight = right + wet[1];

            reverb.wet(left, right, wet);
            interleavedStereo[at] = clamped(sounding + wet[0]);
            interleavedStereo[at + 1] = clamped(soundingRight + wet[1]);
        }
        return frames;
    }

    @Override
    public Duration position() {
        return renderer.position();
    }

    @Override
    public void seek(Duration target) {
        renderer.seek(target);
        silence();
    }

    @Override
    public Optional<Duration> length() {
        return renderer.length();
    }

    @Override
    public List<ChannelState> channels() {
        return renderer.channels();
    }

    @Override
    public void mute(int number, boolean muted) {
        renderer.mute(number, muted);
    }

    private void silence() {
        delay = new FlexDelay(effects, sampleRate);
        reverb = new FlexReverb(effects, sampleRate);
    }

    private static short clamped(int sample) {
        return (short) Math.clamp(sample, Short.MIN_VALUE, Short.MAX_VALUE);
    }
}

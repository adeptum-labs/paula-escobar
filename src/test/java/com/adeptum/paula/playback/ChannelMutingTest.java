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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChannelMutingTest {

    private static final int CHANNELS = 4;
    private static final long DOUBLE_CLICK_MILLIS = 400;

    private final Channels renderer = new Channels();
    private long now = 1_000_000;
    private final ChannelMuting muting = new ChannelMuting(() -> now);

    @Test
    void aClickSilencesAChannelAndTheNextBringsItBack() {
        click(2, false);
        assertEquals(List.of(2), silenced());

        later();
        click(2, false);
        assertEquals(List.of(), silenced());
    }

    @Test
    void aShiftClickLeavesOnlyThatChannelSounding() {
        click(3, true);
        assertEquals(List.of(1, 2, 4), silenced());
    }

    @Test
    void aShiftClickOnTheChannelAlreadyAloneBringsTheRestBack() {
        click(3, true);
        later();
        click(3, true);
        assertEquals(List.of(), silenced());
    }

    @Test
    void aDoubleClickSolosRatherThanLeavingTheFirstClickStanding() {
        click(3, false);
        soon();
        click(3, false);
        assertEquals(List.of(1, 2, 4), silenced());
    }

    @Test
    void aDoubleClickOnTheSoloedChannelBringsTheRestBack() {
        click(3, true);
        later();
        click(3, false);
        soon();
        click(3, false);
        assertEquals(List.of(), silenced());
    }

    @Test
    void clicksTooFarApartAreTwoClicksRatherThanOne() {
        click(2, false);
        later();
        click(2, false);
        assertEquals(List.of(), silenced(), "the second click undid the first instead of soloing");
    }

    @Test
    void aThirdClickStartsAPairOfItsOwn() {
        click(2, false);
        soon();
        click(2, false);
        soon();
        click(2, false);
        assertEquals(List.of(1, 2, 3, 4), silenced(), "the third click silenced the channel the pair had left alone");
    }

    @Test
    void clicksOnDifferentChannelsNeverPair() {
        click(1, false);
        soon();
        click(2, false);
        assertEquals(List.of(1, 2), silenced());
    }

    private void click(int channel, boolean shift) {
        muting.click(renderer, channel, shift);
    }

    private void soon() {
        now += DOUBLE_CLICK_MILLIS / 2;
    }

    private void later() {
        now += DOUBLE_CLICK_MILLIS * 2;
    }

    private List<Integer> silenced() {
        return renderer.channels().stream().filter(ChannelState::muted).map(ChannelState::number).toList();
    }

    /**
     * A renderer that is nothing but the channels it can silence.
     */
    private static final class Channels implements Renderer {

        private final List<Boolean> muted = new ArrayList<>(IntStream.range(0, CHANNELS).mapToObj(channel -> false).toList());

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

        @Override
        public List<ChannelState> channels() {
            return IntStream.rangeClosed(1, CHANNELS)
                    .mapToObj(number -> new ChannelState(number, 0, 0, new double[0], muted.get(number - 1)))
                    .toList();
        }

        @Override
        public void mute(int number, boolean silenced) {
            muted.set(number - 1, silenced);
        }
    }
}

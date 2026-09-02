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

/**
 * The most recent audio that went to the sink, kept for the visualisers. The pump thread writes, the interface
 * thread snapshots; the ring is guarded by a lock held only for the copy.
 */
public final class AudioTap {

    private static final int CHANNELS = 2;

    private final short[] ring;
    private final int capacityFrames;
    private long written;

    public AudioTap(int capacityFrames) {
        this.capacityFrames = capacityFrames;
        this.ring = new short[capacityFrames * CHANNELS];
    }

    public synchronized void write(short[] interleaved, int frames) {
        final int kept = Math.min(frames, capacityFrames);
        written += frames - kept;
        for (int i = frames - kept; i < frames; i++, written++) {
            final int slot = (int) (written % capacityFrames) * CHANNELS;
            ring[slot] = interleaved[i * CHANNELS];
            ring[slot + 1] = interleaved[i * CHANNELS + 1];
        }
    }

    public synchronized long written() {
        return written;
    }

    /**
     * The newest frames, newest last; history older than the ring or before the first write reads as silence.
     */
    public synchronized short[] snapshot(int frames) {
        final short[] out = new short[frames * CHANNELS];
        final int available = (int) Math.min(Math.min(frames, capacityFrames), written);
        for (int i = 0; i < available; i++) {
            final long frame = written - available + i;
            final int slot = (int) (frame % capacityFrames) * CHANNELS;
            final int target = (frames - available + i) * CHANNELS;
            out[target] = ring[slot];
            out[target + 1] = ring[slot + 1];
        }
        return out;
    }
}

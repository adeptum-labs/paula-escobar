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

package com.adeptum.paula.cast;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The sound on its way to a device: written by the player as it renders, read by the connection the device
 * fetches it over. It holds only a little, so a player that renders faster than the device listens waits for
 * it, which is what keeps the two at the same speed.
 */
public final class PcmStream {

    private static final int CHANNELS = 2;

    private final int sampleRate;
    private final int capacityBytes;
    private final Deque<byte[]> chunks = new ArrayDeque<>();
    private int heldBytes;
    private long writtenFrames;
    private boolean ended;
    private boolean abandoned;

    PcmStream(int sampleRate, int capacityFrames) {
        this.sampleRate = sampleRate;
        this.capacityBytes = capacityFrames * CHANNELS * Short.BYTES;
    }

    public int sampleRate() {
        return sampleRate;
    }

    /**
     * How many frames the player has handed over so far, which set against where the device says it is
     * playing measures how far behind the sound runs.
     */
    public synchronized long writtenFrames() {
        return writtenFrames;
    }

    /**
     * Takes the frames, waiting while the stream is full until the device has fetched some; returns false once
     * nothing will read them any more.
     */
    public synchronized boolean write(short[] interleaved, int frames) {
        final byte[] chunk = new byte[frames * CHANNELS * Short.BYTES];
        for (int sample = 0; sample < frames * CHANNELS; sample++) {
            chunk[sample * Short.BYTES] = (byte) interleaved[sample];
            chunk[sample * Short.BYTES + 1] = (byte) (interleaved[sample] >> Byte.SIZE);
        }
        while (heldBytes + chunk.length > capacityBytes && !abandoned && !ended) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        if (abandoned || ended) {
            return false;
        }
        chunks.addLast(chunk);
        heldBytes += chunk.length;
        writtenFrames += frames;
        notifyAll();
        return true;
    }

    /**
     * The next chunk for the device, waiting for one when none is ready; nothing once the stream has ended and
     * been read out.
     */
    synchronized byte[] read() throws InterruptedException {
        while (chunks.isEmpty() && !ended && !abandoned) {
            wait();
        }
        final byte[] chunk = chunks.pollFirst();
        if (chunk != null) {
            heldBytes -= chunk.length;
            notifyAll();
        }
        return chunk;
    }

    /**
     * No more will be written: what is held is still read out, and the connection closes after it.
     */
    public synchronized void end() {
        ended = true;
        notifyAll();
    }

    /**
     * Nothing more will be read, so a writer waiting for room is let go.
     */
    public synchronized void abandon() {
        abandoned = true;
        chunks.clear();
        heldBytes = 0;
        notifyAll();
    }

    public synchronized boolean isEnded() {
        return ended;
    }

    synchronized boolean isDrained() {
        return chunks.isEmpty() && (ended || abandoned);
    }
}

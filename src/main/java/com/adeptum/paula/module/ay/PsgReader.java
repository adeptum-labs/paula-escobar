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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a PSG recording: a header, then a stream of register writes broken into frames by a mark. A frame
 * carries only what changed, so each one starts out holding whatever the frame before it left standing, and
 * the writes that follow a mark belong to the frame it opened.
 */
final class PsgReader {

    private static final byte[] SIGNATURE = {'P', 'S', 'G', 0x1a};
    private static final int HEADER_SIZE = 16;
    private static final int SHORT_HEADER_SIZE = 4;
    private static final int FRAME_MARK = 0xff;
    private static final int SKIP_MARK = 0xfe;
    private static final int END_MARK = 0xfd;
    private static final int FRAMES_PER_SKIP = 4;
    private static final int LAST_REGISTER = 15;
    private static final int VERSION_AT = 4;
    private static final int SHAPE_REGISTER = 13;

    private PsgReader() {
    }

    static RegisterFrames read(byte[] file) throws IOException {
        if (file.length <= HEADER_SIZE) {
            throw new IOException("Not a PSG recording: it holds no stream");
        }
        for (int at = 0; at < SIGNATURE.length; at++) {
            if (file[at] != SIGNATURE[at]) {
                throw new IOException("Not a PSG recording");
            }
        }
        return flattened(framesOf(file, headerSizeOf(file)));
    }

    /**
     * Some emulators wrote only the first four bytes of the header, which shows as a frame mark where the
     * version should be.
     */
    private static int headerSizeOf(byte[] file) {
        return (file[VERSION_AT] & 0xff) == FRAME_MARK ? SHORT_HEADER_SIZE : HEADER_SIZE;
    }

    private static List<byte[]> framesOf(byte[] file, int from) {
        final List<byte[]> frames = new ArrayList<>();
        byte[] open = new byte[RegisterFrames.REGISTERS];
        int at = from;
        while (at < file.length) {
            final int mark = file[at++] & 0xff;
            if (mark == END_MARK) {
                break;
            }
            if (mark == FRAME_MARK) {
                open = opened(frames, open);
            } else if (mark == SKIP_MARK) {
                if (at == file.length) {
                    break;
                }
                for (int more = (file[at++] & 0xff) * FRAMES_PER_SKIP; more > 0; more--) {
                    open = opened(frames, open);
                }
            } else if (mark <= LAST_REGISTER) {
                if (at == file.length) {
                    break;
                }
                final byte value = file[at++];
                if (mark < RegisterFrames.REGISTERS) {
                    open[mark] = value;
                }
            } else {
                break;
            }
        }
        return frames;
    }

    /**
     * A frame carries over everything the one before it left standing, save the envelope shape: that register
     * starts the envelope again whenever it is written, so a frame is opened having not written it.
     */
    private static byte[] opened(List<byte[]> frames, byte[] standing) {
        final byte[] frame = standing.clone();
        frame[SHAPE_REGISTER] = (byte) RegisterFrames.SHAPE_UNTOUCHED;
        frames.add(frame);
        return frame;
    }

    private static RegisterFrames flattened(List<byte[]> frames) {
        final byte[] values = new byte[frames.size() * RegisterFrames.REGISTERS];
        for (int frame = 0; frame < frames.size(); frame++) {
            System.arraycopy(frames.get(frame), 0, values, frame * RegisterFrames.REGISTERS,
                    RegisterFrames.REGISTERS);
        }
        return new RegisterFrames(values, frames.size());
    }
}

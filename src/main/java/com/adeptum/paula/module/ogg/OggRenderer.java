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

package com.adeptum.paula.module.ogg;

import com.adeptum.paula.module.pcm.PcmRenderer;
import de.quippy.ogg.jogg.Packet;
import de.quippy.ogg.jogg.Page;
import de.quippy.ogg.jogg.StreamState;
import de.quippy.ogg.jogg.SyncState;
import de.quippy.ogg.jorbis.Block;
import de.quippy.ogg.jorbis.Comment;
import de.quippy.ogg.jorbis.DspState;
import de.quippy.ogg.jorbis.Info;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Decodes one Ogg Vorbis stream packet by packet. The decoder is driven straight from the pages of the file
 * rather than through JavaMod's container, which reaches for the window toolkit the native image has not got.
 */
@Slf4j
public final class OggRenderer extends PcmRenderer {

    private static final int CHUNK = 8192;
    private static final int HEADER_PACKETS = 3;
    private static final int PAGE_READY = 1;
    private static final int PAGE_HUNGRY = 0;
    private static final int PACKET_READY = 1;
    private static final int PACKET_HUNGRY = 0;
    private static final float PEAK = Short.MAX_VALUE;

    private final byte[] file;
    private final OggAudio audio;

    private SyncState sync;
    private StreamState stream;
    private Page page;
    private Packet packet;
    private Info info;
    private DspState dsp;
    private Block block;

    private int offset;
    private long seekFrames;
    private long dropFrames;
    private long framesLeft;
    private boolean ended;

    public OggRenderer(byte[] file, OggAudio audio, int outputRate) {
        super(audio.rate(), audio.channels(), outputRate);
        this.file = file;
        this.audio = audio;
        open(0);
    }

    @Override
    public Optional<Duration> length() {
        return Optional.of(audio.length());
    }

    @Override
    protected void rewind(Duration target) {
        open(audio.frameAt(target));
    }

    /**
     * A Vorbis stream has to be read from its headers for the decoder to be set up at all, so a seek starts the
     * file again and then steps over whole pages by the sample each one ends on, decoding only from the page
     * the moment sought falls in.
     */
    private void open(long frame) {
        sync = new SyncState();
        sync.init();
        stream = new StreamState();
        page = new Page();
        packet = new Packet();
        info = new Info();
        info.init();
        offset = 0;
        seekFrames = frame;
        dropFrames = frame;
        framesLeft = audio.frames() - frame;
        ended = !readHeaders();
        if (!ended) {
            dsp = new DspState();
            dsp.synthesis_init(info);
            block = new Block(dsp);
        }
    }

    @Override
    protected short[] decode() {
        while (!ended && framesLeft > 0) {
            final short[] samples = drain();
            if (samples != null) {
                return samples;
            }
            ended = !synthesiseNextPacket();
        }
        return null;
    }

    /**
     * The identification, comment and setup headers, which the decoder needs before a single sample can be
     * made and which every seek therefore reads again.
     */
    private boolean readHeaders() {
        final Comment comment = new Comment();
        comment.init();
        int read = 0;
        while (read < HEADER_PACKETS) {
            if (!nextHeaderPage(read == 0)) {
                return false;
            }
            while (read < HEADER_PACKETS) {
                final int status = stream.packetout(packet);
                if (status == PACKET_HUNGRY) {
                    break;
                }
                if (status < 0 || info.synthesis_headerin(comment, packet) < 0) {
                    log.debug("The Vorbis headers of the stream are broken at header {}", read);
                    return false;
                }
                read++;
            }
        }
        return true;
    }

    private boolean nextHeaderPage(boolean first) {
        while (true) {
            final int status = sync.pageout(page);
            if (status == PAGE_READY) {
                if (first) {
                    stream.init(page.serialno());
                }
                stream.pagein(page);
                return true;
            }
            if (status == PAGE_HUNGRY && !feed()) {
                return false;
            }
        }
    }

    private boolean synthesiseNextPacket() {
        if (!nextPacket()) {
            return false;
        }
        if (block.synthesis(packet) == 0) {
            dsp.synthesis_blockin(block);
        }
        return true;
    }

    private boolean nextPacket() {
        while (true) {
            final int status = stream.packetout(packet);
            if (status == PACKET_READY) {
                return true;
            }
            if (status == PACKET_HUNGRY && !nextPage()) {
                return false;
            }
        }
    }

    /**
     * A page ending before the moment sought holds nothing that will be heard, so it is dropped unread and the
     * samples still to throw away are counted from the sample it ended on.
     */
    private boolean nextPage() {
        while (true) {
            final int status = sync.pageout(page);
            if (status == PAGE_READY) {
                if (!endsBeforeTheSeek(page)) {
                    stream.pagein(page);
                    return true;
                }
                dropFrames = seekFrames - page.granulepos();
            } else if (status == PAGE_HUNGRY && !feed()) {
                return false;
            }
        }
    }

    private boolean endsBeforeTheSeek(Page page) {
        final long granule = page.granulepos();
        return granule >= 0 && granule < seekFrames;
    }

    /**
     * The samples the decoder has ready, which is nothing until a packet has a packet before it to overlap
     * with and nothing again while the last page before the moment sought is being thrown away. The last page
     * of a stream runs past the sample it says it ends on, so what is left of the song bounds it as well.
     */
    private short[] drain() {
        final float[][][] pcm = new float[1][][];
        final int[] index = new int[info.channels];
        final int frames = dsp.synthesis_pcmout(pcm, index);
        if (frames <= 0) {
            return null;
        }
        final int dropped = (int) Math.min(dropFrames, frames);
        dropFrames -= dropped;
        final int kept = (int) Math.min(frames - dropped, framesLeft);
        framesLeft -= kept;
        final short[] samples = interleave(pcm[0], index, dropped, kept);
        dsp.synthesis_read(frames);
        return kept == 0 ? null : samples;
    }

    private short[] interleave(float[][] pcm, int[] index, int from, int frames) {
        final int channels = info.channels;
        final short[] samples = new short[frames * channels];
        for (int channel = 0; channel < channels; channel++) {
            final float[] values = pcm[channel];
            final int start = index[channel] + from;
            for (int frame = 0; frame < frames; frame++) {
                samples[frame * channels + channel] = clamped(values[start + frame]);
            }
        }
        return samples;
    }

    private static short clamped(float value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value * PEAK)));
    }

    private boolean feed() {
        if (offset >= file.length) {
            return false;
        }
        final int count = Math.min(CHUNK, file.length - offset);
        final int index = sync.buffer(count);
        System.arraycopy(file, offset, sync.data, index, count);
        sync.wrote(count);
        offset += count;
        return true;
    }
}

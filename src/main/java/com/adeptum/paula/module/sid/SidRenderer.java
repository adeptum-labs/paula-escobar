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

package com.adeptum.paula.module.sid;

import com.adeptum.paula.playback.Renderer;
import de.quippy.sidplay.libsidplay.SIDPlay2;
import de.quippy.sidplay.libsidplay.common.ISID2Types;
import de.quippy.sidplay.libsidplay.components.sidtune.SidTune;
import de.quippy.sidplay.resid_builder.ReSIDBuilder;
import java.time.Duration;

/**
 * Emulates the C64 and its SID chip for one subtune. A SID tune never ends by itself, so playback stops at the
 * song length; seeking backwards restarts the emulation and fast-forwards, since there is no state to rewind.
 */
public final class SidRenderer implements Renderer {

    private static final int CHANNELS = 2;
    private static final int PRECISION_BITS = 16;
    private static final int BYTES_PER_SAMPLE = PRECISION_BITS / Byte.SIZE;
    private static final int SLOTS_PER_FRAME = CHANNELS * BYTES_PER_SAMPLE;
    private static final int BYTE_MASK = 0xFF;
    private static final long FULL_VOLUME = 255;
    private static final String BUILDER_NAME = "ReSID";
    private static final int SCRATCH_FRAMES = 4096;

    private final byte[] file;
    private final int subtune;
    private final int sampleRate;
    private final long lengthFrames;
    private SIDPlay2 player;
    private long renderedFrames;
    private short[] raw = new short[0];

    public SidRenderer(byte[] file, int subtune, Duration length, int sampleRate) {
        this.file = file;
        this.subtune = subtune;
        this.sampleRate = sampleRate;
        this.lengthFrames = length.toMillis() * sampleRate / 1000;
        this.player = start();
    }

    @Override
    public int render(short[] interleavedStereo) {
        final int frames = (int) Math.min(interleavedStereo.length / CHANNELS, lengthFrames - renderedFrames);
        if (frames <= 0) {
            return 0;
        }
        final int produced = emulate(frames);
        for (int i = 0; i < produced * CHANNELS; i++) {
            interleavedStereo[i] = (short) ((raw[i * BYTES_PER_SAMPLE] & BYTE_MASK) | (raw[i * BYTES_PER_SAMPLE + 1] << Byte.SIZE));
        }
        return produced;
    }

    @Override
    public Duration position() {
        return Duration.ofMillis(renderedFrames * 1000 / sampleRate);
    }

    @Override
    public void seek(Duration target) {
        final long targetFrames = Math.clamp(target.toMillis() * sampleRate / 1000, 0, lengthFrames);
        if (targetFrames < renderedFrames) {
            player = start();
            renderedFrames = 0;
        }
        while (renderedFrames < targetFrames) {
            emulate((int) Math.min(SCRATCH_FRAMES, targetFrames - renderedFrames));
        }
    }

    /**
     * The engine fills the array with the little-endian bytes of each sample, one byte per slot.
     */
    private int emulate(int frames) {
        if (raw.length < frames * SLOTS_PER_FRAME) {
            raw = new short[frames * SLOTS_PER_FRAME];
        }
        final int produced = (int) player.play(raw, frames * SLOTS_PER_FRAME) / SLOTS_PER_FRAME;
        renderedFrames += produced;
        return produced;
    }

    private SIDPlay2 start() {
        final SidTune tune = SidLoader.tune(file);
        tune.selectSong(subtune);
        final SIDPlay2 engine = new SIDPlay2();
        final ISID2Types.sid2_config_t config = engine.config();
        config.frequency = sampleRate;
        config.playback = ISID2Types.sid2_playback_t.sid2_stereo;
        config.emulateStereo = true;
        config.precision = PRECISION_BITS;
        config.sampleFormat = ISID2Types.sid2_sample_t.SID2_LITTLE_SIGNED;
        config.sidModel = ISID2Types.sid2_model_t.SID2_MODEL_CORRECT;
        config.sidDefault = ISID2Types.sid2_model_t.SID2_MOS6581;
        config.clockDefault = ISID2Types.sid2_clock_t.SID2_CLOCK_CORRECT;
        config.clockSpeed = ISID2Types.sid2_clock_t.SID2_CLOCK_CORRECT;
        config.environment = ISID2Types.sid2_env_t.sid2_envR;
        config.leftVolume = FULL_VOLUME;
        config.rightVolume = FULL_VOLUME;
        final ReSIDBuilder builder = new ReSIDBuilder(BUILDER_NAME);
        builder.create(engine.info().maxsids);
        builder.filter(true);
        builder.sampling(sampleRate);
        config.sidEmulation = builder;
        engine.config(config);
        engine.load(tune);
        return engine;
    }
}

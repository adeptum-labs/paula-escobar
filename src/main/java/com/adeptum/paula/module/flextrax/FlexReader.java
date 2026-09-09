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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads the block FlexTrax appends to a module to keep its effect settings in. Everything before the block is
 * an ordinary ProTracker module, so the block begins where the samples end and is found by measuring the
 * header, the patterns and the samples rather than by looking for its mark, which sample data can spell by
 * accident. A module saved with the effects off carries no block at all.
 *
 * <p>The format is described in {@code docs/flextrax-format.md}.
 */
public final class FlexReader {

    private static final String MARK = "FLEX";
    private static final int BLOCK_LENGTH = 152;
    private static final int HEADER_LENGTH = 1084;
    private static final int SAMPLES = 31;
    private static final int SAMPLE_ENTRY_LENGTH = 30;
    private static final int FIRST_SAMPLE_LENGTH_AT = 20 + 22;
    private static final int EMPTY_SAMPLE_WORDS = 1;
    private static final int FIRST_ORDER_AT = 952;
    private static final int ORDERS = 128;
    private static final int MARK_AT = 1080;
    private static final int MARK_LENGTH = 4;
    private static final int ROWS = 64;
    private static final int BYTES_PER_NOTE = 4;

    /**
     * Where each setting's record begins in the block, in the order the tracker names them.
     */
    private static final int REVERB_DECAY_AT = 124;
    private static final int REVERB_LEVEL_AT = 132;
    private static final int DELAY_DECAY_AT = 136;
    private static final int DELAY_TIME_AT = 140;
    private static final int DELAY_PING_PONG_AT = 144;
    private static final int DELAY_LEVEL_AT = 148;

    /**
     * A record is four bytes and the setting is the last of them; the two modules in the wild that carry
     * anything in the others carry nothing the tracker could have written.
     */
    private static final int VALUE_IN_RECORD = 3;

    private static final int FOUR_CHANNELS = 4;
    private static final int SIX_CHANNELS = 6;
    private static final int EIGHT_CHANNELS = 8;

    private FlexReader() {
    }

    public static Optional<FlexEffects> of(Path module) throws IOException {
        return of(Files.readAllBytes(module));
    }

    public static Optional<FlexEffects> of(byte[] module) {
        return blockIn(module).map(at -> new FlexEffects(
                setting(module, at, REVERB_DECAY_AT),
                setting(module, at, REVERB_LEVEL_AT),
                setting(module, at, DELAY_DECAY_AT),
                setting(module, at, DELAY_TIME_AT),
                setting(module, at, DELAY_PING_PONG_AT),
                setting(module, at, DELAY_LEVEL_AT)));
    }

    private static Optional<Integer> blockIn(byte[] module) {
        if (module.length < HEADER_LENGTH) {
            return Optional.empty();
        }
        final int channels = channelsOf(module);
        if (channels == 0) {
            return Optional.empty();
        }
        final int at = HEADER_LENGTH + patternsIn(module) * ROWS * channels * BYTES_PER_NOTE + sampleBytesIn(module);
        final boolean marked = at >= 0 && at + MARK_LENGTH + BLOCK_LENGTH <= module.length
                && MARK.equals(new String(module, at, MARK_LENGTH, StandardCharsets.US_ASCII));
        return marked ? Optional.of(at + MARK_LENGTH) : Optional.empty();
    }

    private static int setting(byte[] module, int block, int record) {
        return Math.clamp(module[block + record + VALUE_IN_RECORD] & 0xFF, 0, FlexEffects.LOUDEST);
    }

    /**
     * How many bytes of sample data follow the patterns. A sample the module never uses is given a length of
     * one word rather than none, so that the loop it also carries stays a legal one, and no data is stored
     * for it; counting those two bytes would put the block two bytes further on for every empty sample.
     */
    private static int sampleBytesIn(byte[] module) {
        int bytes = 0;
        for (int sample = 0; sample < SAMPLES; sample++) {
            final int words = word(module, FIRST_SAMPLE_LENGTH_AT + sample * SAMPLE_ENTRY_LENGTH);
            bytes += words > EMPTY_SAMPLE_WORDS ? 2 * words : 0;
        }
        return bytes;
    }

    private static int word(byte[] module, int at) {
        return (module[at] & 0xFF) << 8 | module[at + 1] & 0xFF;
    }

    /**
     * How many patterns the file holds, which is one past the highest the order list names anywhere in it: a
     * module keeps every pattern it was written with, including those the song has since stopped playing.
     */
    private static int patternsIn(byte[] module) {
        int highest = 0;
        for (int order = 0; order < ORDERS; order++) {
            highest = Math.max(highest, module[FIRST_ORDER_AT + order] & 0xFF);
        }
        return highest + 1;
    }

    /**
     * The channel count the four-character mark stands for, zero where it stands for no module at all. The
     * digit forms cover what FlexTrax itself writes; the named ones are what ProTracker and its kin write.
     */
    private static int channelsOf(byte[] module) {
        final String mark = new String(module, MARK_AT, MARK_LENGTH, StandardCharsets.US_ASCII);
        if (mark.endsWith("CHN") && Character.isDigit(mark.charAt(0))) {
            return mark.charAt(0) - '0';
        }
        return switch (mark) {
            case "M.K.", "M!K!", "FLT4" -> FOUR_CHANNELS;
            case "FLT6" -> SIX_CHANNELS;
            case "FLT8", "OCTA", "CD81" -> EIGHT_CHANNELS;
            default -> 0;
        };
    }
}

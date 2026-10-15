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

package com.adeptum.paula.playback.javamod;

import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.loader.pattern.Pattern;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * The subsongs of an Impulse Tracker, Scream Tracker or MO3 order list. Those trackers write the separator
 * "---" between the songs of one file; JavaMod's loaders stop the song length at the first separator but
 * keep the whole order list, so everything after it is still there to be found.
 */
@Slf4j
public final class Subsongs {

    private static final Field SONG_LENGTH = songLengthField();

    /**
     * JavaMod numbers the Impulse and Scream Tracker effects from 'A', so the jump to an order is the second.
     */
    private static final int JUMP_TO_ORDER = 2;

    /**
     * The length JavaMod keeps for a song it has not measured yet; it measures a module once and keeps that.
     */
    private static final long UNMEASURED = -1;

    private final Module tracker;
    private final int[] orders;
    private final List<OrderRange> ranges;
    private final List<Jump> jumps;

    /**
     * Whether the loader cut the order list at a separator; a module it did not cut is left exactly as loaded.
     */
    private final boolean separated;

    private Subsongs(Module tracker, int[] orders, List<OrderRange> ranges, List<Jump> jumps, boolean separated) {
        this.tracker = tracker;
        this.orders = orders;
        this.ranges = ranges;
        this.jumps = jumps;
        this.separated = separated;
    }

    /**
     * Orders [start, end) of the stored list as one song. Order indexes never move, so the jumps in a pattern
     * still land on the orders they name.
     */
    public record OrderRange(int start, int end) {

        boolean holds(int order) {
            return start <= order && order < end;
        }
    }

    private record Jump(PatternElement element, int order) {
    }

    /**
     * Reads the subsongs out of the tracker's order list. The list is only read here; {@link #select(int)} is
     * what installs one of them into the tracker.
     */
    public static Subsongs of(Module tracker) {
        final int[] orders = tracker.getArrangement().clone();
        final int songLength = tracker.getSongLength();
        final boolean separated = SONG_LENGTH != null && songLength < orders.length
                && orders[songLength] == ModConstants.INVALID_PAT_INDEX;
        return separated
                ? new Subsongs(tracker, orders, soundingRanges(tracker, orders), jumps(tracker, orders), true)
                : new Subsongs(tracker, orders, List.of(new OrderRange(0, songLength)), List.of(), false);
    }

    public List<OrderRange> ranges() {
        return ranges;
    }

    public int orders() {
        return ranges.stream().mapToInt(range -> range.end() - range.start()).sum();
    }

    public int count() {
        return ranges.size();
    }

    /**
     * Shows the tracker one subsong as its whole order list: the orders before it play as "+++", which its
     * mixer steps over, and the song ends where the subsong does. A jump out of the subsong, as a game's short
     * cue makes to the silence it waits in, is turned back to the subsong's start, where JavaMod hears the
     * song loop and fades it out. The field JavaMod keeps its song length in is private and its packages are
     * sealed, so it is set by reflection, the way the channel states are read.
     */
    public void select(int index) {
        if (!separated) {
            return;
        }
        final OrderRange range = ranges.get(index);
        final int[] view = Arrays.copyOf(orders, range.end());
        Arrays.fill(view, 0, range.start(), ModConstants.IGNORE_PAT_INDEX);
        tracker.setArrangement(view);
        setSongLength(range.end());
        tracker.setLengthInMilliseconds(UNMEASURED);
        jumps.forEach(jump -> jump.element().setEffektOp(range.holds(jump.order()) ? jump.order() : range.start()));
    }

    /**
     * The ranges between separators that hold a note somewhere; one that does not would be silence to play
     * through. A file whose every range is empty keeps the first of them, so something still sounds.
     */
    private static List<OrderRange> soundingRanges(Module tracker, int[] orders) {
        final List<OrderRange> found = ranges(orders);
        final List<OrderRange> sounding = found.stream()
                .filter(range -> patterns(orders, range.start(), range.end()).flatMap(pattern -> elements(tracker, pattern))
                        .anyMatch(element -> element.getNoteIndex() > 0 || element.getInstrument() > 0))
                .toList();
        return sounding.isEmpty() ? List.of(found.isEmpty() ? new OrderRange(0, orders.length) : found.getFirst()) : sounding;
    }

    /**
     * The order list cut at its separators: an empty range between neighbours, and one that only trails the
     * list, is no subsong.
     */
    private static List<OrderRange> ranges(int[] orders) {
        final List<OrderRange> ranges = new ArrayList<>();
        int start = 0;
        for (int order = 0; order <= orders.length; order++) {
            if (order == orders.length || orders[order] == ModConstants.INVALID_PAT_INDEX) {
                if (order > start) {
                    ranges.add(new OrderRange(start, order));
                }
                start = order + 1;
            }
        }
        return ranges;
    }

    /**
     * Every jump to an order in the patterns the list plays, with the order it was written to land on.
     */
    private static List<Jump> jumps(Module tracker, int[] orders) {
        return patterns(orders, 0, orders.length).distinct().flatMap(pattern -> elements(tracker, pattern))
                .filter(element -> element.getEffekt() == JUMP_TO_ORDER)
                .map(element -> new Jump(element, element.getEffektOp()))
                .toList();
    }

    private static Stream<Integer> patterns(int[] orders, int start, int end) {
        return IntStream.range(start, end).mapToObj(order -> orders[order])
                .filter(pattern -> pattern != ModConstants.IGNORE_PAT_INDEX && pattern != ModConstants.INVALID_PAT_INDEX);
    }

    private static Stream<PatternElement> elements(Module tracker, int index) {
        final Pattern pattern = tracker.getPatternContainer().getPattern(index);
        if (pattern == null) {
            return Stream.empty();
        }
        return IntStream.range(0, pattern.getRowCount()).boxed()
                .flatMap(row -> IntStream.range(0, tracker.getNChannels())
                        .mapToObj(channel -> pattern.getPatternRow(row).getPatternElement(channel)))
                .filter(element -> element != null);
    }

    private void setSongLength(int length) {
        try {
            SONG_LENGTH.setInt(tracker, length);
        } catch (IllegalAccessException e) {
            log.warn("JavaMod song length is not writable, the subsong will not be cut short: {}", e.toString());
        }
    }

    private static Field songLengthField() {
        try {
            final Field field = Module.class.getDeclaredField("songLength");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException e) {
            log.warn("JavaMod song length is not accessible, only the first subsong plays: {}", e.toString());
            return null;
        }
    }
}

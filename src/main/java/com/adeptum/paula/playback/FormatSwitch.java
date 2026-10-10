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

import com.adeptum.paula.demozoo.Variant;
import com.adeptum.paula.playlist.Track;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Moves a track on to the next version of the tune that its release holds, and back again when the new one
 * would not play. Choosing only saves the preference; the track is then played again the way any track is.
 */
final class FormatSwitch {

    record Switched(Variant previous, Variant next) {
    }

    private final TrackLoader.Resolver resolver;
    private final Set<Path> unplayable = new HashSet<>();

    FormatSwitch(TrackLoader.Resolver resolver) {
        this.resolver = resolver;
    }

    /**
     * The versions of the tune the release holds, marked by the file that is playing. A listing opens no
     * file, so it marks the one that ought to play and cannot know when that one would not open and another
     * took its place; the file actually playing is what the screen and a switch must go by.
     */
    List<Variant> variants(Track track, Path playing) throws IOException {
        final List<Variant> variants = resolver.variants(track);
        final boolean listed = variants.stream().anyMatch(variant -> variant.file().equals(playing));
        return listed ? variants.stream()
                .map(variant -> new Variant(variant.file(), variant.format(), variant.file().equals(playing)))
                .toList() : variants;
    }

    /**
     * The version after the one playing, wrapping round past those that would not play, already saved as the
     * preferred one; nothing where the release offers no other choice.
     */
    Optional<Switched> advance(Track track, Path playing) throws IOException {
        final List<Variant> variants = variants(track, playing);
        if (variants.size() < 2) {
            return Optional.empty();
        }
        final Variant current = variants.stream().filter(Variant::plays).findFirst().orElse(variants.get(0));
        final int start = variants.indexOf(current);
        final Optional<Variant> next = IntStream.range(1, variants.size())
                .mapToObj(step -> variants.get((start + step) % variants.size()))
                .filter(variant -> !unplayable.contains(variant.file()))
                .findFirst();
        if (next.isPresent()) {
            resolver.prefer(track, next.get());
        }
        return next.map(variant -> new Switched(current, variant));
    }

    /**
     * Kept out of the cycle for as long as this session lasts; a later session tries it again, as the file
     * may have been replaced in the meantime.
     */
    void failed(Variant variant) {
        unplayable.add(variant.file());
    }

    void revert(Track track, Switched switched) throws IOException {
        resolver.prefer(track, switched.previous());
    }

    /**
     * A chosen version that would not open makes the resolver play another file instead, with no failure to
     * show for it; the choice is dropped and what plays is saved, so the cycle does not keep landing on it.
     */
    Variant fellBack(Track track, Switched switched, Path playing) throws IOException {
        failed(switched.next());
        final Variant actual = resolver.variants(track).stream().filter(variant -> variant.file().equals(playing))
                .findFirst().orElse(switched.previous());
        resolver.prefer(track, actual);
        return actual;
    }
}

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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.demozoo.Variant;
import com.adeptum.paula.playlist.LocalTrack;
import com.adeptum.paula.playlist.Track;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FormatSwitchTest {

    private static final Track TRACK = new LocalTrack(Path.of("x"));
    private static final Path TAPE = Path.of("x.tap");

    private static Variant variant(String format, boolean plays) {
        return new Variant(Path.of("x." + format.toLowerCase()), format, plays);
    }

    private static final class Releases implements TrackLoader.Resolver {

        final List<Variant> variants = new ArrayList<>();
        final List<Variant> preferred = new ArrayList<>();

        @Override
        public Path resolve(Track track) {
            return Path.of("x");
        }

        @Override
        public List<Variant> variants(Track track) {
            return List.copyOf(variants);
        }

        @Override
        public void prefer(Track track, Variant variant) {
            preferred.add(variant);
        }
    }

    private final Releases releases = new Releases();
    private final FormatSwitch formats = new FormatSwitch(releases);

    @Test
    void movesToTheNextFormatAndSavesIt() throws IOException {
        releases.variants.addAll(List.of(variant("TAP", true), variant("XM", false), variant("MP3", false)));

        final FormatSwitch.Switched switched = formats.advance(TRACK, TAPE).orElseThrow();

        assertEquals("TAP", switched.previous().format());
        assertEquals("XM", switched.next().format());
        assertEquals(List.of(variant("XM", false)), releases.preferred);
    }

    @Test
    void wrapsAroundAfterTheLastFormat() throws IOException {
        releases.variants.addAll(List.of(variant("TAP", false), variant("XM", false), variant("MP3", true)));

        assertEquals("TAP", formats.advance(TRACK, Path.of("x.mp3")).orElseThrow().next().format());
    }

    @Test
    void doesNothingForOneFormat() throws IOException {
        releases.variants.add(variant("XM", true));

        assertEquals(Optional.empty(), formats.advance(TRACK, Path.of("x.xm")));
        assertTrue(releases.preferred.isEmpty());
    }

    @Test
    void doesNothingWhenTheReleaseOffersNoChoice() throws IOException {
        assertEquals(Optional.empty(), formats.advance(TRACK, TAPE));
    }

    @Test
    void takesTheFirstFormatWhenTheFileThatPlaysIsNotAmongTheVariants() throws IOException {
        releases.variants.addAll(List.of(variant("TAP", false), variant("XM", false)));

        assertEquals("XM", formats.advance(TRACK, Path.of("elsewhere")).orElseThrow().next().format());
    }

    @Test
    void followsTheFileThatPlaysRatherThanTheOneMarked() throws IOException {
        releases.variants.addAll(List.of(variant("TAP", true), variant("XM", false), variant("MP3", false)));

        final FormatSwitch.Switched switched = formats.advance(TRACK, Path.of("x.xm")).orElseThrow();

        assertEquals("XM", switched.previous().format(), "the tape would not open, so the module is what plays");
        assertEquals("MP3", switched.next().format());
    }

    @Test
    void marksTheFileThatPlaysInTheTagList() throws IOException {
        releases.variants.addAll(List.of(variant("TAP", true), variant("XM", false)));

        assertEquals("TAP/[XM]", Variant.tag(formats.variants(TRACK, Path.of("x.xm"))));
        assertEquals("[TAP]/XM", Variant.tag(formats.variants(TRACK, Path.of("elsewhere"))), "left as listed");
    }

    @Test
    void revertsToThePreviousFormat() throws IOException {
        releases.variants.addAll(List.of(variant("TAP", true), variant("XM", false)));
        final FormatSwitch.Switched switched = formats.advance(TRACK, TAPE).orElseThrow();

        formats.revert(TRACK, switched);

        assertEquals(List.of(variant("XM", false), variant("TAP", true)), releases.preferred);
    }
}

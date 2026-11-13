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

package com.adeptum.paula.demozoo.export;

import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.Nick;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which competitions are music competitions and what they are called.
 */
final class MusicCompetitions {

    private static final int NO_TYPE = 0;

    private MusicCompetitions() {
    }

    /**
     * A production type, music when it sits under Demozoo's music type.
     */
    record Type(int id, String name, boolean music) {
    }

    /**
     * One result as the export has it, with the production's own types in Demozoo's order.
     */
    record Placing(int position, String ranking, int productionId, String title, List<Nick> authors, List<Type> types) {
    }

    /**
     * The competition when it is a music competition. Demozoo leaves a fifth of its competitions untyped, every
     * Kindergarden music competition of the nineties and Assembly 1998's among them, so one without a type is
     * judged by what was entered in it: it is a music competition when most of its typed entries are, and it is
     * named after the music type most of its entries share. The placings are expected in Demozoo's order, by
     * position and then production.
     */
    static Optional<Competition> judge(int id, String name, Type type, List<Placing> placings) {
        if (type != null ? !type.music() : !mostlyMusic(placings)) {
            return Optional.empty();
        }
        final Type named = type != null ? type : commonestMusicType(placings);
        final List<CompoEntry> entries = placings.stream()
                .map(placing -> entry(placing, named.id()))
                .sorted(Comparator.comparingInt(CompoEntry::position))
                .toList();
        return Optional.of(new Competition(id, name, named.id(), named.name(), entries));
    }

    private static boolean mostlyMusic(List<Placing> placings) {
        final List<Placing> typed = placings.stream().filter(placing -> !placing.types().isEmpty()).toList();
        final long music = typed.stream().filter(placing -> placing.types().stream().anyMatch(Type::music)).count();
        return music * 2 > typed.size();
    }

    private static Type commonestMusicType(List<Placing> placings) {
        final List<Type> types = placings.stream().flatMap(placing -> placing.types().stream()).filter(Type::music).toList();
        final Map<Integer, Long> counts = new HashMap<>(types.stream().collect(Collectors.groupingBy(Type::id, Collectors.counting())));
        return types.stream().max(Comparator.comparing(type -> counts.get(type.id()))).orElse(new Type(NO_TYPE, "", true));
    }

    private static CompoEntry entry(Placing placing, int competitionTypeId) {
        final Set<Integer> types = placing.types().stream().map(Type::id).collect(Collectors.toUnmodifiableSet());
        return new CompoEntry(placing.position(), placing.ranking(), placing.productionId(), placing.title(), placing.authors(),
                types.isEmpty() ? Set.of(competitionTypeId) : types);
    }
}

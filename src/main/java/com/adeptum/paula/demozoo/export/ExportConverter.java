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
import com.adeptum.paula.demozoo.Link;
import com.adeptum.paula.demozoo.Nick;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.PartySeries;
import com.adeptum.paula.demozoo.Production;
import com.adeptum.paula.demozoo.Work;
import com.adeptum.paula.demozoo.export.PgCopyReader.RowHandler;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongConsumer;

/**
 * Turns Demozoo's nightly database export into the music catalogue.
 *
 * <p>The export is read twice. The first pass learns the parties, the competitions and their placings, the
 * production types and which productions are music; only then is it known which productions matter, and the
 * second pass keeps the titles, credits, links and pictures of those alone. Reading twice costs a few seconds and
 * keeps the converter free of any assumption about the order the tables come in.
 *
 * <p>Lists come out in the order Demozoo's own API served them, since that is the order the browser shows: a
 * party's competitions by name, results by position, a production's credits by nick, types by their place in
 * Demozoo's type tree, platforms by name, links by class and address, and a musician's works newest first. The
 * picture is the first screenshot added, Demozoo itself keeping them in no fixed order.
 */
public final class ExportConverter {

    private static final String SCENE_ORG_FOLDER = "SceneOrgFolder";
    private static final String MUSIC = "music";
    private static final String MUSIC_TYPE = "music";
    private static final int YEAR_LENGTH = 4;
    private static final String YEAR_PRECISION = "y";
    private static final String MONTH_PRECISION = "m";
    private static final String MONTH_SUFFIX = "-01";

    /**
     * How far through the export the conversion has come, counted in compressed bytes over both passes.
     */
    @FunctionalInterface
    public interface Watcher {

        Watcher NONE = (read, total) -> { };

        void read(long bytes, long total);
    }

    private final Map<String, String> strings = new HashMap<>();

    private final Map<Integer, String> seriesNames = new HashMap<>();
    private final Map<Integer, PartyRow> parties = new HashMap<>();
    private final Map<Integer, String> sceneOrgFolders = new HashMap<>();
    private final Map<Integer, CompetitionRow> competitions = new HashMap<>();
    private final Map<Integer, List<PlacingRow>> placings = new HashMap<>();
    private final Map<Integer, TypeRow> types = new HashMap<>();
    private final Set<Integer> musicProductions = new HashSet<>();
    private final Set<Integer> groups = new HashSet<>();
    private final Map<Integer, String> platforms = new HashMap<>();

    private final Set<Integer> kept = new HashSet<>();
    private final Map<Integer, ProductionRow> productions = new HashMap<>();
    private final Map<Integer, NickRow> nicks = new HashMap<>();
    private Optional<String> musicRoot;

    private ExportConverter() {
    }

    private record PartyRow(int id, String name, Integer seriesId, String startDate) {
    }

    private record CompetitionRow(int id, int partyId, String name, Integer typeId) {
    }

    private record PlacingRow(int productionId, String ranking, int position) {
    }

    private record TypeRow(int id, String name, String path, String internalName) {
    }

    private record NickRow(String name, int releaserId) {
    }

    private record LinkRow(int id, String linkClass, String url, boolean download) {
    }

    private static final class ProductionRow {

        private final int id;
        private final String title;
        private final String releaseDate;
        private final boolean music;
        private final List<Integer> typeIds = new ArrayList<>(1);
        private final List<Integer> platformIds = new ArrayList<>(1);
        private final List<Integer> nickIds = new ArrayList<>(1);
        private final List<LinkRow> links = new ArrayList<>(1);
        private int screenshotId = Integer.MAX_VALUE;
        private String picture;

        private ProductionRow(int id, String title, String releaseDate, boolean music) {
            this.id = id;
            this.title = title;
            this.releaseDate = releaseDate;
            this.music = music;
        }
    }

    /**
     * Converts the gzipped export at the given path.
     */
    public static MusicCatalogue convert(Path export, Watcher watcher) throws IOException {
        final long size = Files.size(export);
        final ExportConverter converter = new ExportConverter();
        try (InputStream in = new Counting(Files.newInputStream(export), read -> watcher.read(read, size * 2))) {
            PgCopyReader.read(in, converter.firstPass());
        }
        converter.chooseProductions();
        try (InputStream in = new Counting(Files.newInputStream(export), read -> watcher.read(size + read, size * 2))) {
            PgCopyReader.read(in, converter.secondPass());
        }
        return converter.catalogue();
    }

    private Map<String, RowHandler> firstPass() {
        final Map<String, RowHandler> handlers = new HashMap<>();
        handlers.put("parties_partyseries", row -> seriesNames.put(row.integer("id"), text(row.text("name"))));
        handlers.put("parties_party", row -> {
            final int id = row.integer("id");
            parties.put(id, new PartyRow(id, text(row.text("name")), row.integerOrNull("party_series_id"),
                    Objects.requireNonNullElse(fuzzyDate(row.text("start_date_date"), row.text("start_date_precision")), "")));
        });
        handlers.put("parties_partyexternallink", row -> {
            if (SCENE_ORG_FOLDER.equals(row.text("link_class"))) {
                final int partyId = row.integer("party_id");
                LinkUrls.url(SCENE_ORG_FOLDER, row.text("parameter"))
                        .ifPresent(url -> sceneOrgFolders.merge(partyId, url, ExportConverter::first));
            }
        });
        handlers.put("parties_competition", row -> {
            final int id = row.integer("id");
            competitions.put(id, new CompetitionRow(id, row.integer("party_id"), text(row.text("name")),
                    row.integerOrNull("production_type_id")));
        });
        handlers.put("parties_competitionplacing", row -> placings.computeIfAbsent(row.integer("competition_id"), id -> new ArrayList<>())
                .add(new PlacingRow(row.integer("production_id"), text(row.text("ranking")), positionOf(row.integerOrNull("position")))));
        handlers.put("productions_productiontype", row -> {
            final int id = row.integer("id");
            types.put(id, new TypeRow(id, text(row.text("name")), row.text("path"), row.text("internal_name")));
        });
        handlers.put("productions_production", row -> {
            if (MUSIC.equals(row.text("supertype"))) {
                musicProductions.add(row.integer("id"));
            }
        });
        handlers.put("demoscene_releaser", row -> {
            if (row.flag("is_group")) {
                groups.add(row.integer("id"));
            }
        });
        handlers.put("platforms_platform", row -> platforms.put(row.integer("id"), text(row.text("name"))));
        return handlers;
    }

    /**
     * Music productions are kept, and so is everything entered in a competition that may turn out to be a music
     * one, since an untyped competition is judged by the types of all its entries.
     */
    private void chooseProductions() {
        kept.addAll(musicProductions);
        competitions.values().stream()
                .filter(competition -> competition.typeId() == null || isMusicType(competition.typeId()))
                .forEach(competition -> placings.getOrDefault(competition.id(), List.of())
                        .forEach(placing -> kept.add(placing.productionId())));
    }

    private Map<String, RowHandler> secondPass() {
        final Map<String, RowHandler> handlers = new HashMap<>();
        handlers.put("productions_production", row -> {
            final int id = row.integer("id");
            if (kept.contains(id)) {
                productions.put(id, new ProductionRow(id, text(row.text("title")),
                        fuzzyDate(row.text("release_date_date"), row.text("release_date_precision")), musicProductions.contains(id)));
            }
        });
        handlers.put("productions_production_types", row -> withProduction(row.integer("production_id"),
                production -> production.typeIds.add(row.integer("productiontype_id"))));
        handlers.put("productions_production_platforms", row -> withProduction(row.integer("production_id"),
                production -> production.platformIds.add(row.integer("platform_id"))));
        handlers.put("productions_production_author_nicks", row -> withProduction(row.integer("production_id"),
                production -> production.nickIds.add(row.integer("nick_id"))));
        handlers.put("demoscene_nick", row -> nicks.put(row.integer("id"), new NickRow(text(row.text("name")), row.integer("releaser_id"))));
        handlers.put("productions_productionlink", row -> withProduction(row.integer("production_id"), production -> {
            final String linkClass = row.text("link_class");
            final int id = row.integer("id");
            final boolean download = row.flag("is_download_link");
            LinkUrls.url(linkClass, row.text("parameter")).ifPresent(url ->
                    production.links.add(new LinkRow(id, text(linkClass), url, download)));
        }));
        handlers.put("productions_screenshot", row -> withProduction(row.integer("production_id"), production -> {
            final int id = row.integer("id");
            final String url = row.text("standard_url");
            if (url != null && !url.isEmpty() && id < production.screenshotId) {
                production.screenshotId = id;
                production.picture = url;
            }
        }));
        return handlers;
    }

    private interface ProductionUpdate {
        void apply(ProductionRow production) throws IOException;
    }

    private void withProduction(int id, ProductionUpdate update) throws IOException {
        final ProductionRow production = productions.get(id);
        if (production != null) {
            update.apply(production);
        }
    }

    private MusicCatalogue catalogue() {
        final Map<Integer, List<Nick>> credits = new HashMap<>();
        final Map<Integer, List<MusicCompetitions.Type>> productionTypes = new HashMap<>();
        productions.values().forEach(production -> {
            credits.put(production.id, creditsOf(production));
            productionTypes.put(production.id, typesOf(production));
        });
        return new MusicCatalogue(series(), musicCompetitions(credits, productionTypes), sceneOrgFolders,
                productionsById(), works(credits, productionTypes));
    }

    private Map<Integer, PartySeries> series() {
        final Map<Integer, List<Party>> partiesBySeries = new HashMap<>();
        parties.values().stream()
                .filter(party -> party.seriesId() != null)
                .sorted(Comparator.comparing(PartyRow::name).thenComparing(PartyRow::startDate))
                .forEach(party -> partiesBySeries.computeIfAbsent(party.seriesId(), id -> new ArrayList<>())
                        .add(new Party(party.id(), party.name(), party.startDate())));
        final Map<Integer, PartySeries> series = new HashMap<>();
        seriesNames.forEach((id, name) -> series.put(id, new PartySeries(id, name,
                partiesBySeries.getOrDefault(id, List.of()).stream().sorted(Comparator.comparing(Party::startDate)).toList())));
        return series;
    }

    private Map<Integer, List<Competition>> musicCompetitions(Map<Integer, List<Nick>> credits,
            Map<Integer, List<MusicCompetitions.Type>> productionTypes) {
        final Map<Integer, List<Competition>> byParty = new HashMap<>();
        competitions.values().stream()
                .sorted(Comparator.comparing(CompetitionRow::name).thenComparingInt(CompetitionRow::id))
                .forEach(competition -> {
                    final MusicCompetitions.Type type = competition.typeId() == null ? null : typeOf(competition.typeId());
                    if (type != null && !type.music()) {
                        return;
                    }
                    final List<MusicCompetitions.Placing> results = placings.getOrDefault(competition.id(), List.of()).stream()
                            .filter(placing -> productions.containsKey(placing.productionId()))
                            .sorted(Comparator.comparingInt(PlacingRow::position).thenComparingInt(PlacingRow::productionId))
                            .map(placing -> new MusicCompetitions.Placing(placing.position(), placing.ranking(), placing.productionId(),
                                    productions.get(placing.productionId()).title, credits.get(placing.productionId()),
                                    productionTypes.get(placing.productionId())))
                            .toList();
                    MusicCompetitions.judge(competition.id(), competition.name(), type, results)
                            .ifPresent(music -> byParty.computeIfAbsent(competition.partyId(), id -> new ArrayList<>()).add(music));
                });
        return byParty;
    }

    private Map<Integer, Production> productionsById() {
        final Map<Integer, Production> byId = new HashMap<>();
        productions.values().forEach(production -> {
            final List<LinkRow> links = production.links.stream()
                    .sorted(Comparator.comparing(LinkRow::linkClass).thenComparing(LinkRow::url).thenComparingInt(LinkRow::id))
                    .toList();
            byId.put(production.id, new Production(production.id, production.title,
                    links.stream().filter(LinkRow::download).map(ExportConverter::link).toList(),
                    links.stream().filter(link -> !link.download()).map(ExportConverter::link).toList(),
                    production.picture));
        });
        return byId;
    }

    /**
     * A musician's works are the music credited to any of their nicks, newest first as Demozoo listed them, with
     * the undated ahead of the dated the way the database sorts a missing date; among equals the later entry in
     * Demozoo leads.
     */
    private Map<Integer, List<Work>> works(Map<Integer, List<Nick>> credits, Map<Integer, List<MusicCompetitions.Type>> productionTypes) {
        final Map<Integer, Set<ProductionRow>> byReleaser = new HashMap<>();
        productions.values().stream().filter(production -> production.music).forEach(production -> production.nickIds.forEach(nickId -> {
            final NickRow nick = nicks.get(nickId);
            if (nick != null) {
                byReleaser.computeIfAbsent(nick.releaserId(), id -> new LinkedHashSet<>()).add(production);
            }
        }));
        final Comparator<ProductionRow> newestFirst = Comparator
                .comparing((ProductionRow production) -> production.releaseDate, Comparator.nullsFirst(Comparator.<String>reverseOrder()))
                .thenComparing(Comparator.comparingInt((ProductionRow production) -> production.id).reversed());
        final Map<Integer, List<Work>> works = new HashMap<>();
        byReleaser.forEach((releaserId, made) -> {
            final List<ProductionRow> ordered = made.stream().sorted(newestFirst).toList();
            final List<Work> list = new ArrayList<>(ordered.size());
            for (int index = 0; index < ordered.size(); index++) {
                final ProductionRow production = ordered.get(index);
                final Set<Integer> typeIds = productionTypes.get(production.id).stream().map(MusicCompetitions.Type::id)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                final CompoEntry entry = new CompoEntry(index + 1, "", production.id, production.title, credits.get(production.id),
                        Set.copyOf(typeIds));
                list.add(new Work(entry, yearOf(production.releaseDate), platformOf(production)));
            }
            works.put(releaserId, list);
        });
        return works;
    }

    private List<Nick> creditsOf(ProductionRow production) {
        return production.nickIds.stream()
                .map(nicks::get)
                .filter(nick -> nick != null && !nick.name().isBlank())
                .sorted(Comparator.comparing(NickRow::name))
                .map(nick -> new Nick(nick.name(), nick.releaserId(), groups.contains(nick.releaserId())))
                .toList();
    }

    private List<MusicCompetitions.Type> typesOf(ProductionRow production) {
        return production.typeIds.stream()
                .map(types::get)
                .filter(type -> type != null)
                .sorted(Comparator.comparing(TypeRow::path))
                .map(type -> new MusicCompetitions.Type(type.id(), type.name(), isMusicPath(type.path())))
                .toList();
    }

    private String platformOf(ProductionRow production) {
        return production.platformIds.stream()
                .map(platforms::get)
                .filter(name -> name != null)
                .min(Comparator.comparing(name -> name.toLowerCase(java.util.Locale.ROOT)))
                .orElse("");
    }

    private MusicCompetitions.Type typeOf(int typeId) {
        final TypeRow type = types.get(typeId);
        return type == null ? new MusicCompetitions.Type(typeId, "", false)
                : new MusicCompetitions.Type(typeId, type.name(), isMusicPath(type.path()));
    }

    private boolean isMusicType(int typeId) {
        final TypeRow type = types.get(typeId);
        return type != null && isMusicPath(type.path());
    }

    /**
     * Demozoo's types form a tree stored as materialised paths; a type is music when its path starts with that of
     * the type named music.
     */
    private boolean isMusicPath(String path) {
        if (musicRoot == null) {
            musicRoot = types.values().stream().filter(type -> MUSIC_TYPE.equals(type.internalName())).map(TypeRow::path).findFirst();
        }
        return musicRoot.map(root -> path != null && path.startsWith(root)).orElse(false);
    }

    private static Link link(LinkRow link) {
        return new Link(link.linkClass(), link.url());
    }

    private static String yearOf(String date) {
        return date == null || date.length() < YEAR_LENGTH ? "" : date.substring(0, YEAR_LENGTH);
    }

    /**
     * Demozoo stores a date with how much of it is known and shows only that much: a year alone, a year and a
     * month, or the whole date.
     */
    private String fuzzyDate(String date, String precision) {
        if (date == null) {
            return null;
        }
        if (YEAR_PRECISION.equals(precision)) {
            return text(date.substring(0, YEAR_LENGTH));
        }
        return text(MONTH_PRECISION.equals(precision) ? date.substring(0, date.length() - MONTH_SUFFIX.length()) : date);
    }

    private static int positionOf(Integer position) {
        return position == null ? 0 : position;
    }

    private static String first(String kept, String later) {
        return kept;
    }

    /**
     * Each distinct string is held once, since the same nick, platform and title recur across the catalogue.
     */
    private String text(String value) {
        return value == null ? "" : strings.computeIfAbsent(value, same -> same);
    }

    /**
     * Counts what is read through it, so the conversion can say how far it has come.
     */
    private static final class Counting extends FilterInputStream {

        private final LongConsumer progress;
        private long read;

        private Counting(InputStream in, LongConsumer progress) {
            super(in);
            this.progress = progress;
        }

        @Override
        public int read() throws IOException {
            final int value = super.read();
            if (value >= 0) {
                advance(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            final int count = super.read(buffer, offset, length);
            if (count > 0) {
                advance(count);
            }
            return count;
        }

        private void advance(int count) {
            read += count;
            progress.accept(read);
        }
    }
}

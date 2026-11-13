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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The catalogue as one gzip-compressed binary file. Every distinct string and nick is written once into a
 * table and entries refer to them by index, which keeps the file small; the format carries no class names, so
 * a corrupt or hostile file can only make the read fail, never execute anything, and the native image needs
 * nothing registered for it.
 */
public final class CatalogueFormat {

    static final int MAGIC = 0x505A445A;
    static final short VERSION = 1;

    private static final int NO_STRING = -1;
    private static final int MAX_COUNT = 10_000_000;
    private static final int MAX_STRING_LENGTH = 1 << 20;

    private CatalogueFormat() {
        /* Empty on purpose */
    }

    public static void write(MusicCatalogue catalogue, OutputStream out) throws IOException {
        final Strings strings = new Strings();
        final Nicks nicks = new Nicks();
        collect(catalogue, strings, nicks);
        try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(out))) {
            data.writeInt(MAGIC);
            data.writeShort(VERSION);
            strings.writeTo(data);
            nicks.writeTo(data, strings);
            writeSeries(data, strings, catalogue.series());
            writeCompetitions(data, strings, nicks, catalogue.competitions());
            writeSceneOrgFolders(data, strings, catalogue.sceneOrgFolders());
            writeProductions(data, strings, catalogue.productions());
            writeWorks(data, strings, nicks, catalogue.works());
        }
    }

    public static MusicCatalogue read(InputStream in) throws IOException {
        try (DataInputStream data = new DataInputStream(new GZIPInputStream(in))) {
            requireMagic(data);
            requireVersion(data);
            final List<String> strings = readStrings(data);
            final List<Nick> nicks = readNicks(data, strings);
            final Map<Integer, PartySeries> series = readSeries(data, strings);
            final Map<Integer, List<Competition>> competitions = readCompetitions(data, strings, nicks);
            final Map<Integer, String> sceneOrgFolders = readSceneOrgFolders(data, strings);
            final Map<Integer, Production> productions = readProductions(data, strings);
            final Map<Integer, List<Work>> works = readWorks(data, strings, nicks);
            return new MusicCatalogue(series, competitions, sceneOrgFolders, productions, works);
        }
    }

    private static void requireMagic(DataInputStream data) throws IOException {
        if (data.readInt() != MAGIC) {
            throw new IOException("not a music catalogue");
        }
    }

    private static void requireVersion(DataInputStream data) throws IOException {
        final short version = data.readShort();
        if (version != VERSION) {
            throw new IOException("unsupported catalogue version " + version);
        }
    }

    private static void collect(MusicCatalogue catalogue, Strings strings, Nicks nicks) {
        for (final PartySeries series : catalogue.series().values()) {
            strings.index(series.name());
            for (final Party party : series.parties()) {
                strings.index(party.name());
                strings.index(party.startDate());
            }
        }
        for (final List<Competition> competitions : catalogue.competitions().values()) {
            for (final Competition competition : competitions) {
                strings.index(competition.name());
                strings.index(competition.typeName());
                competition.entries().forEach(entry -> collect(entry, strings, nicks));
            }
        }
        catalogue.sceneOrgFolders().values().forEach(strings::index);
        for (final Production production : catalogue.productions().values()) {
            strings.index(production.title());
            production.downloads().forEach(link -> collect(link, strings));
            production.externals().forEach(link -> collect(link, strings));
            if (production.picture() != null) {
                strings.index(production.picture());
            }
        }
        for (final List<Work> works : catalogue.works().values()) {
            for (final Work work : works) {
                collect(work.entry(), strings, nicks);
                strings.index(work.year());
                strings.index(work.platform());
            }
        }
    }

    private static void collect(CompoEntry entry, Strings strings, Nicks nicks) {
        strings.index(entry.ranking());
        strings.index(entry.title());
        for (final Nick author : entry.authors()) {
            strings.index(author.name());
            nicks.index(author);
        }
    }

    private static void collect(Link link, Strings strings) {
        strings.index(link.linkClass());
        strings.index(link.url());
    }

    private static void writeSeries(DataOutputStream data, Strings strings, Map<Integer, PartySeries> series)
            throws IOException {
        data.writeInt(series.size());
        for (final Map.Entry<Integer, PartySeries> entry : series.entrySet()) {
            final PartySeries value = entry.getValue();
            data.writeInt(entry.getKey());
            data.writeInt(strings.index(value.name()));
            data.writeInt(value.parties().size());
            for (final Party party : value.parties()) {
                data.writeInt(party.id());
                data.writeInt(strings.index(party.name()));
                data.writeInt(strings.index(party.startDate()));
            }
        }
    }

    private static void writeCompetitions(DataOutputStream data, Strings strings, Nicks nicks,
            Map<Integer, List<Competition>> competitions) throws IOException {
        data.writeInt(competitions.size());
        for (final Map.Entry<Integer, List<Competition>> entry : competitions.entrySet()) {
            data.writeInt(entry.getKey());
            data.writeInt(entry.getValue().size());
            for (final Competition competition : entry.getValue()) {
                writeCompetition(data, strings, nicks, competition);
            }
        }
    }

    private static void writeCompetition(DataOutputStream data, Strings strings, Nicks nicks, Competition competition)
            throws IOException {
        data.writeInt(competition.id());
        data.writeInt(strings.index(competition.name()));
        data.writeInt(competition.typeId());
        data.writeInt(strings.index(competition.typeName()));
        data.writeInt(competition.entries().size());
        for (final CompoEntry entry : competition.entries()) {
            writeEntry(data, strings, nicks, entry);
        }
    }

    private static void writeEntry(DataOutputStream data, Strings strings, Nicks nicks, CompoEntry entry)
            throws IOException {
        data.writeInt(entry.position());
        data.writeInt(strings.index(entry.ranking()));
        data.writeInt(entry.productionId());
        data.writeInt(strings.index(entry.title()));
        data.writeInt(entry.authors().size());
        for (final Nick author : entry.authors()) {
            data.writeInt(nicks.index(author));
        }
        data.writeInt(entry.typeIds().size());
        for (final int typeId : entry.typeIds()) {
            data.writeInt(typeId);
        }
    }

    private static void writeSceneOrgFolders(DataOutputStream data, Strings strings, Map<Integer, String> folders)
            throws IOException {
        data.writeInt(folders.size());
        for (final Map.Entry<Integer, String> entry : folders.entrySet()) {
            data.writeInt(entry.getKey());
            data.writeInt(strings.index(entry.getValue()));
        }
    }

    private static void writeProductions(DataOutputStream data, Strings strings, Map<Integer, Production> productions)
            throws IOException {
        data.writeInt(productions.size());
        for (final Map.Entry<Integer, Production> entry : productions.entrySet()) {
            final Production production = entry.getValue();
            data.writeInt(entry.getKey());
            data.writeInt(strings.index(production.title()));
            writeLinks(data, strings, production.downloads());
            writeLinks(data, strings, production.externals());
            data.writeInt(production.picture() == null ? NO_STRING : strings.index(production.picture()));
        }
    }

    private static void writeLinks(DataOutputStream data, Strings strings, List<Link> links) throws IOException {
        data.writeInt(links.size());
        for (final Link link : links) {
            data.writeInt(strings.index(link.linkClass()));
            data.writeInt(strings.index(link.url()));
        }
    }

    private static void writeWorks(DataOutputStream data, Strings strings, Nicks nicks, Map<Integer, List<Work>> works)
            throws IOException {
        data.writeInt(works.size());
        for (final Map.Entry<Integer, List<Work>> entry : works.entrySet()) {
            data.writeInt(entry.getKey());
            data.writeInt(entry.getValue().size());
            for (final Work work : entry.getValue()) {
                writeEntry(data, strings, nicks, work.entry());
                data.writeInt(strings.index(work.year()));
                data.writeInt(strings.index(work.platform()));
            }
        }
    }

    private static List<String> readStrings(DataInputStream data) throws IOException {
        final int count = readCount(data);
        final List<String> strings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            strings.add(readString(data));
        }
        return strings;
    }

    private static String readString(DataInputStream data) throws IOException {
        final int length = data.readInt();
        if (length < 0 || length > MAX_STRING_LENGTH) {
            throw new IOException("catalogue string length out of range: " + length);
        }
        final byte[] bytes = new byte[length];
        data.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<Nick> readNicks(DataInputStream data, List<String> strings) throws IOException {
        final int count = readCount(data);
        final List<Nick> nicks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final String name = stringAt(strings, data.readInt());
            final int releaserId = data.readInt();
            final boolean group = data.readBoolean();
            nicks.add(new Nick(name, releaserId, group));
        }
        return nicks;
    }

    private static Map<Integer, PartySeries> readSeries(DataInputStream data, List<String> strings) throws IOException {
        final int count = readCount(data);
        final Map<Integer, PartySeries> series = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            final int id = data.readInt();
            final String name = stringAt(strings, data.readInt());
            final int partyCount = readCount(data);
            final List<Party> parties = new ArrayList<>(partyCount);
            for (int j = 0; j < partyCount; j++) {
                final Party party = new Party(data.readInt(), stringAt(strings, data.readInt()),
                        stringAt(strings, data.readInt()));
                parties.add(party);
            }
            series.put(id, new PartySeries(id, name, List.copyOf(parties)));
        }
        return series;
    }

    private static Map<Integer, List<Competition>> readCompetitions(DataInputStream data, List<String> strings,
            List<Nick> nicks) throws IOException {
        final int count = readCount(data);
        final Map<Integer, List<Competition>> competitions = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            final int partyId = data.readInt();
            final int competitionCount = readCount(data);
            final List<Competition> partyCompetitions = new ArrayList<>(competitionCount);
            for (int j = 0; j < competitionCount; j++) {
                partyCompetitions.add(readCompetition(data, strings, nicks));
            }
            competitions.put(partyId, List.copyOf(partyCompetitions));
        }
        return competitions;
    }

    private static Competition readCompetition(DataInputStream data, List<String> strings, List<Nick> nicks)
            throws IOException {
        final int id = data.readInt();
        final String name = stringAt(strings, data.readInt());
        final int typeId = data.readInt();
        final String typeName = stringAt(strings, data.readInt());
        final int entryCount = readCount(data);
        final List<CompoEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(readEntry(data, strings, nicks));
        }
        return new Competition(id, name, typeId, typeName, List.copyOf(entries));
    }

    private static CompoEntry readEntry(DataInputStream data, List<String> strings, List<Nick> nicks)
            throws IOException {
        final int position = data.readInt();
        final String ranking = stringAt(strings, data.readInt());
        final int productionId = data.readInt();
        final String title = stringAt(strings, data.readInt());
        final int authorCount = readCount(data);
        final List<Nick> authors = new ArrayList<>(authorCount);
        for (int i = 0; i < authorCount; i++) {
            authors.add(nickAt(nicks, data.readInt()));
        }
        final int typeCount = readCount(data);
        final Set<Integer> typeIds = new HashSet<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            typeIds.add(data.readInt());
        }
        return new CompoEntry(position, ranking, productionId, title, List.copyOf(authors), Set.copyOf(typeIds));
    }

    private static Map<Integer, String> readSceneOrgFolders(DataInputStream data, List<String> strings)
            throws IOException {
        final int count = readCount(data);
        final Map<Integer, String> folders = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            folders.put(data.readInt(), stringAt(strings, data.readInt()));
        }
        return folders;
    }

    private static Map<Integer, Production> readProductions(DataInputStream data, List<String> strings)
            throws IOException {
        final int count = readCount(data);
        final Map<Integer, Production> productions = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            final int id = data.readInt();
            final String title = stringAt(strings, data.readInt());
            final List<Link> downloads = readLinks(data, strings);
            final List<Link> externals = readLinks(data, strings);
            final int pictureIndex = data.readInt();
            final String picture = pictureIndex == NO_STRING ? null : stringAt(strings, pictureIndex);
            productions.put(id, new Production(id, title, downloads, externals, picture));
        }
        return productions;
    }

    private static List<Link> readLinks(DataInputStream data, List<String> strings) throws IOException {
        final int count = readCount(data);
        final List<Link> links = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            links.add(new Link(stringAt(strings, data.readInt()), stringAt(strings, data.readInt())));
        }
        return List.copyOf(links);
    }

    private static Map<Integer, List<Work>> readWorks(DataInputStream data, List<String> strings, List<Nick> nicks)
            throws IOException {
        final int count = readCount(data);
        final Map<Integer, List<Work>> works = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            final int releaserId = data.readInt();
            final int workCount = readCount(data);
            final List<Work> releaserWorks = new ArrayList<>(workCount);
            for (int j = 0; j < workCount; j++) {
                final CompoEntry entry = readEntry(data, strings, nicks);
                final Work work = new Work(entry, stringAt(strings, data.readInt()), stringAt(strings, data.readInt()));
                releaserWorks.add(work);
            }
            works.put(releaserId, List.copyOf(releaserWorks));
        }
        return works;
    }

    private static String stringAt(List<String> strings, int index) throws IOException {
        if (index < 0 || index >= strings.size()) {
            throw new IOException("catalogue string index out of range: " + index);
        }
        return strings.get(index);
    }

    private static Nick nickAt(List<Nick> nicks, int index) throws IOException {
        if (index < 0 || index >= nicks.size()) {
            throw new IOException("catalogue nick index out of range: " + index);
        }
        return nicks.get(index);
    }

    private static int readCount(DataInputStream data) throws IOException {
        final int count = data.readInt();
        if (count < 0 || count > MAX_COUNT) {
            throw new IOException("catalogue count out of range: " + count);
        }
        return count;
    }

    private static void writeString(DataOutputStream data, String value) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private static final class Strings {

        private final Map<String, Integer> indices = new LinkedHashMap<>();

        int index(String value) {
            return indices.computeIfAbsent(value, ignored -> indices.size());
        }

        void writeTo(DataOutputStream data) throws IOException {
            data.writeInt(indices.size());
            for (final String value : indices.keySet()) {
                writeString(data, value);
            }
        }
    }

    private static final class Nicks {

        private final Map<Nick, Integer> indices = new LinkedHashMap<>();

        int index(Nick nick) {
            return indices.computeIfAbsent(nick, ignored -> indices.size());
        }

        void writeTo(DataOutputStream data, Strings strings) throws IOException {
            data.writeInt(indices.size());
            for (final Nick nick : indices.keySet()) {
                data.writeInt(strings.index(nick.name()));
                data.writeInt(nick.releaserId());
                data.writeBoolean(nick.group());
            }
        }
    }
}

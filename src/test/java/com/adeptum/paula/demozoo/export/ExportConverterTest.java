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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.Link;
import com.adeptum.paula.demozoo.Nick;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.PartySeries;
import com.adeptum.paula.demozoo.Production;
import com.adeptum.paula.demozoo.Work;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportConverterTest {

    private static final String NULL = "\\N";

    /* The series table's note column is never read; it shows that columns are found by name, not by place. */
    private static final String DUMP =
            copy("parties_partyseries", "id, name, note",
                    row("2", "Assembly", "unused"),
                    row("3", "Kindergarden", "unused"),
                    row("4", "The Gathering", "unused"))
            + copy("parties_party", "id, name, party_series_id, start_date_date, start_date_precision",
                    row("101", "Assembly 1996", "2", "1996-03-15", "m"),
                    row("102", "Assembly 1995", "2", "1995-08-10", "y"),
                    row("103", "Kindergarden 1997", "3", "1997-08-01", "d"),
                    row("104", "The Gathering 1998", "4", NULL, NULL))
            + copy("parties_partyexternallink", "link_class, parameter, party_id",
                    row("SceneOrgFolder", "/parties/1996/asm96/", "101"),
                    row("PouetParty", "https://www.pouet.net/party.php?which=1", "101"))
            + copy("parties_competition", "id, party_id, name, production_type_id",
                    row("201", "101", "4 Channel Music", "29"),
                    row("202", "101", "Demo", "1"),
                    row("203", "101", "Chip Music", "31"),
                    row("204", "101", "Chip Music", "29"),
                    row("205", "103", "4 Channel Music", NULL),
                    row("210", "104", "Demo", "1"),
                    row("211", "104", "Musicdisk", "7"),
                    row("212", "104", "Wild", NULL))
            + copy("parties_competitionplacing", "competition_id, production_id, ranking, \"position\"",
                    row("201", "301", "2", "2"),
                    row("201", "302", "1st", "1"),
                    row("201", "303", NULL, "3"),
                    row("205", "322", "3", "3"),
                    row("205", "321", "1", "1"),
                    row("205", "320", "2", "2"),
                    row("205", "323", "4", "4"),
                    row("210", "310", "1", "1"),
                    row("211", "311", "1", "1"),
                    row("212", "312", "1", "1"),
                    row("212", "313", "2", "2"),
                    row("212", "314", "3", "3"))
            + copy("productions_productiontype", "id, name, path, internal_name",
                    row("1", "Demo", "0006", "demo"),
                    row("7", "Musicdisk", "000F", "musicdisk"),
                    row("14", "Music", "000E", "music"),
                    row("29", "Tracked Music", "000E0007", "tracked_music"),
                    row("31", "Executable Music", "000E0004", "executable_music"))
            + copy("productions_production", "id, title, release_date_date, release_date_precision, supertype",
                    row("301", "Second Song", "1996-03-15", "d", "music"),
                    row("302", "First Song", "1995-08-10", "y", "music"),
                    row("303", "NonMusic Entry", NULL, NULL, "production"),
                    row("310", "A Demo", "1998-01-01", "d", "production"),
                    row("311", "A Musicdisk", "1998-01-02", "d", "production"),
                    row("312", "Wild Demo", NULL, NULL, "production"),
                    row("313", "Wild Tune", NULL, NULL, "music"),
                    row("314", "Wild Video", NULL, NULL, "production"),
                    row("320", "Kindergarden Tune", NULL, NULL, "music"),
                    row("321", "Kindergarden Exe", NULL, NULL, "music"),
                    row("322", "Another Tune", NULL, NULL, "music"),
                    row("323", "Kindergarden Untyped", NULL, NULL, "music"),
                    row("330", "Undated Tune", NULL, NULL, "music"),
                    row("331", "A Picture", NULL, NULL, "graphics"),
                    row("332", "Newest Tune", "1997-12-01", "d", "music"),
                    row("333", "Month Tune", "1996-12-24", "m", "music"))
            + copy("productions_production_types", "production_id, productiontype_id",
                    row("301", "29"),
                    row("302", "29"),
                    row("310", "1"),
                    row("311", "7"),
                    row("312", "1"),
                    row("313", "31"),
                    row("314", "1"),
                    row("320", "29"),
                    row("321", "31"),
                    row("322", "29"),
                    row("330", "29"),
                    row("332", "29"),
                    row("333", "29"))
            + copy("productions_production_platforms", "production_id, platform_id",
                    row("301", "1"),
                    row("301", "2"))
            + copy("productions_production_author_nicks", "production_id, nick_id",
                    row("301", "501"),
                    row("301", "502"),
                    row("302", "503"),
                    row("302", "504"),
                    row("302", "505"),
                    row("330", "501"),
                    row("330", "506"),
                    row("331", "501"),
                    row("332", "506"),
                    row("333", "501"))
            + copy("demoscene_nick", "id, releaser_id, name",
                    row("501", "601", "Theseus"),
                    row("502", "602", "Spaceballs"),
                    row("503", "601", "beta"),
                    row("504", "603", NULL),
                    row("505", "604", "Alpha"),
                    row("506", "601", "T$"))
            + copy("demoscene_releaser", "id, is_group",
                    row("601", "f"),
                    row("602", "t"),
                    row("603", "f"),
                    row("604", "f"))
            + copy("platforms_platform", "id, name",
                    row("1", "ZX Spectrum"),
                    row("2", "amiga"))
            + copy("productions_productionlink", "id, link_class, parameter, production_id, is_download_link",
                    row("9001", "AmpModule", "1234", "301", "t"),
                    row("9002", "SceneOrgFile", "/parties/1996/music/zeta.zip", "301", "t"),
                    row("9003", "SceneOrgFile", "/parties/1996/music/funky tune.zip", "301", "t"),
                    row("9004", "ModarchiveModule", "moduleid=5678", "301", "f"),
                    row("9005", "UnknownClass", "whatever", "301", "f"))
            + copy("productions_screenshot", "id, production_id, standard_url",
                    row("900", "301", "https://media.demozoo.org/s/900.png"),
                    row("899", "301", NULL),
                    row("898", "301", "https://media.demozoo.org/s/898.png"),
                    row("897", "301", ""));

    @Test
    void sortsThePartiesOfASeriesByStartDateShowingOnlyTheKnownPart(@TempDir Path dir) throws IOException {
        final PartySeries assembly = catalogue(dir).series(2).orElseThrow();

        assertEquals("Assembly", assembly.name());
        assertEquals(List.of(new Party(102, "Assembly 1995", "1995"), new Party(101, "Assembly 1996", "1996-03")),
                assembly.parties(), "the year-only party first, its date cut to the part Demozoo knows");
    }

    @Test
    void ordersAPartysMusicCompetitionsByNameThenId(@TempDir Path dir) throws IOException {
        final List<Competition> competitions = catalogue(dir).competitions(101);

        assertEquals(List.of(201, 203, 204), competitions.stream().map(Competition::id).toList(),
                "4 Channel Music leads, the two Chip Music entries fall in id order");
        assertEquals(List.of("4 Channel Music", "Chip Music", "Chip Music"),
                competitions.stream().map(Competition::name).toList());
    }

    @Test
    void ranksTheEntriesOfATypedMusicCompetitionByPosition(@TempDir Path dir) throws IOException {
        final Competition competition = catalogue(dir).competitions(101).get(0);

        assertEquals(29, competition.typeId());
        assertEquals(List.of(302, 301, 303), competition.entries().stream().map(CompoEntry::productionId).toList(),
                "the export listed them out of order");
        assertEquals("1st", competition.entries().get(0).ranking(), "the ranking string is Demozoo's own");
        assertEquals("2", competition.entries().get(1).ranking());
        assertEquals(Set.of(29), competition.entries().get(2).typeIds(), "an entry with no types takes the compo's");
    }

    @Test
    void sortsCreditsByNickNameFlagsGroupsAndDropsBlankNicks(@TempDir Path dir) throws IOException {
        final List<CompoEntry> entries = catalogue(dir).competitions(101).get(0).entries();

        assertEquals(List.of(new Nick("Spaceballs", 602, true), new Nick("Theseus", 601, false)),
                entries.get(1).authors(), "the group nick is flagged and leads alphabetically");
        assertEquals(List.of(new Nick("Alpha", 604, false), new Nick("beta", 601, false)),
                entries.get(0).authors(), "the blank nick is dropped");
    }

    @Test
    void judgesAnUntypedCompetitionByItsEntries(@TempDir Path dir) throws IOException {
        final Competition competition = catalogue(dir).competitions(103).get(0);

        assertEquals("4 Channel Music", competition.name());
        assertEquals(29, competition.typeId(), "the commonest music type names the competition");
        assertEquals("Tracked Music", competition.typeName());
        assertEquals(List.of(321, 320, 322, 323), competition.entries().stream().map(CompoEntry::productionId).toList());
        assertEquals(Set.of(29), competition.entries().get(3).typeIds(), "an entry without types takes that type");
    }

    @Test
    void leavesOutNonMusicAndUnknownCompetitionsAndTheirParties(@TempDir Path dir) throws IOException {
        final MusicCatalogue converted = catalogue(dir);

        assertEquals(List.of(), converted.competitions(104),
                "a Demo-typed, a Musicdisk-typed and an untyped mostly-demo competition are no music competitions");
        assertEquals(List.of(), converted.competitions(102), "a party with no music competition is absent");
    }

    @Test
    void ordersLinksBuildsTheirAddressesAndKeepsTheLowestNumberedPicture(@TempDir Path dir) throws IOException {
        final Production production = catalogue(dir).production(301).orElseThrow();

        assertEquals(List.of(
                new Link("AmpModule", "https://amp.dascene.net/downmod.php?index=1234"),
                new Link("SceneOrgFile", "https://files.scene.org/view/parties/1996/music/funky%20tune.zip"),
                new Link("SceneOrgFile", "https://files.scene.org/view/parties/1996/music/zeta.zip")),
                production.downloads(), "by class, then address, with the SceneOrgFile parameter quoted");
        assertEquals(List.of(new Link("ModarchiveModule", "https://modarchive.org/module.php?moduleid=5678")),
                production.externals(), "the unknown class is dropped");
        assertEquals("https://media.demozoo.org/s/898.png", production.picture(),
                "the lowest screenshot id with an address wins");
    }

    @Test
    void keepsTheSceneOrgFolderOfAParty(@TempDir Path dir) throws IOException {
        final MusicCatalogue converted = catalogue(dir);

        assertEquals(Optional.of("https://files.scene.org/browse/parties/1996/asm96/"), converted.sceneOrgFolder(101));
        assertEquals(Optional.empty(), converted.sceneOrgFolder(102));
    }

    @Test
    void findsANonMusicEntryOfAMusicCompetitionByProduction(@TempDir Path dir) throws IOException {
        final MusicCatalogue converted = catalogue(dir);

        final Production production = converted.production(303).orElseThrow();
        assertEquals("NonMusic Entry", production.title());
        assertEquals(303, converted.competitions(101).get(0).entries().get(2).productionId());
    }

    @Test
    void listsTheWorksOfAReleaserCreditedThroughTwoNicks(@TempDir Path dir) throws IOException {
        final List<Work> works = catalogue(dir).works(601);

        assertEquals(List.of("Undated Tune", "Newest Tune", "Month Tune", "Second Song", "First Song"),
                works.stream().map(work -> work.entry().title()).toList(),
                "the undated leads, then the newest first; the graphics production is no work");
        assertEquals(List.of(1, 2, 3, 4, 5), works.stream().map(work -> work.entry().position()).toList());
        assertEquals(List.of("", "1997", "1996", "1996", "1995"), works.stream().map(Work::year).toList());
        assertEquals("amiga", works.get(3).platform(), "the first platform by case-insensitive name");
        assertEquals(List.of(new Nick("Spaceballs", 602, true), new Nick("Theseus", 601, false)),
                works.get(3).entry().authors());
        assertTrue(works.stream().noneMatch(work -> work.entry().productionId() == 331),
                "a graphics production credited to the same releaser is left out");
    }

    @Test
    void reportsTheWholeOfBothPassesToTheWatcher(@TempDir Path dir) throws IOException {
        final Path export = exportFile(dir);
        final AtomicLong read = new AtomicLong();
        final AtomicLong total = new AtomicLong();

        ExportConverter.convert(export, (bytes, whole) -> {
            read.set(bytes);
            total.set(whole);
        });

        assertEquals(2 * Files.size(export), total.get(), "both passes together are the whole export");
        assertEquals(total.get(), read.get(), "the second pass ends at the total");
    }

    private static MusicCatalogue catalogue(Path dir) throws IOException {
        return ExportConverter.convert(exportFile(dir), ExportConverter.Watcher.NONE);
    }

    private static Path exportFile(Path dir) throws IOException {
        final Path export = dir.resolve("export.sql.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(export))) {
            out.write(DUMP.getBytes(StandardCharsets.UTF_8));
        }
        return export;
    }

    private static String copy(String table, String columns, String... rows) {
        return "COPY public." + table + " (" + columns + ") FROM stdin;\n" + String.join("\n", rows) + "\n\\.\n";
    }

    private static String row(String... fields) {
        return String.join("\t", fields);
    }
}

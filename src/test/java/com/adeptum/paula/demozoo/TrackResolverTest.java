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

package com.adeptum.paula.demozoo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.playback.Progress;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.testing.TestArchives;
import com.adeptum.paula.testing.TestModules;
import com.adeptum.paula.testing.TestSids;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackResolverTest {

    private static final String PRODUCTION_URL = "https://demozoo.org/api/v1/productions/7/?format=json";
    private static final String SCENE_ORG_VIEW = "https://files.scene.org/view/parties/1995/assembly95/m4ch/funkyeeh.zip";
    private static final String SCENE_ORG_FILE = "https://archive.scene.org/pub/parties/1995/assembly95/m4ch/funkyeeh.zip";
    private static final String MODLAND_FILE = "https://ftp.modland.com/pub/modules/Protracker/Theseus/funkyeeh.mod";
    private static final String MODLAND_SPACED = "https://ftp.modland.com/pub/modules/Digibooster Pro/TZX/rasp in feelings.dbm";
    private static final String MODARCHIVE_PAGE = "https://modarchive.org/index.php?request=view_by_moduleid&query=123";
    private static final String MODARCHIVE_MODULE_PAGE = "https://modarchive.org/module.php?123";
    private static final String MODARCHIVE_FILE = "https://api.modarchive.org/downloads.php?moduleid=123";
    private static final byte[] README = "hello".getBytes(StandardCharsets.US_ASCII);
    private static final CompoEntry ENTRY = new CompoEntry(1, "1", 7, "Funkyeeh", "Theseus", Set.of(29));

    private final FakeHttp http = new FakeHttp();

    private TrackResolver resolver(Path dir) {
        return resolver(dir, new Progress());
    }

    private TrackResolver resolver(Path dir, Progress progress) {
        final CacheDirectory cache = new CacheDirectory(dir);
        return new TrackResolver(new DemozooClient(http, cache), http, cache,
                ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none()), progress);
    }

    @Test
    void rewritesSceneOrgViewAndGetLinksToTheArchive() {
        assertEquals(URI.create(SCENE_ORG_FILE), TrackResolver.downloadUri(new Link("SceneOrgFile", SCENE_ORG_VIEW)));
        assertEquals(URI.create(SCENE_ORG_FILE), TrackResolver.downloadUri(new Link("SceneOrgFile", SCENE_ORG_VIEW.replace("/view/", "/get/"))));
    }

    @Test
    void rewritesModarchivePagesToTheDownloadApi() {
        assertEquals(URI.create(MODARCHIVE_FILE), TrackResolver.downloadUri(new Link("ModarchiveModule", MODARCHIVE_PAGE)));
        assertEquals(URI.create(MODARCHIVE_FILE), TrackResolver.downloadUri(new Link("ModarchiveModule", MODARCHIVE_MODULE_PAGE)));
    }

    @Test
    void otherLinksAreUsedAsTheyAre() {
        assertEquals(URI.create(MODLAND_FILE), TrackResolver.downloadUri(new Link("ModlandFile", MODLAND_FILE)));
    }

    @Test
    void escapesTheCharactersDemozooLeavesUnescaped() {
        assertEquals(URI.create(MODLAND_SPACED.replace(" ", "%20")),
                TrackResolver.downloadUri(new Link("ModlandFile", MODLAND_SPACED)));
    }

    @Test
    void keepsEscapesThatAreAlreadyThere() {
        final String escaped = MODLAND_SPACED.replace(" ", "%20").replace("Pro/", "%23Pro/");
        assertEquals(URI.create(escaped), TrackResolver.downloadUri(new Link("ModlandFile", escaped)));
    }

    @Test
    void triesSceneOrgThenModarchiveThenModlandThenAnyOtherDownload() {
        final Link sceneOrg = new Link("SceneOrgFile", SCENE_ORG_VIEW);
        final Link modland = new Link("ModlandFile", MODLAND_FILE);
        final Link modarchive = new Link("ModarchiveModule", MODARCHIVE_PAGE);
        final Link other = new Link("AmigascneFile", "https://ftp.amigascne.org/x.lha");
        final Link pouet = new Link("PouetProduction", "https://www.pouet.net/prod.php?which=1");

        assertEquals(List.of(sceneOrg, modarchive, modland), TrackResolver.preferredLinks(production(List.of(modland, sceneOrg), List.of(modarchive))));
        assertEquals(List.of(modarchive, modland, other), TrackResolver.preferredLinks(production(List.of(modland, other), List.of(modarchive))));
        assertEquals(List.of(modland, other), TrackResolver.preferredLinks(production(List.of(other, modland), List.of(pouet))));
        assertEquals(List.of(other), TrackResolver.preferredLinks(production(List.of(other), List.of(pouet))));
        assertEquals(List.of(), TrackResolver.preferredLinks(production(List.of(), List.of(pouet))));
    }

    @Test
    void downloadsExtractsAndReturnsTheFirstPlayableFile(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("readme.txt", README);
        entries.put("music/tune.mod", TestModules.proTracker());
        http.put(SCENE_ORG_FILE, TestArchives.zip(entries), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(dir.resolve("files/7/extracted/music/tune.mod"), resolved);
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
    }

    /**
     * A party archive can hold hundreds of entries, and unpacking one takes long enough that the screen should
     * say so rather than sit on "Loading".
     */
    @Test
    void tellsTheScreenWhatItIsUnpacking(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("readme.txt", README);
        entries.put("music/tune.mod", TestModules.proTracker());
        http.put(SCENE_ORG_FILE, TestArchives.zip(entries), Optional.empty());
        final Progress progress = new Progress();
        final CacheDirectory cache = new CacheDirectory(dir);
        final TrackResolver resolver = new TrackResolver(new DemozooClient(http, cache), http, cache,
                ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none()), progress);

        resolver.resolve(ENTRY);

        final String said = progress.step().orElseThrow();
        assertTrue(said.startsWith("Unpacking funkyeeh.zip"), "it names the archive, said " + said);
        assertTrue(said.endsWith("2 entries"), "and counts its way through, said " + said);
    }

    /**
     * A recorded track runs to megabytes, and a wait with nothing said looks like a refusal.
     */
    @Test
    void countsALongDownloadUpOnTheScreen(@TempDir Path dir) throws IOException {
        final byte[] big = new byte[700 * 1024];
        System.arraycopy(TestModules.proTracker(), 0, big, 0, TestModules.proTracker().length);
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW.replace(".zip", ".mod")));
        http.put(SCENE_ORG_FILE.replace(".zip", ".mod"), big, Optional.empty());
        final Progress progress = new Progress();

        resolver(dir, progress).resolve(ENTRY);

        final String said = progress.step().orElseThrow();
        assertTrue(said.startsWith("Downloading funkyeeh.mod"), "it names the file, said " + said);
        assertTrue(said.endsWith("% of 700 kB"), "and how far along it is, said " + said);
    }

    @Test
    void saysNothingAboutADownloadTooShortToWaitOn(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW.replace(".zip", ".mod")));
        http.put(SCENE_ORG_FILE.replace(".zip", ".mod"), TestModules.proTracker(), Optional.empty());
        final Progress progress = new Progress();

        resolver(dir, progress).resolve(ENTRY);

        assertEquals(Optional.empty(), progress.step(), "a module of a few kilobytes is fetched and played");
    }

    @Test
    void reusesExtractedFilesWithoutRequests(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("tune.mod", TestModules.proTracker())), Optional.empty());
        final Path first = resolver(dir).resolve(ENTRY);
        final int requests = http.requests();

        assertEquals(first, resolver(dir).resolve(ENTRY));
        assertEquals(requests, http.requests());
    }

    @Test
    void acceptsPlainModuleDownloads(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestModules.proTracker(), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(dir.resolve("files/7/funkyeeh.mod"), resolved);
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
    }

    @Test
    void usesTheServerFileNameForNamelessUrls(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, "{\"id\":7,\"title\":\"Funkyeeh\",\"download_links\":[],\"external_links\":["
                + "{\"link_class\":\"ModarchiveModule\",\"url\":\"" + MODARCHIVE_PAGE + "\"}]}");
        http.put(MODARCHIVE_FILE, TestModules.proTracker(), Optional.of("gauged.mod"));

        assertEquals(dir.resolve("files/7/gauged.mod"), resolver(dir).resolve(ENTRY));
    }

    @Test
    void keepsServerFileNamesInsideTheCache(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestModules.proTracker(), Optional.of("../../escaped.mod"));

        assertEquals(dir.resolve("files/7/escaped.mod"), resolver(dir).resolve(ENTRY));
    }

    @Test
    void namesDownloadsWithoutAnyFileName(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", "https://files.scene.org/view/parties/x/"));
        http.put("https://archive.scene.org/pub/parties/x/", TestArchives.zip(Map.of("tune.mod", TestModules.proTracker())), Optional.empty());

        assertEquals(dir.resolve("files/7/extracted/tune.mod"), resolver(dir).resolve(ENTRY));
    }

    @Test
    void neverReusesADownloadedArchiveAsAModule(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestArchives.zip(Map.of("tune.mod", TestModules.proTracker())), Optional.of("archive.mod"));
        final Path first = resolver(dir).resolve(ENTRY);

        assertEquals(dir.resolve("files/7/extracted/tune.mod"), first);
        assertEquals(first, resolver(dir).resolve(ENTRY));
    }

    @Test
    void unwrapsPackedModulesInsideArchives(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("mod.tune", TestArchives.xpk(TestModules.proTracker(), "NUKE", 1000))), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(dir.resolve("files/7/extracted/mod.tune"), resolved);
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
        assertEquals(resolved, resolver(dir).resolve(ENTRY));
    }

    @Test
    void unwrapsAPackedDirectDownload(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestArchives.xpk(TestModules.proTracker(), "NUKE", 1000), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(dir.resolve("files/7/extracted/funkyeeh.mod"), resolved);
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
    }

    @Test
    void failsClearlyWithoutDownloads(@TempDir Path dir) {
        http.put(PRODUCTION_URL, "{\"id\":7,\"title\":\"Funkyeeh\",\"download_links\":[],\"external_links\":[]}");
        final IOException error = assertThrows(IOException.class, () -> resolver(dir).resolve(ENTRY));
        assertEquals("No download for Funkyeeh", error.getMessage());
    }

    @Test
    void failsClearlyWhenTheArchiveHasNoModule(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("readme.txt", README)), Optional.empty());
        final IOException error = assertThrows(IOException.class, () -> resolver(dir).resolve(ENTRY));
        assertTrue(error.getMessage().startsWith("No playable file in funkyeeh.zip"), error.getMessage());
    }

    @Test
    void movesOnToTheNextDownloadWhenTheFirstHoldsNothingPlayable(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW, "ModlandFile", MODLAND_FILE));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("compo.d64", README)), Optional.empty());
        http.put(MODLAND_FILE, TestModules.proTracker(), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
    }

    @Test
    void picksTheFileNamedAfterTheEntryFromABundle(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("aaa.mod", TestModules.proTracker());
        entries.put("theseus.mod", TestModules.digiBooster());
        http.put(SCENE_ORG_FILE, TestArchives.zip(entries), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals("theseus.mod", resolved.getFileName().toString(), "the entry is by Theseus");
    }

    @Test
    void unpacksTheDiskImageInsideAPartyFile(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final byte[] disk = TestArchives.d64(Map.of("THESEUS", TestSids.program()));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("compo.d64", disk)), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals("THESEUS.prg", resolved.getFileName().toString());
        assertArrayEquals(TestSids.program(), Files.readAllBytes(resolved));
    }

    @Test
    void playsTheTuneRatherThanTheWholeReleaseWhereBothAreOffered(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW, "ModlandFile", MODLAND_FILE));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("compo.d64", TestArchives.d64(Map.of("THESEUS", TestSids.program())))), Optional.empty());
        http.put(MODLAND_FILE, TestModules.proTracker(), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals("funkyeeh.mod", resolved.getFileName().toString(), "the module comes before the C64 program");
    }

    @Test
    void failsClearlyOnUnknownDownloads(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW.replace("funkyeeh.zip", "page.html")));
        http.put(SCENE_ORG_FILE.replace("funkyeeh.zip", "page.html"), "<html>".getBytes(StandardCharsets.US_ASCII), Optional.empty());
        final IOException error = assertThrows(IOException.class, () -> resolver(dir).resolve(ENTRY));
        assertEquals("page.html for Funkyeeh is not a module or archive", error.getMessage());
    }

    private static Production production(List<Link> downloads, List<Link> externals) {
        return new Production(7, "Funkyeeh", downloads, externals);
    }

    private static String productionJson(String... classesAndUrls) {
        final StringBuilder links = new StringBuilder();
        for (int at = 0; at < classesAndUrls.length; at += 2) {
            links.append(links.isEmpty() ? "" : ",")
                    .append("{\"link_class\":\"").append(classesAndUrls[at]).append("\",\"url\":\"").append(classesAndUrls[at + 1]).append("\"}");
        }
        return "{\"id\":7,\"title\":\"Funkyeeh\",\"download_links\":[" + links + "],\"external_links\":[]}";
    }

}

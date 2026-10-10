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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.favourites.PreferredFormats;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.javamod.JavaModLoader;
import com.adeptum.paula.playback.Progress;
import com.adeptum.paula.module.sid.SongLengths;
import com.adeptum.paula.testing.TestArchives;
import com.adeptum.paula.testing.TestModules;
import com.adeptum.paula.testing.TestSids;
import com.adeptum.paula.testing.TestTaps;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final CompoEntry ENTRY = new CompoEntry(1, "1", 7, "Funkyeeh", List.of(new Nick("Theseus", 0, false)), Set.of(29));

    private final FakeHttp http = new FakeHttp();

    private TrackResolver resolver(Path dir) {
        return resolver(dir, new Progress());
    }

    private TrackResolver resolver(Path dir, ModuleLoaderRegistry loaders) {
        final CacheDirectory cache = new CacheDirectory(dir);
        return new TrackResolver(new DemozooClient(http, cache), http, cache, loaders, new Progress());
    }

    /**
     * Paula as it stood before it could read X-Tracker modules.
     */
    private static ModuleLoaderRegistry withoutXTracker() {
        return new ModuleLoaderRegistry(List.of(new JavaModLoader()));
    }

    private TrackResolver resolver(Path dir, Progress progress) {
        final CacheDirectory cache = new CacheDirectory(dir);
        return new TrackResolver(new DemozooClient(http, cache), http, cache,
                ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none(), cache), progress);
    }

    private TrackResolver resolver(Path dir, PreferredFormats preferred) {
        final CacheDirectory cache = new CacheDirectory(dir);
        final ModuleLoaderRegistry loaders = ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none(), cache);
        return new TrackResolver(new DemozooClient(http, cache), http, cache, loaders, new Progress(),
                new UnplayableReleases(cache, loaders), preferred);
    }

    private void releaseOf(Map<String, byte[]> files) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(files), Optional.empty());
    }

    private static Map<String, byte[]> threeFormats() throws IOException {
        final Map<String, byte[]> release = new LinkedHashMap<>();
        release.put("Funkyeeh.mp3", recording());
        release.put("Funkyeeh.tap", TestTaps.loaderTape(TestTaps.SQUARE_WAVE));
        release.put("Funkyeeh.mod", TestModules.proTracker());
        return release;
    }

    @Test
    void rewritesSceneOrgViewAndGetLinksToTheArchive() throws IOException {
        assertEquals(URI.create(SCENE_ORG_FILE), TrackResolver.downloadUri(new Link("SceneOrgFile", SCENE_ORG_VIEW)));
        assertEquals(URI.create(SCENE_ORG_FILE), TrackResolver.downloadUri(new Link("SceneOrgFile", SCENE_ORG_VIEW.replace("/view/", "/get/"))));
    }

    @Test
    void rewritesModarchivePagesToTheDownloadApi() throws IOException {
        assertEquals(URI.create(MODARCHIVE_FILE), TrackResolver.downloadUri(new Link("ModarchiveModule", MODARCHIVE_PAGE)));
        assertEquals(URI.create(MODARCHIVE_FILE), TrackResolver.downloadUri(new Link("ModarchiveModule", MODARCHIVE_MODULE_PAGE)));
    }

    @Test
    void otherLinksAreUsedAsTheyAre() throws IOException {
        assertEquals(URI.create(MODLAND_FILE), TrackResolver.downloadUri(new Link("ModlandFile", MODLAND_FILE)));
    }

    @Test
    void escapesTheCharactersDemozooLeavesUnescaped() throws IOException {
        assertEquals(URI.create(MODLAND_SPACED.replace(" ", "%20")),
                TrackResolver.downloadUri(new Link("ModlandFile", MODLAND_SPACED)));
    }

    @Test
    void keepsEscapesThatAreAlreadyThere() throws IOException {
        final String escaped = MODLAND_SPACED.replace(" ", "%20").replace("Pro/", "%23Pro/");
        assertEquals(URI.create(escaped), TrackResolver.downloadUri(new Link("ModlandFile", escaped)));
    }

    @Test
    void refusesAModArchiveLinkWithAnImpossibleId() {
        assertThrows(IOException.class,
                () -> TrackResolver.downloadUri(new Link("ModarchiveModule", "https://modarchive.org/module.php?99999999999")));
    }

    /**
     * A link Demozoo cannot spell used to stop the whole resolve, taking with it the good link behind it.
     */
    @Test
    void skipsALinkThatIsNoUri(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", "https://files.scene.org/view/x[1].zip",
                "ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestModules.proTracker(), Optional.empty());

        assertEquals(downloaded(dir, MODLAND_FILE).resolve("funkyeeh.mod"), resolver(dir).resolve(ENTRY));
        assertEquals(2, http.requests(), "the production, then the one link that reads");
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

        assertEquals(downloaded(dir, SCENE_ORG_FILE).resolve("extracted/music/tune.mod"), resolved);
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
    }

    @Test
    void takesATapeOutOfAZipAsTheTune(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("readme.txt", README);
        entries.put("surprisingly NOT four twenty.tap", TestTaps.loaderTape(TestTaps.SQUARE_WAVE));
        http.put(SCENE_ORG_FILE, TestArchives.zip(entries), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(downloaded(dir, SCENE_ORG_FILE).resolve("extracted/surprisingly NOT four twenty.tap"), resolved);
    }

    /**
     * The 1-bit compos hand in a recording of the tune beside the tape and the tracker module it was made from,
     * all under one name; the recording is only what the others sound like, so it comes last.
     */
    @Test
    void prefersTheTapeAndTheModuleToARecordingOfThem(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> release = new LinkedHashMap<>();
        release.put("Funkyeeh.mp3", recording());
        release.put("Funkyeeh.tap", TestTaps.loaderTape(TestTaps.SQUARE_WAVE));
        http.put(SCENE_ORG_FILE, TestArchives.zip(release), Optional.empty());

        assertEquals("Funkyeeh.tap", resolver(dir).resolve(ENTRY).getFileName().toString());
    }

    @Test
    void prefersAModuleToARecordingOfIt(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> release = new LinkedHashMap<>();
        release.put("Funkyeeh.mp3", recording());
        release.put("Funkyeeh.mod", TestModules.proTracker());
        http.put(SCENE_ORG_FILE, TestArchives.zip(release), Optional.empty());

        assertEquals("Funkyeeh.mod", resolver(dir).resolve(ENTRY).getFileName().toString());
    }

    private static byte[] recording() throws IOException {
        try (InputStream in = TrackResolverTest.class.getResourceAsStream("/mp3/paula-test.mp3")) {
            return in.readAllBytes();
        }
    }

    @Test
    void listsTheFormatsOfADownloadedReleaseInPlayOrder(@TempDir Path dir) throws IOException {
        releaseOf(threeFormats());
        final TrackResolver resolver = resolver(dir, PreferredFormats.inMemory());
        resolver.resolve(ENTRY);

        final List<Variant> variants = resolver.variants(ENTRY);

        assertEquals(List.of("MOD", "TAP", "MP3"), variants.stream().map(Variant::format).toList());
        assertEquals(List.of(true, false, false), variants.stream().map(Variant::plays).toList());
        assertEquals("[MOD]/TAP/MP3", Variant.tag(variants));
    }

    @Test
    void listsNothingForAReleaseThatIsNotDownloaded(@TempDir Path dir) throws IOException {
        releaseOf(threeFormats());

        assertEquals(List.of(), resolver(dir, PreferredFormats.inMemory()).variants(ENTRY));
    }

    @Test
    void listsNothingForAReleaseOfOneFormat(@TempDir Path dir) throws IOException {
        releaseOf(Map.of("Funkyeeh.mod", TestModules.proTracker()));
        final TrackResolver resolver = resolver(dir, PreferredFormats.inMemory());
        resolver.resolve(ENTRY);

        assertEquals(List.of(), resolver.variants(ENTRY));
    }

    @Test
    void listsOnlyTheFilesNamingTheEntryFromACompetitionBundle(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("Funkyeeh.mod", TestModules.proTracker());
        bundle.put("Funkyeeh.tap", TestTaps.loaderTape(TestTaps.SQUARE_WAVE));
        bundle.put("Quiet-Someone.mod", TestModules.proTracker());
        bundle.put("Quiet-Someone.mp3", recording());
        releaseOf(bundle);
        final TrackResolver resolver = resolver(dir, PreferredFormats.inMemory());
        resolver.resolve(ENTRY);

        assertEquals(List.of("MOD", "TAP"), resolver.variants(ENTRY).stream().map(Variant::format).toList());
    }

    @Test
    void namesTheFormatOfAFileNamedTheModlandWayByItsPrefix(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> release = new LinkedHashMap<>();
        release.put("mod.Funkyeeh", TestModules.proTracker());
        release.put("Funkyeeh.mp3", recording());
        releaseOf(release);
        final TrackResolver resolver = resolver(dir, PreferredFormats.inMemory());
        resolver.resolve(ENTRY);

        assertEquals(List.of("MOD", "MP3"), resolver.variants(ENTRY).stream().map(Variant::format).toList());
    }

    @Test
    void tellsTwoPrefixedFormatsApart(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> release = new LinkedHashMap<>();
        release.put("mod.Funkyeeh", TestModules.proTracker());
        release.put("med.Funkyeeh", TestModules.medMmd0());
        releaseOf(release);
        final TrackResolver resolver = resolver(dir, PreferredFormats.inMemory());
        resolver.resolve(ENTRY);

        assertEquals(List.of("MED", "MOD"), resolver.variants(ENTRY).stream().map(Variant::format).toList());
    }

    @Test
    void listsNothingWhereTheFilesAreNotNamedAfterTheEntry(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> numbered = new LinkedHashMap<>();
        numbered.put("01.mod", TestModules.proTracker());
        numbered.put("01.mp3", recording());
        releaseOf(numbered);
        final TrackResolver resolver = resolver(dir, PreferredFormats.inMemory());
        resolver.resolve(ENTRY);

        assertEquals(List.of(), resolver.variants(ENTRY));
    }

    @Test
    void playsTheFormatPreferredAndMarksIt(@TempDir Path dir) throws IOException {
        releaseOf(threeFormats());
        final TrackResolver resolver = resolver(dir, PreferredFormats.inMemory());
        resolver.resolve(ENTRY);
        final Variant tape = resolver.variants(ENTRY).get(1);

        resolver.prefer(ENTRY, tape);

        assertEquals(tape.file(), resolver.resolve(ENTRY));
        assertEquals(List.of(false, true, false), resolver.variants(ENTRY).stream().map(Variant::plays).toList());
    }

    @Test
    void remembersAPreferenceBetweenResolvers(@TempDir Path dir) throws IOException {
        releaseOf(threeFormats());
        final PreferredFormats preferred = PreferredFormats.inMemory();
        final TrackResolver first = resolver(dir, preferred);
        first.resolve(ENTRY);
        final Variant recording = first.variants(ENTRY).get(2);
        first.prefer(ENTRY, recording);

        assertEquals(recording.file(), resolver(dir, preferred).resolve(ENTRY));
    }

    @Test
    void fallsBackToTheDefaultWhenThePreferredFileIsGone(@TempDir Path dir) throws IOException {
        releaseOf(threeFormats());
        final PreferredFormats preferred = PreferredFormats.inMemory();
        final TrackResolver resolver = resolver(dir, preferred);
        final Path defaultPick = resolver.resolve(ENTRY);
        preferred.set(String.valueOf(ENTRY.productionId()), "extracted/Funkyeeh.gone");

        assertEquals(defaultPick, resolver.resolve(ENTRY));
        assertEquals(List.of(true, false, false), resolver.variants(ENTRY).stream().map(Variant::plays).toList());
    }

    @Test
    void fallsBackToTheDefaultWhenThePreferredFileDoesNotOpen(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> release = new LinkedHashMap<>();
        release.put("Funkyeeh.mod", TestModules.proTracker());
        release.put("Funkyeeh.mp3", new byte[] {1, 2, 3});
        releaseOf(release);
        final PreferredFormats preferred = PreferredFormats.inMemory();
        final TrackResolver resolver = resolver(dir, preferred);
        final Path defaultPick = resolver.resolve(ENTRY);
        preferred.set(String.valueOf(ENTRY.productionId()), "extracted/Funkyeeh.mp3");

        assertEquals(defaultPick, resolver.resolve(ENTRY));
    }

    @Test
    void listingLoadsNoFile(@TempDir Path dir) throws IOException {
        releaseOf(threeFormats());
        final TrackResolver resolver = resolver(dir, PreferredFormats.inMemory());
        resolver.resolve(ENTRY);
        assertFalse(Files.exists(dir.resolve("tapes")), "resolving opened only the module");

        resolver.variants(ENTRY);

        assertFalse(Files.exists(dir.resolve("tapes")), "listing the formats must not analyse the tape");
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
                ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none(), cache), progress);

        resolver.resolve(ENTRY);

        final String said = progress.step().orElseThrow().text();
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

        final String said = progress.step().orElseThrow().text();
        assertTrue(said.startsWith("Downloading funkyeeh.mod"), "it names the file, said " + said);
        assertTrue(said.endsWith("% of 700 kB"), "and how far along it is, said " + said);
    }

    /**
     * Counting only at the end is the same as saying nothing: the wait is the whole point, and a line that
     * moves once the download is already done tells nobody anything.
     */
    @Test
    void countsUpThroughoutTheDownloadRatherThanOnlyAtTheEnd(@TempDir Path dir) throws IOException {
        final byte[] big = new byte[3 * 1024 * 1024];
        System.arraycopy(TestModules.proTracker(), 0, big, 0, TestModules.proTracker().length);
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW.replace(".zip", ".mod")));
        http.put(SCENE_ORG_FILE.replace(".zip", ".mod"), big, Optional.empty());
        final Progress progress = new Progress();
        final List<Double> reached = new ArrayList<>();
        http.afterEachBlock(() -> progress.step().filter(Progress.Step::measured).map(Progress.Step::fraction)
                .filter(f -> reached.isEmpty() || Double.compare(reached.get(reached.size() - 1), f) != 0)
                .ifPresent(reached::add));

        resolver(dir, progress).resolve(ENTRY);

        assertTrue(reached.size() > 10, "it was counted up along the way, not once at the end: " + reached.size());
        assertTrue(reached.get(0) < 0.5, "starting well before the end, first was " + reached.get(0));
        assertEquals(reached.stream().sorted().toList(), reached, "and only ever going up");
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

    /**
     * An archive is unpacked through the loaders of the day, so a tune in a format Paula learned later was
     * never taken out of it. The copy on disk is unpacked afresh rather than fetched again.
     */
    @Test
    void unpacksACachedArchiveAgainForAFormatLearnedSince(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("readme.txt", README, "tune.dmf", TestModules.dmf())), Optional.empty());
        assertThrows(IOException.class, () -> resolver(dir, withoutXTracker()).resolve(ENTRY));
        final int requests = http.requests();

        assertEquals(downloaded(dir, SCENE_ORG_FILE).resolve("extracted/tune.dmf"), resolver(dir).resolve(ENTRY));
        assertEquals(requests, http.requests(), "the archive already lay in the cache");
    }

    /**
     * A competition bundle holds a tune for every entrant, so one that was skipped leaves the entry pointing at
     * it resolving to somebody else's tune rather than to nothing at all.
     */
    @Test
    void unpacksAgainEvenWhereTheArchiveAlreadyHeldSomethingPlayable(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("theseus.mod", TestModules.proTracker());
        bundle.put("rival.dmf", TestModules.dmf());
        http.put(SCENE_ORG_FILE, TestArchives.zip(bundle), Optional.empty());
        resolver(dir, withoutXTracker()).resolve(ENTRY);

        resolver(dir).resolve(ENTRY);

        assertTrue(Files.exists(downloaded(dir, SCENE_ORG_FILE).resolve("extracted/rival.dmf")));
    }

    /**
     * A release handed in as executable music comes down whole and holds nothing a loader takes, which is worth
     * remembering across restarts so the browser can say so.
     */
    @Test
    void notesAReleaseWhoseDownloadHoldsNothingPlayable(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("readme.txt", README, "tune.exe", README)), Optional.empty());

        assertThrows(NothingPlayableException.class, () -> resolver(dir).resolve(ENTRY));

        assertTrue(resolver(dir).holdsNothingPlayable(ENTRY.productionId()));
    }

    @Test
    void doesNotNoteAReleaseThatCouldNotBeBroughtDown(@TempDir Path dir) {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final TrackResolver resolver = resolver(dir);

        assertThrows(IOException.class, () -> resolver.resolve(ENTRY));

        assertFalse(resolver.holdsNothingPlayable(ENTRY.productionId()));
    }

    /**
     * A note taken while Paula knew fewer formats says nothing about the formats of today, and a release that
     * plays after all is no longer noted.
     */
    @Test
    void forgetsTheNoteOnceTheReleasePlays(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("readme.txt", README, "tune.dmf", TestModules.dmf())), Optional.empty());
        final TrackResolver before = resolver(dir, withoutXTracker());
        assertThrows(NothingPlayableException.class, () -> before.resolve(ENTRY));
        assertTrue(before.holdsNothingPlayable(ENTRY.productionId()));

        final TrackResolver after = resolver(dir);
        assertFalse(after.holdsNothingPlayable(ENTRY.productionId()));
        after.resolve(ENTRY);

        assertFalse(resolver(dir, withoutXTracker()).holdsNothingPlayable(ENTRY.productionId()));
    }

    @Test
    void leavesAnExtractionAloneWhileTheLoadersAreTheSame(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("tune.mod", TestModules.proTracker())), Optional.empty());
        Files.delete(resolver(dir).resolve(ENTRY));

        assertThrows(IOException.class, () -> resolver(dir).resolve(ENTRY));
    }

    /**
     * A party hands in a whole competition as one archive, and every entry in it points at that same file.
     */
    /**
     * Cache 1994 handed in its music as numbered files, so no file names the entry and the first in name order
     * is somebody else's tune; the title a module carries inside it is what says which entry it is.
     */
    @Test
    void picksTheModuleWhoseOwnTitleNamesTheEntryWhereNoFileNameDoes(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("1.MOD", titled("eagles_fly_alone"));
        bundle.put("17.MOD", titled("the amigo"));
        http.put(SCENE_ORG_FILE, TestArchives.zip(bundle), Optional.empty());
        final CompoEntry amigo = new CompoEntry(1, "1", 7, "The Amigo", List.of(new Nick("Gyu", 0, false)), Set.of(29));

        assertEquals("17.MOD", resolver(dir).resolve(amigo).getFileName().toString());
    }

    /**
     * A title the tracker cut short still names the entry it began.
     */
    @Test
    void takesATitleCutShortByTheTrackerAsNamingTheEntry(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("1.MOD", titled("eagles_fly_alone"));
        bundle.put("15.MOD", titled("expedition on"));
        http.put(SCENE_ORG_FILE, TestArchives.zip(bundle), Optional.empty());
        final CompoEntry expedition = new CompoEntry(7, "7", 7, "Expedition On Planet Earth", List.of(), Set.of(29));

        assertEquals("15.MOD", resolver(dir).resolve(expedition).getFileName().toString());
    }

    /**
     * A file named after the entry still comes first, however its module is titled inside.
     */
    @Test
    void prefersTheFileNamedAfterTheEntryOverAModuleTitledLikeIt(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("a.mod", titled("funkyeeh"));
        bundle.put("theseus.mod", titled("untitled"));
        http.put(SCENE_ORG_FILE, TestArchives.zip(bundle), Optional.empty());

        assertEquals("theseus.mod", resolver(dir).resolve(ENTRY).getFileName().toString());
    }

    /**
     * Antiq 1999 named its C64 entries after title and author, and Jonny handed in three of them.
     */
    @Test
    void prefersTheFileNamingMostOfTheEntry(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("IN SPACE  -JONNY.mod", TestModules.proTracker());
        bundle.put("WILD      -JONNY.mod", TestModules.proTracker());
        http.put(SCENE_ORG_FILE, TestArchives.zip(bundle), Optional.empty());
        final CompoEntry wild = new CompoEntry(4, "4", 7, "Wild", List.of(new Nick("Jonny", 0, false)), Set.of(29));

        assertEquals("WILD      -JONNY.mod", resolver(dir).resolve(wild).getFileName().toString());
    }

    /**
     * Lonely's entry is on the Antiq 1999 disk under another title, while a word of the title Demozoo knows it
     * by names somebody else's tune.
     */
    @Test
    void prefersTheFileNamingTheAuthorOverOneSharingAWordOfTheTitle(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("LOVE WITH U-DOKI.mod", TestModules.proTracker());
        bundle.put("LOVERS    -JONNY.mod", TestModules.proTracker());
        bundle.put("SIDZAK   -LONELY.mod", TestModules.proTracker());
        http.put(SCENE_ORG_FILE, TestArchives.zip(bundle), Optional.empty());
        final CompoEntry ame = new CompoEntry(1, "1", 7, "Ame Sanctuary of Love", List.of(new Nick("Lonely", 0, false)),
                Set.of(29));

        assertEquals("SIDZAK   -LONELY.mod", resolver(dir).resolve(ame).getFileName().toString());
    }

    private static byte[] titled(String title) {
        final byte[] module = TestModules.proTracker();
        final byte[] name = title.getBytes(StandardCharsets.US_ASCII);
        Arrays.fill(module, 0, 20, (byte) 0);
        System.arraycopy(name, 0, module, 0, Math.min(name.length, 20));
        return module;
    }

    /**
     * A rip decades old sometimes carries a module too damaged to open; a working recording beside it,
     * named after the entry just the same, should be offered instead of a file that will only fail to play.
     */
    @Test
    void skipsACandidateThatNamesTheEntryButDoesNotOpen(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("funkyeeh.dbm", "not a real module".getBytes(StandardCharsets.US_ASCII));
        bundle.put("funkyeeh.mod", TestModules.proTracker());
        http.put(SCENE_ORG_FILE, TestArchives.zip(bundle), Optional.empty());

        assertEquals("funkyeeh.mod", resolver(dir).resolve(ENTRY).getFileName().toString());
    }

    @Test
    void fetchesACompetitionBundleOnceForAllItsEntries(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(PRODUCTION_URL.replace("/7/", "/8/"), productionJson(8, "SceneOrgFile", SCENE_ORG_VIEW));
        final Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("theseus.mod", TestModules.proTracker());
        bundle.put("rival.mod", TestModules.proTracker());
        http.put(SCENE_ORG_FILE, TestArchives.zip(bundle), Optional.empty());
        final CompoEntry rival = new CompoEntry(2, "2", 8, "Rasp", List.of(new Nick("Rival", 0, false)), Set.of(29));

        final Path first = resolver(dir).resolve(ENTRY);
        final Path second = resolver(dir).resolve(rival);

        assertEquals("theseus.mod", first.getFileName().toString());
        assertEquals("rival.mod", second.getFileName().toString());
        assertEquals(3, http.requests(), "two productions looked up, one archive fetched");
    }

    @Test
    void doesNotFetchAgainABundleThatHeldNothingPlayable(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(PRODUCTION_URL.replace("/7/", "/8/"), productionJson(8, "SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("readme.txt", README)), Optional.empty());
        final CompoEntry rival = new CompoEntry(2, "2", 8, "Rasp", List.of(new Nick("Rival", 0, false)), Set.of(29));

        assertThrows(IOException.class, () -> resolver(dir).resolve(ENTRY));
        final IOException error = assertThrows(IOException.class, () -> resolver(dir).resolve(rival));

        assertTrue(error.getMessage().startsWith("No playable file in funkyeeh.zip"), error.getMessage());
        assertEquals(3, http.requests(), "the archive was brought down once");
    }

    @Test
    void acceptsPlainModuleDownloads(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestModules.proTracker(), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(downloaded(dir, MODLAND_FILE).resolve("funkyeeh.mod"), resolved);
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
    }

    @Test
    void usesTheServerFileNameForNamelessUrls(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, "{\"id\":7,\"title\":\"Funkyeeh\",\"download_links\":[],\"external_links\":["
                + "{\"link_class\":\"ModarchiveModule\",\"url\":\"" + MODARCHIVE_PAGE + "\"}]}");
        http.put(MODARCHIVE_FILE, TestModules.proTracker(), Optional.of("gauged.mod"));

        assertEquals(downloaded(dir, MODARCHIVE_FILE).resolve("gauged.mod"), resolver(dir).resolve(ENTRY));
    }

    @Test
    void keepsServerFileNamesInsideTheCache(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestModules.proTracker(), Optional.of("../../escaped.mod"));

        assertEquals(downloaded(dir, MODLAND_FILE).resolve("escaped.mod"), resolver(dir).resolve(ENTRY));
    }

    @Test
    void namesDownloadsWithoutAnyFileName(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", "https://files.scene.org/view/parties/x/"));
        http.put("https://archive.scene.org/pub/parties/x/", TestArchives.zip(Map.of("tune.mod", TestModules.proTracker())), Optional.empty());

        assertEquals(downloaded(dir, "https://archive.scene.org/pub/parties/x/").resolve("extracted/tune.mod"), resolver(dir).resolve(ENTRY));
    }

    @Test
    void neverReusesADownloadedArchiveAsAModule(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestArchives.zip(Map.of("tune.mod", TestModules.proTracker())), Optional.of("archive.mod"));
        final Path first = resolver(dir).resolve(ENTRY);

        assertEquals(downloaded(dir, MODLAND_FILE).resolve("extracted/tune.mod"), first);
        assertEquals(first, resolver(dir).resolve(ENTRY));
    }

    @Test
    void unwrapsPackedModulesInsideArchives(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("mod.tune", TestArchives.xpk(TestModules.proTracker(), "NUKE", 1000))), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(downloaded(dir, SCENE_ORG_FILE).resolve("extracted/mod.tune"), resolved);
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
        assertEquals(resolved, resolver(dir).resolve(ENTRY));
    }

    @Test
    void unwrapsGzippedModulesInsideArchives(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("MOD.tune.gz", TestArchives.gzip(TestModules.proTracker()))),
                Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(downloaded(dir, SCENE_ORG_FILE).resolve("extracted/MOD.tune"), resolved);
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
    }

    @Test
    void unwrapsAGzippedDirectDownload(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestArchives.gzip(TestModules.proTracker()), Optional.of("XM.survival.gz"));

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(downloaded(dir, MODLAND_FILE).resolve("extracted/XM.survival"), resolved);
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
    }

    @Test
    void unwrapsAPackedDirectDownload(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestArchives.xpk(TestModules.proTracker(), "NUKE", 1000), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(downloaded(dir, MODLAND_FILE).resolve("extracted/funkyeeh.mod"), resolved);
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
        entries.put("theseus.mod", TestModules.proTrackerSwappingSamples());
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
    void unwrapsTheModuleInsideAnUnrealPackage(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final byte[] packaged = TestArchives.umx(TestModules.proTracker());
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("music.umx", packaged)), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals("music.mod", resolved.getFileName().toString());
        assertArrayEquals(TestModules.proTracker(), Files.readAllBytes(resolved));
    }

    @Test
    void unpacksTheTapeImageInsideAPartyFile(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        final byte[] tape = TestArchives.t64(Map.of("THESEUS", TestSids.program()));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("compo.t64", tape)), Optional.empty());

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
    void movesOnToTheNextDownloadWhenTheFirstHoldsATapeThatCannotBePlayed(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW, "ModlandFile", MODLAND_FILE));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("silent.tap", silentTape())), Optional.empty());
        http.put(MODLAND_FILE, TestModules.proTracker(), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals("funkyeeh.mod", resolved.getFileName().toString(), "the module comes before the silent tape");
    }

    @Test
    void offersATapeThatCannotBePlayedWhereNothingElseIs(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW));
        http.put(SCENE_ORG_FILE, TestArchives.zip(Map.of("silent.tap", silentTape())), Optional.empty());

        final Path resolved = resolver(dir).resolve(ENTRY);

        assertEquals(downloaded(dir, SCENE_ORG_FILE).resolve("extracted/silent.tap"), resolved,
                "playing it tells why it cannot be played");
    }

    private static byte[] silentTape() {
        return TestTaps.tape(TestTaps.codePart("x", TestTaps.ENTRY, TestTaps.WAIT_FOREVER));
    }

    @Test
    void failsClearlyOnUnknownDownloads(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("SceneOrgFile", SCENE_ORG_VIEW.replace("funkyeeh.zip", "page.html")));
        http.put(SCENE_ORG_FILE.replace("funkyeeh.zip", "page.html"), "<html>".getBytes(StandardCharsets.US_ASCII), Optional.empty());
        final IOException error = assertThrows(IOException.class, () -> resolver(dir).resolve(ENTRY));
        assertEquals("page.html for Funkyeeh is not a module or archive", error.getMessage());
    }

    @Test
    void resolvesAModuleByItsModArchiveId(@TempDir Path dir) throws IOException {
        http.put(MODARCHIVE_FILE, TestModules.proTracker(), Optional.of("gauged.mod"));

        final Path resolved = resolver(dir).resolve(123, "Gauged", "gauged.mod");

        assertEquals(downloaded(dir, MODARCHIVE_FILE).resolve("gauged.mod"), resolved);
        assertEquals(resolved, resolver(dir).resolve(123, "Gauged", "gauged.mod"), "remembered");
        assertEquals(1, http.requests(), "no Demozoo lookup and no second download");
    }

    @Test
    void keepsAModuleApartFromAProductionOfTheSameNumber(@TempDir Path dir) throws IOException {
        http.put(PRODUCTION_URL, productionJson("ModlandFile", MODLAND_FILE));
        http.put(MODLAND_FILE, TestModules.proTracker(), Optional.empty());
        http.put(MODARCHIVE_FILE.replace("123", "7"), TestModules.digiBooster(), Optional.of("seven.dbm"));

        final Path production = resolver(dir).resolve(ENTRY);
        final Path module = resolver(dir).resolve(7, "Seven", "seven.dbm");

        assertEquals("funkyeeh.mod", production.getFileName().toString());
        assertEquals("seven.dbm", module.getFileName().toString());
    }

    @Test
    void picksTheFileNamedLikeTheModuleFromAnArchive(@TempDir Path dir) throws IOException {
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("aaa.mod", TestModules.proTracker());
        entries.put("gauged.mod", TestModules.proTracker());
        http.put(MODARCHIVE_FILE, TestArchives.zip(entries), Optional.of("gauged.zip"));

        assertEquals("gauged.mod", resolver(dir).resolve(123, "Something Else", "gauged.mod").getFileName().toString());
    }

    private static Path downloaded(Path dir, String url) {
        return new DownloadCache(new CacheDirectory(dir)).directory(URI.create(url));
    }

    private static Production production(List<Link> downloads, List<Link> externals) {
        return new Production(7, "Funkyeeh", downloads, externals, null);
    }

    private static String productionJson(String... classesAndUrls) {
        return productionJson(7, classesAndUrls);
    }

    private static String productionJson(int id, String... classesAndUrls) {
        final StringBuilder links = new StringBuilder();
        for (int at = 0; at < classesAndUrls.length; at += 2) {
            links.append(links.isEmpty() ? "" : ",")
                    .append("{\"link_class\":\"").append(classesAndUrls[at]).append("\",\"url\":\"").append(classesAndUrls[at + 1]).append("\"}");
        }
        return "{\"id\":" + id + ",\"title\":\"Funkyeeh\",\"download_links\":[" + links + "],\"external_links\":[]}";
    }

}

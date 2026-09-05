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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachedReleaseArtTest {

    private static final int PRODUCTION = 7;
    private static final String RELEASE_URL = "https://archive.scene.org/pub/parties/1995/assembly95/m4ch/funkyeeh.zip";
    private static final Charset CODE_PAGE = StandardCharsets.ISO_8859_1;
    private static final String BANNER = "\n.------------------.\n|  I.C.I.N.G 9.7   |\n`------------------'\n\n\n";

    private static CachedReleaseArt art(Path dir) {
        return new CachedReleaseArt(new CacheDirectory(dir));
    }

    private static Path release(Path dir) throws IOException {
        final DownloadCache downloads = new DownloadCache(new CacheDirectory(dir));
        final Path directory = downloads.directory(URI.create(RELEASE_URL));
        downloads.remember(PRODUCTION, directory);
        return Files.createDirectories(directory.resolve("extracted"));
    }

    @Test
    void readsTheArtOutOfTheFilesTheReleaseCameWith(@TempDir Path dir) throws IOException {
        Files.write(release(dir).resolve("file_id.diz"), BANNER.getBytes(CODE_PAGE));

        final List<String> art = art(dir).of(PRODUCTION).orElseThrow();

        assertEquals(List.of(".------------------.", "|  I.C.I.N.G 9.7   |", "`------------------'"), art);
    }

    @Test
    void readsBoxDrawnArtInTheCodePageOfThePc(@TempDir Path dir) throws IOException {
        Files.write(release(dir).resolve("party.nfo"), new byte[] {(byte) 0xC9, (byte) 0xCD, (byte) 0xBB, '\n', (byte) 0xC8, (byte) 0xCD, (byte) 0xBC});

        final List<String> art = art(dir).of(PRODUCTION).orElseThrow();

        assertEquals(List.of("╔═╗", "╚═╝"), art);
    }

    @Test
    void readsArtWithoutBoxesAsTheAmigaWroteIt(@TempDir Path dir) throws IOException {
        Files.write(release(dir).resolve("party.nfo"), new byte[] {'P', (byte) 0xF6, 's', 'e', '\n', 'N', 'o', 't', 'e'});

        final List<String> art = art(dir).of(PRODUCTION).orElseThrow();

        assertEquals(List.of("Pöse", "Note"), art);
    }

    @Test
    void prefersTheFileIdOverTheRest(@TempDir Path dir) throws IOException {
        final Path release = release(dir);
        Files.write(release.resolve("aaa.nfo"), "one\ntwo".getBytes(CODE_PAGE));
        Files.write(release.resolve("file_id.diz"), BANNER.getBytes(CODE_PAGE));

        final List<String> art = art(dir).of(PRODUCTION).orElseThrow();

        assertTrue(art.getFirst().startsWith("."), art.getFirst());
    }

    @Test
    void leavesAloneWhatItCannotShowAsPlainText(@TempDir Path dir) throws IOException {
        Files.write(release(dir).resolve("bbs.nfo"), "\033[15C\033[32mBROADWAY BBS\n\033[0mline two".getBytes(CODE_PAGE));

        assertEquals(Optional.empty(), art(dir).of(PRODUCTION),
                "art shaped by cursor moves cannot be a block of text");
    }

    @Test
    void hasNothingForReleasesThatCameWithout(@TempDir Path dir) throws IOException {
        Files.write(release(dir).resolve("tune.mod"), "not art".getBytes(CODE_PAGE));

        assertEquals(Optional.empty(), art(dir).of(PRODUCTION));
        assertEquals(Optional.empty(), art(dir).of(PRODUCTION + 1), "never played");
    }

    @Test
    void looksAgainAtAReleaseThatIsStillBeingDownloaded(@TempDir Path dir) throws IOException {
        final CachedReleaseArt art = new CachedReleaseArt(new CacheDirectory(dir), Duration.ZERO, Clock.systemUTC());
        release(dir);

        assertEquals(Optional.empty(), art.of(PRODUCTION), "nothing has landed yet");
        Files.write(release(dir).resolve("file_id.diz"), BANNER.getBytes(CODE_PAGE));

        assertTrue(art.of(PRODUCTION).isPresent(), "the art that arrived is picked up");
    }

    @Test
    void skipsAOneLineNote(@TempDir Path dir) throws IOException {
        Files.write(release(dir).resolve("file_id.diz"), "just a note\n".getBytes(CODE_PAGE));

        assertEquals(Optional.empty(), art(dir).of(PRODUCTION));
    }
}

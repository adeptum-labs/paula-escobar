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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileCatalogueTest {

    private static final int RELEASER_ID = 5887;
    private static final int PARTY_ID = 103;

    @Test
    void aWrittenCatalogueReadsBackEqual(@TempDir Path dir) throws IOException {
        final MusicCatalogue catalogue = catalogue();
        FileCatalogue.write(catalogue, dir);
        assertEquals(catalogue, FileCatalogue.catalogue(dir.resolve(CatalogueFormat.fileName())).catalogue());
    }

    @Test
    void writeAnswersBothFilesAndTheChecksumNamesTheCatalogueWithItsSha256(@TempDir Path dir) throws IOException {
        final Path directory = dir.resolve("catalogue");
        final List<Path> written = FileCatalogue.write(catalogue(), directory);
        assertEquals(List.of(directory.resolve(CatalogueFormat.fileName()),
                directory.resolve(CatalogueFormat.checksumName())), written);

        final byte[] bytes = Files.readAllBytes(directory.resolve(CatalogueFormat.fileName()));
        final String checksum = Files.readString(directory.resolve(CatalogueFormat.checksumName()), StandardCharsets.US_ASCII);
        assertEquals(Checksums.line(Checksums.sha256(bytes), CatalogueFormat.fileName()), checksum);
        assertTrue(checksum.matches("[0-9a-f]{64}  demozoo-catalogue-v1\\.bin\\.gz\n"));
    }

    @Test
    void aMissingFileIsAnIoException(@TempDir Path dir) {
        final FileCatalogue missing = FileCatalogue.catalogue(dir.resolve("nothing-here.bin.gz"));
        assertThrows(IOException.class, missing::catalogue);
    }

    private static MusicCatalogue catalogue() {
        final Nick musician = new Nick("UnreaL", RELEASER_ID, false);
        final CompoEntry entry = new CompoEntry(1, "1st", 7, "Funkyeeh", List.of(musician), Set.of(13));
        final PartySeries assembly = new PartySeries(2, "Assembly",
                List.of(new Party(PARTY_ID, "Assembly 1993", "1993-08-01")));
        final Competition competition = new Competition(9, "4 Channel Music", 13, "Music", List.of(entry));
        final Production production = new Production(7, "Funkyeeh",
                List.of(new Link("SceneOrgFile", "https://files.scene.org/funky.mod")), List.of(),
                "https://demozoo.org/media/7.png");

        return new MusicCatalogue(Map.of(2, assembly), Map.of(PARTY_ID, List.of(competition)),
                Map.of(PARTY_ID, "assembly/1993"), Map.of(7, production),
                Map.of(RELEASER_ID, List.of(new Work(entry, "1993", "Amiga OCS/ECS"))));
    }
}

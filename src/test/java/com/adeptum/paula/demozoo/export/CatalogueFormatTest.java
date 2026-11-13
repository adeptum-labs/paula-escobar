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

import com.adeptum.paula.demozoo.Competition;
import com.adeptum.paula.demozoo.CompoEntry;
import com.adeptum.paula.demozoo.Link;
import com.adeptum.paula.demozoo.Nick;
import com.adeptum.paula.demozoo.Party;
import com.adeptum.paula.demozoo.PartySeries;
import com.adeptum.paula.demozoo.Production;
import com.adeptum.paula.demozoo.Work;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class CatalogueFormatTest {

    private static final int RELEASER_ID = 5887;
    private static final int PARTY_ID = 103;

    @Test
    void roundTripKeepsEveryField() throws IOException {
        final MusicCatalogue catalogue = catalogue();
        assertEquals(catalogue, CatalogueFormat.read(new ByteArrayInputStream(encoded(catalogue))));
    }

    @Test
    void wrongMagicIsRejected() throws IOException {
        final byte[] file = gzip(0, CatalogueFormat.VERSION, data -> { });
        assertThrows(IOException.class, () -> CatalogueFormat.read(new ByteArrayInputStream(file)));
    }

    @Test
    void unknownVersionIsRejected() throws IOException {
        final byte[] file = gzip(CatalogueFormat.MAGIC, 2, data -> { });
        final IOException thrown = assertThrows(IOException.class,
                () -> CatalogueFormat.read(new ByteArrayInputStream(file)));
        assertEquals("unsupported catalogue version 2", thrown.getMessage());
    }

    @Test
    void truncatedFileIsRejected() throws IOException {
        final byte[] file = encoded(catalogue());
        final byte[] truncated = Arrays.copyOf(file, file.length / 2);
        assertThrows(IOException.class, () -> CatalogueFormat.read(new ByteArrayInputStream(truncated)));
    }

    @Test
    void outOfRangeStringIndexIsRejected() throws IOException {
        final byte[] file = gzip(CatalogueFormat.MAGIC, CatalogueFormat.VERSION, data -> {
            final byte[] value = "a".getBytes(StandardCharsets.UTF_8);
            data.writeInt(1);
            data.writeInt(value.length);
            data.write(value);
            data.writeInt(0);
            data.writeInt(1);
            data.writeInt(PARTY_ID);
            data.writeInt(7);
        });
        assertThrows(IOException.class, () -> CatalogueFormat.read(new ByteArrayInputStream(file)));
    }

    private static MusicCatalogue catalogue() {
        final Nick musician = new Nick("UnreaL", RELEASER_ID, false);
        final Nick group = new Nick("Spaceballs", 42, true);
        final CompoEntry entry = new CompoEntry(1, "1st", 7, "Funkyeeh", List.of(musician, group), Set.of(13, 31));

        final PartySeries assembly = new PartySeries(2, "Assembly",
                List.of(new Party(PARTY_ID, "Assembly 1993", "1993-08-01"), new Party(104, "Assembly 1994", "")));
        final Competition competition = new Competition(9, "4 Channel Music", 13, "Music", List.of(entry));

        final Production pictured = new Production(7, "Funkyeeh",
                List.of(new Link("SceneOrgFile", "https://files.scene.org/funky.mod")),
                List.of(new Link("PouetProduction", "https://www.pouet.net/prod.php?which=1")),
                "https://demozoo.org/media/7.png");
        final Production unpictured = new Production(8, "Quiet Tune", List.of(), List.of(), null);

        return new MusicCatalogue(Map.of(2, assembly), Map.of(PARTY_ID, List.of(competition)),
                Map.of(PARTY_ID, "assembly/1993"), Map.of(7, pictured, 8, unpictured),
                Map.of(RELEASER_ID, List.of(new Work(entry, "1993", "Amiga OCS/ECS"))));
    }

    private static byte[] encoded(MusicCatalogue catalogue) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CatalogueFormat.write(catalogue, bytes);
        return bytes.toByteArray();
    }

    private static byte[] gzip(int magic, int version, Body body) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(bytes))) {
            data.writeInt(magic);
            data.writeShort(version);
            body.write(data);
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface Body {
        void write(DataOutputStream data) throws IOException;
    }
}

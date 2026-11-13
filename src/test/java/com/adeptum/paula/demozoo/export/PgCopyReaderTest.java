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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class PgCopyReaderTest {

    static final String PRODUCTION = "SET statement_timeout = 0;\n"
            + "COPY public.productions_production (id, \"position\", name, note) FROM stdin;\n"
            + "7\t2\tTab\\tthere\t\\N\n"
            + "8\t\\N\tLine\\nbreak\t\\\\\n"
            + "\\.\n"
            + "ANALYZE public.productions_production;\n";

    static final String TWO_TABLES = """
            SET client_encoding = 'UTF8';
            COPY public.other (id) FROM stdin;
            99
            \\.
            COPY public.wanted (id) FROM stdin;
            7
            \\.
            ANALYZE public.wanted;
            """;

    @Test
    void readsEscapesAndNullsOfATableItKnows() throws IOException {
        final List<String> rows = new ArrayList<>();

        PgCopyReader.read(gzipped(PRODUCTION), Map.of("productions_production", row -> rows.add(
                row.integer("id") + "|" + row.integerOrNull("position") + "|" + row.text("name") + "|"
                        + row.text("note"))));

        assertEquals(List.of("7|2|Tab\tthere|null", "8|null|Line\nbreak|\\"), rows);
    }

    @Test
    void readsFlags() throws IOException {
        final List<Boolean> flags = new ArrayList<>();

        PgCopyReader.read(gzipped("COPY public.t (is_playable) FROM stdin;\nt\nf\n\\.\n"),
                Map.of("t", row -> flags.add(row.flag("is_playable"))));

        assertEquals(List.of(true, false), flags);
    }

    @Test
    void skipsSqlAndTablesItWasNotAskedFor() throws IOException {
        final List<Integer> ids = new ArrayList<>();

        PgCopyReader.read(gzipped(TWO_TABLES), Map.of("wanted", row -> ids.add(row.integer("id"))));

        assertEquals(List.of(7), ids);
    }

    @Test
    void failsWhenARequestedTableNeverAppears() throws IOException {
        final IOException error = assertThrows(IOException.class, () -> PgCopyReader.read(
                gzipped("COPY public.other (id) FROM stdin;\n1\n\\.\n"), Map.of("wanted", row -> { })));

        assertEquals("Demozoo export has no table wanted", error.getMessage());
    }

    @Test
    void failsOnAColumnTheTableDoesNotHave() throws IOException {
        final IOException error = assertThrows(IOException.class, () -> PgCopyReader.read(
                gzipped("COPY public.t (id) FROM stdin;\n1\n\\.\n"), Map.of("t", row -> row.text("title"))));

        assertEquals("Demozoo export table t has no column title", error.getMessage());
    }

    @Test
    void failsOnValuesThatAreNotIntegers() throws IOException {
        final IOException unparsable = assertThrows(IOException.class, () -> PgCopyReader.read(
                gzipped("COPY public.t (id) FROM stdin;\nnot a number\n\\.\n"),
                Map.of("t", row -> row.integer("id"))));
        final IOException empty = assertThrows(IOException.class, () -> PgCopyReader.read(
                gzipped("COPY public.t (id) FROM stdin;\n\\N\n\\.\n"), Map.of("t", row -> row.integer("id"))));

        assertEquals("Demozoo export table t has no integer in column id for value not a number",
                unparsable.getMessage());
        assertEquals("Demozoo export table t has no integer in column id for value NULL", empty.getMessage());
    }

    @Test
    void failsWhenABlockIsNotClosed() throws IOException {
        assertThrows(IOException.class, () -> PgCopyReader.read(
                gzipped("COPY public.t (id) FROM stdin;\n1\n"), Map.of("t", row -> { })));
    }

    private static InputStream gzipped(String text) throws IOException {
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(compressed)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(compressed.toByteArray());
    }
}

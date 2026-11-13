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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Streams the tables of Demozoo's nightly pg_dump export, handing the rows of the wanted tables to their handlers
 * while every other table and every SQL statement in between is passed over.
 */
public final class PgCopyReader {

    private static final int BUFFER_BYTES = 1 << 20;
    private static final String COPY = "COPY ";
    private static final String FROM_STDIN = " FROM stdin;";
    private static final String END_OF_BLOCK = "\\.";

    private PgCopyReader() {
    }

    /**
     * Reads the gzipped export once from beginning to end. A table named in {@code handlers} gets each of its rows,
     * and the bare table name is the key, so {@code productions_production} matches
     * {@code COPY public.productions_production (...)}.
     */
    public static void read(InputStream gzipped, Map<String, RowHandler> handlers) throws IOException {
        final Set<String> seen = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(gzipped, BUFFER_BYTES), StandardCharsets.UTF_8), BUFFER_BYTES)) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!isCopyStatement(line)) {
                    continue;
                }
                final String table = tableName(line);
                final RowHandler handler = handlers.get(table);
                if (handler == null) {
                    skipBlock(reader, table);
                } else {
                    seen.add(table);
                    readBlock(reader, table, columnIndexes(line), handler);
                }
            }
        }
        for (final String table : handlers.keySet()) {
            if (!seen.contains(table)) {
                throw new IOException("Demozoo export has no table " + table);
            }
        }
    }

    @FunctionalInterface
    public interface RowHandler {
        void accept(Row row) throws IOException;
    }

    /**
     * One row of a table. The reader fills a single instance for every row of a block, so a handler that needs a
     * value after it returns must copy it out first.
     */
    public static final class Row {

        private final String table;
        private final Map<String, Integer> columns;
        private final List<String> values = new ArrayList<>();

        private Row(String table, Map<String, Integer> columns) {
            this.table = table;
            this.columns = columns;
        }

        /**
         * The field as text, or {@code null} where the export holds SQL NULL; a column missing from the table is an
         * error, not an absence.
         */
        public String text(String column) throws IOException {
            return values.get(index(column));
        }

        public int integer(String column) throws IOException {
            final String value = text(column);
            if (value == null) {
                throw notAnInteger(column, null);
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw notAnInteger(column, value);
            }
        }

        public Integer integerOrNull(String column) throws IOException {
            final String value = text(column);
            return value == null ? null : integer(column);
        }

        public boolean flag(String column) throws IOException {
            final String value = text(column);
            if ("t".equals(value)) {
                return true;
            }
            if ("f".equals(value)) {
                return false;
            }
            throw new IOException("Demozoo export table " + table + " has no flag in column " + column
                    + " for value " + valueText(value));
        }

        private void load(String line) throws IOException {
            parseFields(line, values);
        }

        private int index(String column) throws IOException {
            final Integer index = columns.get(column);
            if (index == null) {
                throw new IOException("Demozoo export table " + table + " has no column " + column);
            }
            return index;
        }

        private IOException notAnInteger(String column, String value) {
            return new IOException("Demozoo export table " + table + " has no integer in column " + column
                    + " for value " + valueText(value));
        }

        private static String valueText(String value) {
            return value == null ? "NULL" : value;
        }
    }

    private static boolean isCopyStatement(String line) {
        return line.startsWith(COPY) && line.endsWith(FROM_STDIN);
    }

    private static String tableName(String copyStatement) {
        final int columns = copyStatement.indexOf('(');
        final int end = columns < 0 ? copyStatement.indexOf(FROM_STDIN) : columns;
        final String qualified = copyStatement.substring(COPY.length(), end).strip();
        final int schema = qualified.lastIndexOf('.');
        return schema < 0 ? qualified : qualified.substring(schema + 1);
    }

    private static void readBlock(BufferedReader reader, String table, Map<String, Integer> columns,
            RowHandler handler) throws IOException {
        final Row row = new Row(table, columns);
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.equals(END_OF_BLOCK)) {
                return;
            }
            row.load(line);
            handler.accept(row);
        }
        throw unterminated(table);
    }

    private static void skipBlock(BufferedReader reader, String table) throws IOException {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.equals(END_OF_BLOCK)) {
                return;
            }
        }
        throw unterminated(table);
    }

    private static IOException unterminated(String table) {
        return new IOException("Demozoo export block for table " + table + " is not closed by " + END_OF_BLOCK);
    }

    /**
     * The column list of a COPY statement, read once per table; names may be quoted, as {@code "position"} is.
     */
    private static Map<String, Integer> columnIndexes(String copyStatement) throws IOException {
        final int open = copyStatement.indexOf('(');
        final int close = copyStatement.lastIndexOf(')');
        if (open < 0 || close < open) {
            throw new IOException("Demozoo export has a COPY statement without columns: " + copyStatement);
        }
        final Map<String, Integer> columns = new HashMap<>();
        final StringBuilder name = new StringBuilder();
        boolean quoted = false;
        int index = 0;
        for (int at = open + 1; at <= close; at++) {
            final char character = at == close ? ',' : copyStatement.charAt(at);
            if (character == '"' && quoted && at + 1 < copyStatement.length()
                    && copyStatement.charAt(at + 1) == '"') {
                name.append('"');
                at++;
            } else if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                columns.put(name.toString().strip(), index);
                index++;
                name.setLength(0);
            } else {
                name.append(character);
            }
        }
        return columns;
    }

    /**
     * Splits a data line into its fields and unescapes them in one pass; the export holds millions of rows, so a
     * regex or a split per row would cost more than the walk itself.
     */
    private static void parseFields(String line, List<String> fields) throws IOException {
        fields.clear();
        final StringBuilder value = new StringBuilder();
        boolean escaped = false;
        int start = 0;
        for (int at = 0; at < line.length(); at++) {
            final char character = line.charAt(at);
            if (escaped) {
                value.append(unescape(character));
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '\t') {
                fields.add(isNull(line, start, at) ? null : value.toString());
                value.setLength(0);
                start = at + 1;
            } else {
                value.append(character);
            }
        }
        fields.add(isNull(line, start, line.length()) ? null : value.toString());
    }

    private static boolean isNull(String line, int start, int end) {
        return end - start == 2 && line.charAt(start) == '\\' && line.charAt(start + 1) == 'N';
    }

    private static char unescape(char escape) throws IOException {
        return switch (escape) {
            case '\\' -> '\\';
            case 't' -> '\t';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'v' -> (char) 0x0B;
            // Only ever a whole field, which isNull turns into a null once the field ends.
            case 'N' -> 'N';
            default -> throw new IOException("Demozoo export has an unknown escape \\" + escape);
        };
    }
}

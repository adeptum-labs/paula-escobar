/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

package com.adeptum.paula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

class PaulaTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final CommandLine cli = Paula.commandLine()
            .setOut(new PrintWriter(out))
            .setErr(new PrintWriter(err))
            .setColorScheme(CommandLine.Help.defaultColorScheme(Ansi.OFF));

    @Test
    void noArgumentsPrintsUsage() {
        assertEquals(0, cli.execute());
        assertTrue(out.toString().startsWith("Usage: paula"));
    }

    @Test
    void versionIsReadFromBuildProperties() {
        assertEquals(0, cli.execute("--version"));
        assertTrue(out.toString().startsWith("paula 0."));
    }

    @Test
    void formatsListsProTracker() {
        assertEquals(0, cli.execute("formats"));
        assertTrue(out.toString().contains("ProTracker"));
        assertTrue(out.toString().contains(".mod"));
    }

    @Test
    void infoPrintsModuleMetadata(@TempDir Path dir) throws Exception {
        final Path file = dir.resolve("tune.mod");
        final byte[] data = new byte[1084];
        System.arraycopy("Hello".getBytes(StandardCharsets.ISO_8859_1), 0, data, 0, 5);
        System.arraycopy("M.K.".getBytes(StandardCharsets.ISO_8859_1), 0, data, 1080, 4);
        Files.write(file, data);

        assertEquals(0, cli.execute("info", file.toString()));
        assertTrue(out.toString().startsWith("Hello"));
        assertTrue(out.toString().contains("Channels:4"));
    }

    @Test
    void infoOnUnsupportedFileFails() {
        assertEquals(1, cli.execute("info", "missing.sid"));
        assertEquals("Error: Unsupported module format: missing.sid", err.toString().strip());
    }
}

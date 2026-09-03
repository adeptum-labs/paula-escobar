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

package com.adeptum.paula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.testing.TestModules;
import com.adeptum.paula.testing.TestSids;
import java.io.PrintWriter;
import java.io.StringWriter;
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
    void formatsListsTrackerModules() {
        assertEquals(0, cli.execute("formats"));
        assertTrue(out.toString().contains("Tracker modules"));
        assertTrue(out.toString().contains(".mod"));
        assertTrue(out.toString().contains(".xm"));
    }

    @Test
    void infoPrintsModuleMetadata(@TempDir Path dir) throws Exception {
        assertEquals(0, cli.execute("info", TestModules.writeProTracker(dir).toString()));
        assertTrue(out.toString().startsWith(TestModules.TITLE));
        assertTrue(out.toString().contains("Channels:4"));
        assertTrue(out.toString().contains(TestModules.SAMPLE_NAME));
    }

    @Test
    void formatsListsSidTunes() {
        assertEquals(0, cli.execute("formats"));
        assertTrue(out.toString().contains("SID"));
        assertTrue(out.toString().contains(".sid"));
    }

    @Test
    void infoPrintsSidCredits(@TempDir Path dir) throws Exception {
        assertEquals(0, cli.execute("info", TestSids.writePsid(dir).toString()));
        assertTrue(out.toString().startsWith(TestSids.NAME));
        assertTrue(out.toString().contains(TestSids.AUTHOR));
        assertTrue(out.toString().contains(TestSids.RELEASED));
        assertTrue(out.toString().contains("Length:  2 subtunes"));
    }

    @Test
    void infoOnUnsupportedFileFails() {
        assertEquals(1, cli.execute("info", "missing.xyz"));
        assertEquals("Error: Unsupported module format: missing.xyz", err.toString().strip());
    }
}

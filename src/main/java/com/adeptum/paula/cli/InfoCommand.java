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

package com.adeptum.paula.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Help.ColorScheme;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import com.adeptum.paula.module.ModuleLoaderRegistry;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.sid.SongLengths;

@Command(name = "info", description = "Show metadata for a module file.")
public final class InfoCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Parameters(paramLabel = "FILE", description = "Module file to inspect.")
    private Path file;

    @Override
    public void run() {
        try {
            print(ModuleLoaderRegistry.withBuiltInLoaders(SongLengths.none()).load(file).metadata());
        } catch (IOException e) {
            throw new ExecutionException(spec.commandLine(), e.getMessage(), e);
        }
    }

    private void print(ModuleMetadata meta) {
        final PrintWriter out = spec.commandLine().getOut();
        final ColorScheme colors = spec.commandLine().getColorScheme();
        out.println(colors.text("@|bold,fg(cyan) " + meta.displayTitle() + "|@"));
        out.println(colors.text("@|faint Format:  |@" + meta.format().name()));
        out.println(colors.text("@|faint Channels:|@" + meta.channels()));
        out.println(colors.text("@|faint Length:  |@" + meta.songLength() + " positions"));
        meta.credits().forEach(out::println);
        for (int i = 0; i < meta.instruments().size(); i++) {
            out.println(colors.text(String.format("@|faint %02d|@ %s", i + 1, meta.instruments().get(i))));
        }
    }
}

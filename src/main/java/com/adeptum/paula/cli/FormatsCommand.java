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

import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoaderRegistry;

@Command(name = "formats", description = "List the supported module formats.")
public final class FormatsCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        ModuleLoaderRegistry.withBuiltInLoaders().formats().forEach(this::print);
    }

    private void print(ModuleFormat format) {
        final String extensions = format.extensions().stream().map(e -> "." + e).collect(Collectors.joining(", "));
        spec.commandLine().getOut().println(spec.commandLine().getColorScheme()
                .text(String.format("@|bold,fg(cyan) %-6s|@ %-28s @|faint %s|@", format.id(), format.name(), extensions)));
    }
}

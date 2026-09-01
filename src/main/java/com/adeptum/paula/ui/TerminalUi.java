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

package com.adeptum.paula.ui;

import java.io.IOException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp.Capability;

/**
 * Full-screen JLine front end: raw mode, alternate screen and non-blocking key polling.
 */
public final class TerminalUi implements AutoCloseable {

    private final Terminal terminal;
    private final Display display;

    public TerminalUi() throws IOException {
        terminal = TerminalBuilder.builder().system(true).build();
        display = new Display(terminal, true);
        terminal.enterRawMode();
        terminal.puts(Capability.enter_ca_mode);
        terminal.puts(Capability.cursor_invisible);
        terminal.flush();
    }

    public Action poll(long timeoutMillis) throws IOException {
        return Action.forKey(terminal.reader().read(timeoutMillis));
    }

    public void draw(PlayerView view) {
        display.resize(terminal.getHeight(), terminal.getWidth());
        display.update(Screen.render(view, terminal.getWidth()), 0);
    }

    @Override
    public void close() throws IOException {
        terminal.puts(Capability.cursor_visible);
        terminal.puts(Capability.exit_ca_mode);
        terminal.flush();
        terminal.close();
    }
}

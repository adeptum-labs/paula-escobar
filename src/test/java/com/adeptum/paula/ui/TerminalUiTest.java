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

package com.adeptum.paula.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

class TerminalUiTest {

    @Test
    void aKeyboardlessTerminalWaitsOutEveryPollAndHasAScreenToDrawOn() throws IOException {
        final Terminal terminal = new DumbTerminal("test", Terminal.TYPE_DUMB,
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), StandardCharsets.UTF_8);

        try (TerminalUi ui = new TerminalUi(terminal)) {
            assertEquals(Key.of(Key.Special.TIMEOUT), ui.poll(1));
            assertEquals(80, ui.width());
            assertEquals(24, ui.height());
        }
    }

    /**
     * JLine writes only what differs from the screen it believes is there, and a resize leaves that belief
     * wrong, so a screen whose text has not changed has to be drawn again all the same.
     */
    @Test
    void drawsTheWholeScreenAgainAfterTheTerminalIsResized() throws IOException {
        final ByteArrayOutputStream written = new ByteArrayOutputStream();

        try (Terminal terminal = new ExternalTerminal("test", "xterm-256color",
                new ByteArrayInputStream(new byte[0]), written, StandardCharsets.UTF_8)) {
            terminal.setSize(new Size(80, 30));
            final TerminalUi ui = new TerminalUi(terminal);
            ui.draw(filled(80, 30));

            terminal.setSize(new Size(60, 20));
            final int beforeResize = written.size();
            ui.draw(filled(60, 20));

            assertTrue(written.size() > beforeResize + 60 * 20,
                    "every cell is written out again at the new size");
        }
    }

    /**
     * A screen of full-width lines, as the player draws, which is where JLine's reflow of the old screen lines
     * up with the new one and it writes nothing at all.
     */
    private static List<AttributedString> filled(int width, int height) {
        return Collections.nCopies(height, new AttributedString("x".repeat(width)));
    }
}

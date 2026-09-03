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

import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp.Capability;

/**
 * Full-screen JLine front end: raw mode, alternate screen and non-blocking key polling.
 */
public final class TerminalUi implements AutoCloseable {

    private static final int ESCAPE = 27;
    private static final char CSI = '[';
    private static final char SS3 = 'O';
    private static final char FIRST_FINAL_BYTE = 0x40;
    private static final char LAST_FINAL_BYTE = 0x7E;
    private static final long SEQUENCE_TIMEOUT_MILLIS = 50;

    private final Terminal terminal;
    private final Display display;

    public TerminalUi() throws IOException {
        terminal = TerminalBuilder.builder().system(true).dumb(false).build();
        display = new Display(terminal, true);
        terminal.enterRawMode();
        terminal.puts(Capability.enter_ca_mode);
        terminal.puts(Capability.cursor_invisible);
        terminal.flush();
    }

    public Key poll(long timeoutMillis) throws IOException {
        final int key = terminal.reader().read(timeoutMillis);
        return key == ESCAPE ? escapedKey() : Key.forByte(key);
    }

    /**
     * Nothing following the escape within the short window means the user pressed escape itself. A CSI sequence
     * carries parameter and intermediate bytes before its final byte, an SS3 sequence is exactly one byte long and
     * anything else is an alt chord the player does not use.
     */
    private Key escapedKey() throws IOException {
        final int introducer = terminal.reader().read(SEQUENCE_TIMEOUT_MILLIS);
        if (introducer < 0) {
            return Key.of(Key.Special.ESCAPE);
        }
        final StringBuilder sequence = new StringBuilder().append((char) introducer);
        if (introducer == SS3) {
            appendNext(sequence);
        } else if (introducer == CSI) {
            boolean more = appendNext(sequence);
            while (more && !isFinalByte(sequence.charAt(sequence.length() - 1))) {
                more = appendNext(sequence);
            }
        } else {
            return Key.NONE;
        }
        return Key.forEscapeSequence(sequence.toString());
    }

    private boolean appendNext(StringBuilder sequence) throws IOException {
        final int key = terminal.reader().read(SEQUENCE_TIMEOUT_MILLIS);
        if (key < 0) {
            return false;
        }
        sequence.append((char) key);
        return true;
    }

    private static boolean isFinalByte(char value) {
        return value >= FIRST_FINAL_BYTE && value <= LAST_FINAL_BYTE;
    }

    public int width() {
        return terminal.getWidth();
    }

    public int height() {
        return terminal.getHeight();
    }

    public void draw(List<AttributedString> lines) {
        display.resize(terminal.getHeight(), terminal.getWidth());
        display.update(lines, 0);
    }

    @Override
    public void close() throws IOException {
        terminal.puts(Capability.cursor_visible);
        terminal.puts(Capability.exit_ca_mode);
        terminal.flush();
        terminal.close();
    }
}

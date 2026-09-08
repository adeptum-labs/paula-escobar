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

import com.adeptum.paula.cast.CastDevice;
import com.adeptum.paula.ui.visual.Palette;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * The panel that offers the devices on the network to play on, laid over the player: this machine first, then
 * every device found, with a mark beside the one playing and a spinner while the network is being asked.
 */
public final class CastPopup {

    /**
     * What was chosen: this machine, a device, or another look at the network.
     */
    public sealed interface Choice permits Local, Device, Rescan {
    }

    public record Local() implements Choice {
    }

    public record Device(CastDevice device) implements Choice {
    }

    public record Rescan() implements Choice {
    }

    private static final String TITLE = "Cast to";
    private static final String LOCAL = "This machine";
    private static final String SCANNING = "Scanning the network";
    private static final String NOTHING = "No device has answered";
    private static final String KEYS = "↑↓ choose  enter play there  r look again  esc close";
    private static final String SPINNER = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏";
    private static final String PLAYING_MARK = "▶ ";
    private static final String NO_MARK = "  ";
    private static final int NARROWEST = 44;
    private static final int PADDING = 6;

    private boolean open;
    private int cursor;
    private int spin;
    private int left;
    private int top;
    private int shown;
    private List<CastDevice> devices = List.of();

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
    }

    public void close() {
        open = false;
    }

    /**
     * The player screen with the panel laid over its middle.
     */
    public List<AttributedString> draw(List<AttributedString> screen, List<CastDevice> found, boolean scanning,
            CastDevice castingTo, int width, int height) {
        devices = found;
        shown = found.size() + 1;
        cursor = Math.clamp(cursor, 0, shown - 1);
        final List<AttributedString> content = new ArrayList<>();
        content.add(item(LOCAL, castingTo == null, cursor == 0));
        for (int index = 0; index < found.size(); index++) {
            final CastDevice device = found.get(index);
            content.add(item(device.describe(), device.equals(castingTo), cursor == index + 1));
        }
        content.add(AttributedString.EMPTY);
        content.add(scanning ? spinner() : found.isEmpty() ? dim(NOTHING) : AttributedString.EMPTY);
        content.add(dim(KEYS));

        final int boxWidth = Math.min(width, Math.max(NARROWEST, widest(content) + PADDING));
        final int boxHeight = Math.min(height, content.size() + 2);
        left = Math.max(0, (width - boxWidth) / 2);
        top = Math.max(0, (height - boxHeight) / 2);
        return Frame.overlay(screen, Frame.box(TITLE, content, boxWidth, boxHeight), left, top, width);
    }

    /**
     * A key while the panel is up, which it takes for itself; the choice made with it, if one was.
     */
    public Optional<Choice> handle(Key key) {
        if (key.mouse() != null) {
            return clicked(key.mouse());
        }
        switch (key.special()) {
            case UP -> cursor = Math.max(0, cursor - 1);
            case DOWN -> cursor = Math.min(shown - 1, cursor + 1);
            case ENTER -> {
                return Optional.of(chosen(cursor));
            }
            case ESCAPE -> close();
            case NONE -> {
                if (key.is('r')) {
                    return Optional.of(new Rescan());
                }
                if (key.is('c') || key.is('q')) {
                    close();
                }
            }
            default -> { }
        }
        return Optional.empty();
    }

    private Optional<Choice> clicked(Mouse mouse) {
        final int row = mouse.row() - top - 1;
        if (mouse.column() < left || row < 0 || row >= shown) {
            close();
            return Optional.empty();
        }
        cursor = row;
        return Optional.of(chosen(row));
    }

    private Choice chosen(int index) {
        return index == 0 ? new Local() : new Device(devices.get(index - 1));
    }

    private AttributedString item(String label, boolean playing, boolean chosen) {
        final AttributedStringBuilder line = new AttributedStringBuilder();
        line.style(chosen ? Palette.SELECTED : AttributedStyle.DEFAULT);
        line.append(playing ? PLAYING_MARK : NO_MARK).append(label);
        return line.toAttributedString();
    }

    private AttributedString spinner() {
        spin = (spin + 1) % SPINNER.length();
        return new AttributedStringBuilder().style(Palette.DIMMED).append(SPINNER.charAt(spin)).append(' ')
                .append(SCANNING).append("…").toAttributedString();
    }

    private static AttributedString dim(String text) {
        return new AttributedStringBuilder().style(Palette.DIMMED).append(text).toAttributedString();
    }

    private static int widest(List<AttributedString> lines) {
        return lines.stream().mapToInt(AttributedString::columnLength).max().orElse(0);
    }
}

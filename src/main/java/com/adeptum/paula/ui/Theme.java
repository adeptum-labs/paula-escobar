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

import org.jline.utils.AttributedStyle;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.ColorScheme;

public final class Theme {

    public static final AttributedStyle TITLE = AttributedStyle.BOLD.foreground(AttributedStyle.CYAN);
    public static final AttributedStyle LABEL = AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK);
    public static final AttributedStyle VALUE = AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);
    public static final AttributedStyle ACCENT = AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA);
    public static final AttributedStyle KEY = AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW);
    public static final AttributedStyle STATUS = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
    public static final AttributedStyle DIMMED = LABEL;

    private Theme() {
    }

    public static ColorScheme helpColors() {
        return new ColorScheme.Builder(Ansi.AUTO)
                .commands(Ansi.Style.bold, Ansi.Style.fg_cyan)
                .options(Ansi.Style.fg_yellow)
                .parameters(Ansi.Style.fg_magenta)
                .optionParams(Ansi.Style.italic)
                .build();
    }
}

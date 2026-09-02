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

package com.adeptum.paula.ui.visual;

import org.jline.utils.AttributedStyle;

/**
 * The colours of the interface as 24-bit values; JLine rounds them for terminals with fewer colours.
 */
public final class Palette {

    public static final AttributedStyle TITLE_TEXT = AttributedStyle.BOLD.foreground(255, 255, 255);
    public static final AttributedStyle FOOTER = AttributedStyle.DEFAULT.foreground(200, 200, 210).background(40, 42, 54);
    public static final AttributedStyle FOOTER_KEY = AttributedStyle.BOLD.foreground(255, 200, 80).background(40, 42, 54);
    public static final AttributedStyle BORDER = AttributedStyle.DEFAULT.foreground(90, 100, 140);
    public static final AttributedStyle PANEL_TITLE = AttributedStyle.BOLD.foreground(140, 200, 255);
    public static final AttributedStyle LABEL = AttributedStyle.DEFAULT.foreground(130, 140, 170);
    public static final AttributedStyle VALUE = AttributedStyle.DEFAULT.foreground(230, 230, 240);
    public static final AttributedStyle ACCENT = AttributedStyle.BOLD.foreground(255, 120, 200);
    public static final AttributedStyle ACTIVE = AttributedStyle.BOLD.foreground(120, 255, 160);
    public static final AttributedStyle DIMMED = AttributedStyle.DEFAULT.foreground(100, 105, 125);
    public static final AttributedStyle SELECTED = AttributedStyle.BOLD.foreground(255, 255, 255).background(70, 60, 130);
    public static final AttributedStyle GOLD = AttributedStyle.BOLD.foreground(255, 215, 0);
    public static final AttributedStyle SILVER = AttributedStyle.BOLD.foreground(200, 205, 215);
    public static final AttributedStyle BRONZE = AttributedStyle.BOLD.foreground(205, 127, 50);
    public static final AttributedStyle PEAK = AttributedStyle.DEFAULT.foreground(255, 255, 255);
    public static final AttributedStyle SCOPE = AttributedStyle.DEFAULT.foreground(120, 230, 255);
    public static final AttributedStyle SCOPE_QUIET = AttributedStyle.DEFAULT.foreground(70, 90, 120);

    private static final int[][] TITLE_GRADIENT = {{120, 20, 140}, {40, 60, 190}, {20, 150, 190}};
    private static final int[][] LEVEL_GRADIENT = {{40, 140, 255}, {40, 220, 200}, {140, 240, 60}, {255, 220, 40}, {255, 60, 60}};

    private Palette() {
    }

    /**
     * The colour of a level bar at a height between 0 (bottom) and 1 (top).
     */
    public static AttributedStyle level(double height) {
        final int[] rgb = interpolate(LEVEL_GRADIENT, height);
        return AttributedStyle.DEFAULT.foreground(rgb[0], rgb[1], rgb[2]);
    }

    /**
     * The background of the title bar at a horizontal position between 0 and 1.
     */
    public static AttributedStyle titleBackground(double position) {
        final int[] rgb = interpolate(TITLE_GRADIENT, position);
        return TITLE_TEXT.background(rgb[0], rgb[1], rgb[2]);
    }

    private static int[] interpolate(int[][] stops, double position) {
        final double scaled = Math.clamp(position, 0, 1) * (stops.length - 1);
        final int from = Math.min((int) scaled, stops.length - 2);
        final double t = scaled - from;
        final int[] rgb = new int[3];
        for (int i = 0; i < 3; i++) {
            rgb[i] = (int) Math.round(stops[from][i] + (stops[from + 1][i] - stops[from][i]) * t);
        }
        return rgb;
    }
}

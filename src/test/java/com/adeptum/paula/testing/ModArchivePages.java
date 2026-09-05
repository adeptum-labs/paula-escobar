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

package com.adeptum.paula.testing;

import com.adeptum.paula.modarchive.Chart;
import com.adeptum.paula.modarchive.ChartEntry;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a chart listing page in the shape modarchive.org itself serves, so a parser test exercises the
 * same anchors, spans and pagination form the site's HTML actually carries, entities and all.
 */
public final class ModArchivePages {

    private static final Pattern LEADING_NUMBER = Pattern.compile("^([\\d,]+)");

    private ModArchivePages() {
    }

    public static byte[] page(Chart chart, int page, int lastPage, ChartEntry... entries) {
        final StringBuilder html = new StringBuilder("<html><body>").append(about(lastPage));
        for (final ChartEntry entry : entries) {
            html.append(entryBlock(chart, entry));
        }
        return html.append("</body></html>").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String about(int lastPage) {
        return "<div class=\"bgsegment2 top2\"><h2>About</h2><p>"
                + "<a href=\"index.php?request=x&amp;page=" + lastPage + "#mods\">Last</a></p></div>";
    }

    private static String entryBlock(Chart chart, ChartEntry entry) {
        final int moduleId = entry.moduleId();
        return "<div class=\"bgsegment2\"><table><tr>"
                + "<td width=\"80\" valign=\"top\">" + heading(chart, entry) + "</td>"
                + "<td width=\"400\" valign=\"top\">"
                + "<a class=\"chart-listing-title\" href=\"module.php?" + moduleId + "\">" + escape(entry.title()) + "</a>"
                + "<br><span class=\"chart-listing\">" + escape(entry.fileName()) + "</span>"
                + "</td>"
                + "<td valign=\"top\"><div class=\"paragraph\"><ul class='nolist'>"
                + "<li><a class=\"standard-link\" href=\"module.php?" + moduleId + "#favourites\">"
                + sentence(chart, entry) + "</a></li>"
                + "</ul></div></td>"
                + "</tr></table></div>";
    }

    private static String heading(Chart chart, ChartEntry entry) {
        return switch (chart) {
            case TOP_FAVOURITES -> "<h2 class=\"chart-listing-title\">" + leadingNumber(entry) + "</h2>";
            case MOST_DOWNLOADS -> "<h1 class=\"chart-listing-title\">#1</h1>";
            case FEATURED -> "";
        };
    }

    private static String sentence(Chart chart, ChartEntry entry) {
        return switch (chart) {
            case TOP_FAVOURITES -> "A favourite of " + leadingNumber(entry) + " members!";
            case MOST_DOWNLOADS -> "Downloaded " + leadingNumber(entry) + " times!";
            case FEATURED -> "Featured " + entry.measure();
        };
    }

    private static String leadingNumber(ChartEntry entry) {
        final Matcher matcher = LEADING_NUMBER.matcher(entry.measure());
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }
}

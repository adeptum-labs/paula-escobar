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

import com.adeptum.paula.modarchive.Artist;
import com.adeptum.paula.modarchive.Chart;
import com.adeptum.paula.modarchive.ChartEntry;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the pages of modarchive.org in the shape the site itself serves them, so a parser test exercises the
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

    /**
     * An artist's module list in the site's shape: a heading with the name and a table row per module, the
     * file in the link's title, the name in a span and the rating in another, with the pagination up top.
     */
    public static byte[] artistPage(Artist artist, int page, int lastPage, ChartEntry... entries) {
        final StringBuilder html = new StringBuilder("<html><body><h1>").append(escape(artist.name())).append("'s Modules</h1>")
                .append("<p><a class='pagination' href=\"?request=view_artist_modules&amp;query=").append(artist.memberId())
                .append("&amp;page=").append(lastPage).append("#mods\">").append(lastPage).append("</a></p><table>");
        for (final ChartEntry entry : entries) {
            html.append("<tr><td><span class=\"format-icon\">").append(entry.extension().toUpperCase()).append("</span></td>")
                    .append("<td><a class=\"module-listing\" href=\"module.php?").append(entry.moduleId())
                    .append("\" title=\"").append(escape(entry.fileName())).append("\">").append(escape(entry.fileName())).append("</a></td>")
                    .append("<td><span class=\"module-listing\">").append(escape(entry.title())).append("</span></td>")
                    .append("<td><span class='module-listing'>")
                    .append(entry.measure().isEmpty() ? "Unrated" : "Rated " + entry.measure()).append("</span></td></tr>");
        }
        return html.append("</table></body></html>").toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A module's own page, as far as the artists it names go: the registered ones in a list under their heading.
     */
    public static byte[] modulePage(Artist... artists) {
        final StringBuilder html = new StringBuilder("<html><body><h1>a module</h1>");
        if (artists.length > 0) {
            html.append("<h2><img src='rosette.png'> Registered Artist(s):</h2><ul class='nolist'>");
            for (final Artist artist : artists) {
                html.append("<li><img src=\"member.png\">&nbsp;<a class=\"standard-link\" href=\"member.php?").append(artist.memberId())
                        .append("\">").append(escape(artist.name())).append("</a> </li>");
            }
            html.append("</ul>");
        }
        return html.append("<h2>Ratings</h2></body></html>").toString().getBytes(StandardCharsets.UTF_8);
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

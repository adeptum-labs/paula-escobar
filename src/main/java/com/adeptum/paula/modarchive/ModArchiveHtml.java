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

package com.adeptum.paula.modarchive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the site's own pages, which have no other form: a chart, where each row sits in a block of its own,
 * an artist's modules, where each is a table row, and a module's page, which names its registered artists.
 * A row that does not read is left out rather than losing the page; a listing page with no rows at all is
 * refused, since that is the site changing shape or being down, and neither is worth keeping.
 */
final class ModArchiveHtml {

    private static final String BLOCK = "<div class=\"bgsegment";
    private static final Pattern MODULE = Pattern.compile(
            "<a class=\"chart-listing-title\" href=\"module\\.php\\?(\\d+)\">(.*?)</a>", Pattern.DOTALL);
    private static final Pattern FILE_NAME = Pattern.compile("<span class=\"chart-listing\">(.*?)</span>", Pattern.DOTALL);
    private static final String ROW = "<tr>";
    private static final Pattern ARTIST_MODULE = Pattern.compile(
            "<a class=\"module-listing\" href=\"module\\.php\\?(\\d+)\" title=\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern ARTIST_TITLE = Pattern.compile("<span class=\"module-listing\">(.*?)</span>", Pattern.DOTALL);
    private static final Pattern RATING = Pattern.compile("Rated ([\\d.]+ / 10)");
    private static final String REGISTERED_ARTISTS = "Registered Artist(s)";
    private static final String LIST_END = "</ul>";
    private static final Pattern MEMBER = Pattern.compile("member\\.php\\?(\\d+)\">(.*?)</a>", Pattern.DOTALL);
    private static final Pattern PAGE = Pattern.compile("[?&;]page=(\\d+)");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#(\\d+)|amp|lt|gt|quot|nbsp);");

    private ModArchiveHtml() {
    }

    static ChartPage parse(Listing listing, int page, byte[] body) throws IOException {
        final String html = new String(body, StandardCharsets.UTF_8);
        final List<ChartEntry> entries = switch (listing) {
            case Chart chart -> Arrays.stream(html.split(Pattern.quote(BLOCK))).flatMap(block -> entry(chart, block).stream()).toList();
            case Artist artist -> Arrays.stream(html.split(Pattern.quote(ROW))).flatMap(row -> entry(row).stream()).toList();
        };
        if (entries.isEmpty()) {
            throw new IOException("No entries on " + listing.title() + " page " + page);
        }
        return new ChartPage(listing, page, lastPage(html, page), entries);
    }

    /**
     * The artists a module page names as registered, in the order it lists them; none where the page has
     * no such list, which is how the site shows a module whose author never signed up.
     */
    static List<Artist> artists(byte[] modulePage) {
        final String html = new String(modulePage, StandardCharsets.UTF_8);
        final int heading = html.indexOf(REGISTERED_ARTISTS);
        if (heading < 0) {
            return List.of();
        }
        final int end = html.indexOf(LIST_END, heading);
        final Matcher members = MEMBER.matcher(html.substring(heading, end < 0 ? html.length() : end));
        final List<Artist> artists = new ArrayList<>();
        while (members.find()) {
            artists.add(new Artist(Integer.parseInt(members.group(1)), unescape(members.group(2)).strip()));
        }
        return artists;
    }

    private static Optional<ChartEntry> entry(String row) {
        final Matcher module = ARTIST_MODULE.matcher(row);
        if (!module.find()) {
            return Optional.empty();
        }
        final Matcher title = ARTIST_TITLE.matcher(row);
        final Matcher rating = RATING.matcher(row);
        return Optional.of(new ChartEntry(Integer.parseInt(module.group(1)),
                title.find() ? unescape(title.group(1)).strip() : "", unescape(module.group(2)).strip(),
                rating.find() ? rating.group(1) : ""));
    }

    private static Optional<ChartEntry> entry(Chart chart, String block) {
        final Matcher module = MODULE.matcher(block);
        if (!module.find()) {
            return Optional.empty();
        }
        final Matcher fileName = FILE_NAME.matcher(block);
        final String text = unescape(TAG.matcher(block).replaceAll(" "));
        return Optional.of(new ChartEntry(Integer.parseInt(module.group(1)), unescape(module.group(2)).strip(),
                fileName.find() ? unescape(fileName.group(1)).strip() : "", chart.measure(text)));
    }

    private static int lastPage(String html, int page) {
        final Matcher pages = PAGE.matcher(html);
        int last = page;
        while (pages.find()) {
            last = Math.max(last, Integer.parseInt(pages.group(1)));
        }
        return last;
    }

    static String unescape(String text) {
        final Matcher entities = ENTITY.matcher(text);
        final StringBuilder plain = new StringBuilder(text.length());
        while (entities.find()) {
            final String replacement = switch (entities.group(1)) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "nbsp" -> " ";
                default -> Character.toString(Integer.parseInt(entities.group(2)));
            };
            entities.appendReplacement(plain, Matcher.quoteReplacement(replacement));
        }
        return entities.appendTail(plain).toString();
    }
}

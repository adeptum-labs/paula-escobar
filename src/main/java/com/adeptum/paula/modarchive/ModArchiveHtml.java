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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a chart listing off the site's own pages, which have no other form. Each row sits in a block of its
 * own, so a block that does not read is left out rather than losing the page; a page with no rows at all is
 * refused, since that is the site changing shape or being down, and neither is worth keeping.
 */
final class ModArchiveHtml {

    private static final String BLOCK = "<div class=\"bgsegment";
    private static final Pattern MODULE = Pattern.compile(
            "<a class=\"chart-listing-title\" href=\"module\\.php\\?(\\d+)\">(.*?)</a>", Pattern.DOTALL);
    private static final Pattern FILE_NAME = Pattern.compile("<span class=\"chart-listing\">(.*?)</span>", Pattern.DOTALL);
    private static final Pattern PAGE = Pattern.compile("[?&;]page=(\\d+)");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#(\\d+)|amp|lt|gt|quot|nbsp);");

    private ModArchiveHtml() {
    }

    static ChartPage parse(Chart chart, int page, byte[] body) throws IOException {
        final String html = new String(body, StandardCharsets.UTF_8);
        final List<ChartEntry> entries = new ArrayList<>();
        for (final String block : html.split(Pattern.quote(BLOCK))) {
            entry(chart, block).ifPresent(entries::add);
        }
        if (entries.isEmpty()) {
            throw new IOException("No chart entries on " + chart.title() + " page " + page);
        }
        return new ChartPage(chart, page, lastPage(html, page), entries);
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

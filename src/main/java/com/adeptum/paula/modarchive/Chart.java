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

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The charts modarchive.org keeps, each read off its own listing pages. The revered chart is left out, since
 * the site serves it the same rows as the downloads.
 */
public enum Chart implements Listing {

    TOP_FAVOURITES("top-favourites", "Top Favourites", "request=view_top_favourites",
            Pattern.compile("A favourite of ([\\d,]+) member"), " favourites"),
    MOST_DOWNLOADS("most-downloads", "Most Downloads", "request=view_chart&query=tophits",
            Pattern.compile("Downloaded ([\\d,]+) times"), " downloads"),
    FEATURED("featured", "Featured", "request=view_chart&query=featured",
            Pattern.compile("Featured (Week \\d+, \\d+)"), "");


    private final String id;
    private final String title;
    private final String query;
    private final Pattern measure;
    private final String unit;

    Chart(String id, String title, String query, Pattern measure, String unit) {
        this.id = id;
        this.title = title;
        this.query = query;
        this.measure = measure;
        this.unit = unit;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public URI page(int page) {
        return URI.create(SITE + query + PAGE + page);
    }

    /**
     * What the chart counts, as the listing says it, or nothing where the sentence is not there.
     */
    String measure(String text) {
        final Matcher matcher = measure.matcher(text);
        return matcher.find() ? matcher.group(1) + unit : "";
    }
}

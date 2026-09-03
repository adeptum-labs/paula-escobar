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

package com.adeptum.paula.demozoo;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Serves canned responses by URL and counts requests; unknown URLs and offline mode fail like a network would.
 */
public final class FakeHttp implements HttpFetcher {

    private final Map<URI, Response> responses = new HashMap<>();
    private int requests;
    private boolean offline;

    public void put(String url, String body) {
        put(url, body.getBytes(StandardCharsets.UTF_8), Optional.empty());
    }

    public void put(String url, byte[] body, Optional<String> fileName) {
        responses.put(URI.create(url), new Response(body, fileName));
    }

    public void goOffline() {
        offline = true;
    }

    public void goOnline() {
        offline = false;
    }

    public int requests() {
        return requests;
    }

    @Override
    public Response get(URI uri) throws IOException {
        requests++;
        if (offline) {
            throw new IOException("offline");
        }
        final Response response = responses.get(uri);
        if (response == null) {
            throw new IOException("HTTP 404 from " + uri.getHost());
        }
        return response;
    }
}

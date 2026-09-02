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

package com.adeptum.paula.demozoo;

import com.adeptum.paula.cli.BuildInfo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JdkHttpFetcher implements HttpFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    private static final Pattern FILE_NAME = Pattern.compile("filename=\"?([^\";]+)\"?");
    private static final int SUCCESS_CLASS = 2;

    private final String userAgent;
    private HttpClient client;

    public JdkHttpFetcher(String userAgent) {
        this.userAgent = userAgent;
    }

    public static JdkHttpFetcher paula() throws IOException {
        return new JdkHttpFetcher("PaulaEscobar/" + BuildInfo.version());
    }

    @Override
    public Response get(URI uri) throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header(USER_AGENT_HEADER, userAgent).GET().build();
        try {
            final HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != SUCCESS_CLASS) {
                throw new IOException("HTTP " + response.statusCode() + " from " + uri.getHost());
            }
            return new Response(response.body(), fileName(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, e);
        }
    }

    /**
     * Created on first use so no networking is initialised while the native image is built.
     */
    private synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
        }
        return client;
    }

    private static Optional<String> fileName(HttpResponse<?> response) {
        return response.headers().firstValue(CONTENT_DISPOSITION_HEADER).map(FILE_NAME::matcher).filter(Matcher::find).map(m -> m.group(1));
    }
}

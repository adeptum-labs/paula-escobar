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

import com.adeptum.paula.cli.BuildInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final long UNKNOWN_LENGTH = -1;
    private static final int BLOCK = 16 * 1024;

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
        return get(uri, Watcher.NONE);
    }

    /**
     * The body is read in blocks rather than in one go, so that whoever is waiting can be told how far along a
     * long download is while it happens.
     */
    @Override
    public Response get(URI uri, Watcher watcher) throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header(USER_AGENT_HEADER, userAgent).GET().build();
        try {
            final HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != SUCCESS_CLASS) {
                response.body().close();
                throw new IOException("HTTP " + response.statusCode() + " from " + uri.getHost());
            }
            return new Response(body(response, watcher), fileName(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, e);
        }
    }

    private static byte[] body(HttpResponse<InputStream> response, Watcher watcher) throws IOException {
        final long total = response.headers().firstValueAsLong(CONTENT_LENGTH_HEADER).orElse(UNKNOWN_LENGTH);
        final ByteArrayOutputStream body = new ByteArrayOutputStream(total > 0 ? (int) total : BLOCK);
        try (InputStream in = response.body()) {
            final byte[] block = new byte[BLOCK];
            for (int read = in.read(block); read >= 0; read = in.read(block)) {
                body.write(block, 0, read);
                watcher.read(body.size(), total);
            }
        }
        return body.toByteArray();
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

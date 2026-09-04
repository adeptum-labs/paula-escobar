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
    /**
     * Long enough for a recorded track of many megabytes on a poor line; a module arrives in a moment, but a
     * streaming compo entry can run past ten minutes of audio and the whole of it is fetched before it plays.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    private static final Pattern FILE_NAME = Pattern.compile("filename=\"?([^\";]+)\"?");
    private static final int SUCCESS_CLASS = 2;
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final long UNKNOWN_LENGTH = -1;

    private final String userAgent;
    private final Duration timeout;
    private HttpClient client;

    public JdkHttpFetcher(String userAgent) {
        this(userAgent, REQUEST_TIMEOUT);
    }

    JdkHttpFetcher(String userAgent, Duration timeout) {
        this.userAgent = userAgent;
        this.timeout = timeout;
    }

    public static JdkHttpFetcher paula() throws IOException {
        return new JdkHttpFetcher("PaulaEscobar/" + BuildInfo.version());
    }

    @Override
    public Response get(URI uri) throws IOException {
        return get(uri, Watcher.NONE);
    }

    /**
     * The body is taken block by block, so that whoever is waiting can be told how far along a long download
     * has come. The blocks are gathered while the request is still in hand rather than read from a stream
     * afterwards, since only then does the timeout cover the body: a stream handed back and read at leisure
     * can stall for ever with nothing to stop it.
     */
    @Override
    public Response get(URI uri, Watcher watcher) throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header(USER_AGENT_HEADER, userAgent).GET().build();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            final HttpResponse<Void> response = client().send(request, info -> blocks(info, body, watcher));
            if (response.statusCode() / 100 != SUCCESS_CLASS) {
                throw new IOException("HTTP " + response.statusCode() + " from " + uri.getHost());
            }
            return new Response(body.toByteArray(), fileName(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, e);
        }
    }

    private static HttpResponse.BodySubscriber<Void> blocks(HttpResponse.ResponseInfo info,
            ByteArrayOutputStream body, Watcher watcher) {
        final long total = info.headers().firstValueAsLong(CONTENT_LENGTH_HEADER).orElse(UNKNOWN_LENGTH);
        return HttpResponse.BodySubscribers.ofByteArrayConsumer(block -> block.ifPresent(bytes -> {
            body.writeBytes(bytes);
            watcher.read(body.size(), total);
        }));
    }

    /**
     * Created on first use so no networking is initialised while the native image is built. Redirects are
     * followed even down to plain HTTP, since several of the scene archives still hop through it and nothing
     * secret is ever sent or asked for.
     */
    private synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.ALWAYS).build();
        }
        return client;
    }

    /**
     * A download script such as AMP's names nothing itself and redirects to the file; the name is then the one
     * at the end of the redirect rather than the query the request began as.
     */
    private static Optional<String> fileName(HttpResponse<?> response) {
        return response.headers().firstValue(CONTENT_DISPOSITION_HEADER).map(FILE_NAME::matcher)
                .filter(Matcher::find).map(matcher -> matcher.group(1))
                .or(() -> redirectedName(response));
    }

    private static Optional<String> redirectedName(HttpResponse<?> response) {
        final String path = response.uri().getPath();
        return Optional.ofNullable(path).map(name -> name.substring(name.lastIndexOf('/') + 1)).filter(name -> !name.isBlank());
    }
}

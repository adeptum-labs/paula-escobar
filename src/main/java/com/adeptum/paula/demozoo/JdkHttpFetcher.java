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
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
    /**
     * A database export of a couple of hundred megabytes crawls along a slow line for far longer than a track
     * ever could, so it is given half an hour rather than five minutes.
     */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(30);
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String ETAG_HEADER = "ETag";
    private static final String LAST_MODIFIED_HEADER = "Last-Modified";
    private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    private static final Pattern FILE_NAME = Pattern.compile("filename=\"?([^\";]+)\"?");
    private static final int SUCCESS_CLASS = 2;
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final long UNKNOWN_LENGTH = -1;

    private final String userAgent;
    private final Duration timeout;
    private final Duration downloadTimeout;
    private HttpClient client;

    public JdkHttpFetcher(String userAgent) {
        this(userAgent, REQUEST_TIMEOUT, DOWNLOAD_TIMEOUT);
    }

    JdkHttpFetcher(String userAgent, Duration timeout) {
        this(userAgent, timeout, DOWNLOAD_TIMEOUT);
    }

    JdkHttpFetcher(String userAgent, Duration timeout, Duration downloadTimeout) {
        this.userAgent = userAgent;
        this.timeout = timeout;
        this.downloadTimeout = downloadTimeout;
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
            requireSuccess(uri, response.statusCode());
            return new Response(body.toByteArray(), fileName(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, e);
        }
    }

    /**
     * Asks after the export with none of it sent; the handle and date the server keeps tell whether it has
     * changed since it was last fetched.
     */
    @Override
    public Head head(URI uri) throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header(USER_AGENT_HEADER, userAgent)
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        try {
            final HttpResponse<Void> response = client().send(request, HttpResponse.BodyHandlers.discarding());
            requireSuccess(uri, response.statusCode());
            return new Head(response.headers().firstValue(ETAG_HEADER),
                    response.headers().firstValue(LAST_MODIFIED_HEADER),
                    response.headers().firstValueAsLong(CONTENT_LENGTH_HEADER).orElse(UNKNOWN_LENGTH));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, e);
        }
    }

    /**
     * The body is written to the file block by block as it arrives, since an export of a couple of hundred
     * megabytes is not to be held in memory.
     */
    @Override
    public void download(URI uri, Path target, Watcher watcher) throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(uri).timeout(downloadTimeout).header(USER_AGENT_HEADER, userAgent).GET().build();
        try (OutputStream file = Files.newOutputStream(target)) {
            requireSuccess(uri, client().send(request, info -> blocks(info, file, watcher)).statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw abandoned(target, new IOException("Interrupted while downloading " + uri, e));
        } catch (UncheckedIOException e) {
            throw abandoned(target, e.getCause());
        } catch (IOException e) {
            throw abandoned(target, e);
        }
    }

    /**
     * Each block is written out as it arrives, so a body of any size passes through, and whoever is watching is
     * told how much of it has come.
     */
    private static HttpResponse.BodySubscriber<Void> blocks(HttpResponse.ResponseInfo info, OutputStream body,
            Watcher watcher) {
        final long total = info.headers().firstValueAsLong(CONTENT_LENGTH_HEADER).orElse(UNKNOWN_LENGTH);
        final long[] written = {0};
        return HttpResponse.BodySubscribers.ofByteArrayConsumer(block -> block.ifPresent(bytes -> {
            try {
                body.write(bytes);
            } catch (IOException e) {
                // A body subscriber can report no checked failure, so a write that fails travels out unchecked.
                throw new UncheckedIOException(e);
            }
            written[0] += bytes.length;
            watcher.read(written[0], total);
        }));
    }

    private static void requireSuccess(URI uri, int status) throws IOException {
        if (status / 100 != SUCCESS_CLASS) {
            throw new IOException("HTTP " + status + " from " + uri.getHost());
        }
    }

    /**
     * A half-written export looks whole to whatever opens it next, so a download that fails part way through
     * takes its file with it.
     */
    private static IOException abandoned(Path target, IOException failure) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            failure.addSuppressed(e);
        }
        return failure;
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

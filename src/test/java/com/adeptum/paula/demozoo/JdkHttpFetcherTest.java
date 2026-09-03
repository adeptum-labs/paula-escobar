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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkHttpFetcherTest {

    private static final int ANY_PORT = 0;
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final String DISPOSITION = "Content-Disposition";

    private final JdkHttpFetcher fetcher = new JdkHttpFetcher("paula-test");
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(ANY_PORT), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsTheWholeBodyBackWhateverItsLength() throws IOException {
        final byte[] body = noise(300_000);
        serve("/tune.mod", body, Optional.empty());

        assertArrayEquals(body, fetcher.get(uri("/tune.mod")).body(), "the body survives being read in blocks");
    }

    /**
     * The body arrives in blocks, so whoever is waiting on a long download can be told how far along it is.
     */
    @Test
    void countsTheBodyUpAsItArrives() throws IOException {
        final byte[] body = noise(300_000);
        serve("/tune.mod", body, Optional.empty());
        final List<Long> reads = new ArrayList<>();

        final byte[] fetched = fetcher.get(uri("/tune.mod"), (read, total) -> {
            reads.add(read);
            assertEquals(body.length, total, "the length the server promised");
        }).body();

        assertArrayEquals(body, fetched);
        assertTrue(reads.size() > 1, "it was counted up in blocks, not handed over at once");
        assertEquals(body.length, reads.get(reads.size() - 1), "and the last count is the whole of it");
        assertEquals(reads.stream().sorted().toList(), reads, "counting only ever goes up");
    }

    @Test
    void takesTheNameTheServerGivesTheFile() throws IOException {
        serve("/download.php", "x".getBytes(StandardCharsets.UTF_8), Optional.of("attachment; filename=\"tune.mod\""));

        assertEquals(Optional.of("tune.mod"), fetcher.get(uri("/download.php")).fileName());
    }

    @Test
    void reportsWhatTheServerRefused() {
        serve("/missing", new byte[0], Optional.empty(), NOT_FOUND);

        final IOException thrown = assertThrows(IOException.class, () -> fetcher.get(uri("/missing")));
        assertTrue(thrown.getMessage().contains("404"), thrown.getMessage());
    }

    private void serve(String path, byte[] body, Optional<String> disposition) {
        serve(path, body, disposition, OK);
    }

    private void serve(String path, byte[] body, Optional<String> disposition, int status) {
        server.createContext(path, exchange -> {
            disposition.ifPresent(value -> exchange.getResponseHeaders().add(DISPOSITION, value));
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private static byte[] noise(int length) {
        final byte[] body = new byte[length];
        new Random(7).nextBytes(body);
        return body;
    }
}

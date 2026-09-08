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

package com.adeptum.paula.cast;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cast.CastStreamServer.Served;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CastStreamServerTest {

    private static final int SAMPLE_RATE = 48000;
    private static final int FRAMES = 4;
    private static final short[] TONE = {100, -100, 200, -200, 300, -300, 400, -400};
    private static final byte[] TONE_BYTES = {100, 0, -100, -1, -56, 0, 56, -1, 44, 1, -44, -2, -112, 1, 112, -2};

    @Test
    void servesAWaveFileThatNeverEndsAtTheAddressItGaveOut() throws Exception {
        try (CastStreamServer server = new CastStreamServer(InetAddress.getLoopbackAddress())) {
            final Served served = server.open(SAMPLE_RATE);
            final URI url = URI.create(served.url());
            assertEquals("127.0.0.1", url.getHost());
            assertEquals(server.port(), url.getPort());

            served.stream().write(TONE, FRAMES);
            served.stream().end();
            final Response response = get(url, "GET " + url.getPath() + " HTTP/1.1\r\nHost: x\r\n\r\n");

            assertTrue(response.headers.startsWith("HTTP/1.1 200 OK"));
            assertTrue(response.headers.contains("Content-Type: audio/wav"));
            assertEquals("RIFF", new String(response.body, 0, 4, StandardCharsets.US_ASCII));
            assertEquals("data", new String(response.body, 36, 4, StandardCharsets.US_ASCII));
            assertArrayEquals(TONE_BYTES, Arrays.copyOfRange(response.body, 44, 44 + TONE_BYTES.length),
                    "the samples follow the header, smallest byte first");
        }
    }

    @Test
    void ignoresTheRangeTheDeviceAsksFor() throws Exception {
        try (CastStreamServer server = new CastStreamServer(InetAddress.getLoopbackAddress())) {
            final Served served = server.open(SAMPLE_RATE);
            served.stream().end();
            final URI url = URI.create(served.url());
            final Response response = get(url, "GET " + url.getPath() + " HTTP/1.1\r\nRange: bytes=0-\r\n\r\n");

            assertTrue(response.headers.startsWith("HTTP/1.1 200 OK"), "the whole stream, not a part of it");
            assertTrue(response.headers.contains("Accept-Ranges: none"));
        }
    }

    @Test
    void answersAHeadRequestWithTheHeadersAlone() throws Exception {
        try (CastStreamServer server = new CastStreamServer(InetAddress.getLoopbackAddress())) {
            final Served served = server.open(SAMPLE_RATE);
            final URI url = URI.create(served.url());
            final Response response = get(url, "HEAD " + url.getPath() + " HTTP/1.1\r\n\r\n");

            assertTrue(response.headers.startsWith("HTTP/1.1 200 OK"));
            assertEquals(0, response.body.length);
        }
    }

    @Test
    void knowsNothingOfAStreamItWasNotAskedToOpen() throws Exception {
        try (CastStreamServer server = new CastStreamServer(InetAddress.getLoopbackAddress())) {
            final URI url = URI.create(server.open(SAMPLE_RATE).url());
            final Response response = get(url, "GET /elsewhere.wav HTTP/1.1\r\n\r\n");

            assertTrue(response.headers.startsWith("HTTP/1.1 404"));
        }
    }

    @Test
    void makesTheWriterWaitWhileTheDeviceHasNotFetched() throws Exception {
        final PcmStream stream = new PcmStream(SAMPLE_RATE, FRAMES);
        assertTrue(stream.write(TONE, FRAMES), "the first chunk fits");

        final Thread writer = new Thread(() -> stream.write(TONE, FRAMES));
        writer.start();
        writer.join(200);
        assertTrue(writer.isAlive(), "the second waits for room");

        assertEquals(TONE_BYTES.length, stream.read().length);
        writer.join(2000);
        assertFalse(writer.isAlive(), "and goes on once some was read");
        assertEquals(FRAMES * 2, stream.writtenFrames());
        stream.abandon();
    }

    @Test
    void letsAWaitingWriterGoWhenTheStreamIsAbandoned() throws Exception {
        final PcmStream stream = new PcmStream(SAMPLE_RATE, FRAMES);
        stream.write(TONE, FRAMES);
        final boolean[] outcome = new boolean[1];
        final Thread writer = new Thread(() -> outcome[0] = stream.write(TONE, FRAMES));
        writer.start();
        writer.join(100);

        stream.abandon();
        writer.join(2000);

        assertFalse(writer.isAlive());
        assertFalse(outcome[0], "a write to a stream nobody reads says so");
    }

    private record Response(String headers, byte[] body) {
    }

    private static Response get(URI url, String request) throws IOException {
        try (Socket socket = new Socket(url.getHost(), url.getPort())) {
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            final InputStream in = socket.getInputStream();
            final ByteArrayOutputStream all = new ByteArrayOutputStream();
            in.transferTo(all);
            final byte[] bytes = all.toByteArray();
            final int split = indexOf(bytes, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            return new Response(new String(bytes, 0, split, StandardCharsets.US_ASCII),
                    Arrays.copyOfRange(bytes, split + 4, bytes.length));
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int at = 0; at + needle.length <= haystack.length; at++) {
            if (Arrays.equals(haystack, at, at + needle.length, needle, 0, needle.length)) {
                return at;
            }
        }
        return haystack.length;
    }
}

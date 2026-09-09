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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Serves the sound to the device, which fetches it rather than being sent it: a Cast device is told a web
 * address and plays what it finds there.
 *
 * <p>Each stream is a wave file with no end, its length field set to the most a wave file can say, written
 * out as fast as the device takes it. The device asks for a range now and then; there is no range to give of
 * sound that has not happened yet, so it gets the whole stream from wherever it is.</p>
 */
@Slf4j
public final class CastStreamServer implements AutoCloseable {

    private static final int CHANNELS = 2;
    private static final int BITS = 16;
    private static final int WAVE_HEADER_LENGTH = 44;
    private static final int ENDLESS = 0x7FFF_FFF0;
    private static final int BACKLOG = 4;

    /**
     * The port the sound is served on unless another is asked for, fixed so that a firewall between the
     * player and the device can be told about it once; taken to be any free port when it is in use.
     */
    public static final int DEFAULT_PORT = 7373;
    private static final String CRLF = "\r\n";

    /**
     * Two seconds of sound held for the device, beyond which the player waits: enough to ride out a fetch
     * that comes late, little enough that the lag stays short.
     */
    private static final int HELD_SECONDS = 2;

    /**
     * The sound the device is handed in one go when it takes a stream up, so that what it starts on is worth
     * starting on rather than the first trickle of the song.
     */
    static final int START_SECONDS = 3;

    /**
     * How far ahead of real time the sound may go out. A device asked to play a stream while it is already
     * playing another will not start on the new one until it holds some nine seconds of it, and paced at the
     * speed it plays it takes six seconds to get there: six seconds of silence on the device, and six
     * seconds the player, held back only once the device is playing, spends running ahead of it. Given room
     * to go out as fast as the player renders, the device has its fill within the moment. Nothing runs this
     * far ahead once the device plays, since the player is held to the lag long before then.
     */
    private static final int AHEAD_SECONDS = 10;
    private static final long PACE_STEP_MILLIS = 20;

    private final ServerSocket server;
    private final Map<String, PcmStream> streams = new ConcurrentHashMap<>();
    private final Map<PcmStream, Socket> connections = new ConcurrentHashMap<>();
    private final AtomicInteger names = new AtomicInteger();
    private volatile boolean closed;

    public CastStreamServer(InetAddress bind, int port) throws IOException {
        this.server = listen(bind, port);
        final Thread thread = new Thread(this::serve, "paula-cast-stream");
        thread.setDaemon(true);
        thread.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    private static ServerSocket listen(InetAddress bind, int port) throws IOException {
        try {
            return new ServerSocket(port, BACKLOG, bind);
        } catch (IOException taken) {
            log.debug("Port {} is taken, serving the sound on any free one", port, taken);
            return new ServerSocket(0, BACKLOG, bind);
        }
    }

    /**
     * A new stream at an address of its own, which is what the device is told to play.
     */
    public Served open(int sampleRate) {
        final String path = "/paula-" + names.incrementAndGet() + ".wav";
        final PcmStream stream = new PcmStream(sampleRate, sampleRate * HELD_SECONDS);
        streams.put(path, stream);
        return new Served(stream, "http://" + server.getInetAddress().getHostAddress() + ":" + port() + path);
    }

    /**
     * Done with a stream, and with the connection the device was reading it over. Abandoning the sound alone
     * leaves a connection whose writer is stuck against a device that has stopped reading, and the device is
     * slow to take up the next stream while the one before is still open to it.
     */
    public void forget(Served served) {
        served.stream().abandon();
        streams.values().remove(served.stream());
        hangUp(connections.remove(served.stream()));
    }

    @Override
    public void close() {
        closed = true;
        streams.values().forEach(PcmStream::abandon);
        connections.values().forEach(CastStreamServer::hangUp);
        try {
            server.close();
        } catch (IOException e) {
            log.debug("Closing the stream server", e);
        }
    }

    private static void hangUp(Socket client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (IOException e) {
            log.debug("Closing a stream connection", e);
        }
    }

    public record Served(PcmStream stream, String url) {
    }

    private void serve() {
        while (!closed) {
            try {
                final Socket client = server.accept();
                final Thread thread = new Thread(() -> answer(client), "paula-cast-stream-" + client.getPort());
                thread.setDaemon(true);
                thread.start();
            } catch (IOException e) {
                if (!closed) {
                    log.debug("Accepting a stream connection", e);
                }
            }
        }
    }

    private void answer(Socket client) {
        try (client) {
            final Request request = Request.read(client);
            log.debug("{} asked for {}{}", client.getInetAddress().getHostAddress(), request == null ? "nothing" : request.path,
                    request == null || request.range.isEmpty() ? "" : " from " + request.range);
            final PcmStream stream = request == null ? null : streams.get(request.path);
            final OutputStream out = client.getOutputStream();
            if (stream == null) {
                out.write(("HTTP/1.1 404 Not Found" + CRLF + "Connection: close" + CRLF + CRLF).getBytes(StandardCharsets.US_ASCII));
                return;
            }
            out.write(headers().getBytes(StandardCharsets.US_ASCII));
            if (request.head) {
                return;
            }
            stream.fetching();
            connections.put(stream, client);
            final int bytesPerSecond = stream.sampleRate() * CHANNELS * BITS / Byte.SIZE;
            final byte[] start = soundToStartOn(stream, (long) bytesPerSecond * START_SECONDS);
            out.write(waveHeader(stream.sampleRate()));
            out.write(start);
            out.flush();
            pump(stream, out, bytesPerSecond, start.length);
        } catch (IOException | InterruptedException e) {
            log.debug("A stream connection ended", e);
        }
    }

    /**
     * The sound the device is handed before anything else, gathered rather than waited for so that a stream
     * holding less than this is still read out of while it fills. Short of it only where the song ends first.
     */
    private static byte[] soundToStartOn(PcmStream stream, long bytes) throws IOException, InterruptedException {
        final ByteArrayOutputStream start = new ByteArrayOutputStream();
        byte[] chunk;
        while (start.size() < bytes && (chunk = stream.read()) != null) {
            start.write(chunk);
        }
        return start.toByteArray();
    }

    private static void pump(PcmStream stream, OutputStream out, int bytesPerSecond, long alreadySent)
            throws IOException, InterruptedException {
        final long started = System.nanoTime();
        long sent = alreadySent;
        byte[] chunk;
        while ((chunk = stream.read()) != null) {
            while (sent > allowed(started, bytesPerSecond)) {
                Thread.sleep(PACE_STEP_MILLIS);
            }
            out.write(chunk);
            out.flush();
            sent += chunk.length;
        }
    }

    private static long allowed(long started, int bytesPerSecond) {
        return (long) AHEAD_SECONDS * bytesPerSecond + (System.nanoTime() - started) / 1_000_000_000L * bytesPerSecond
                + (System.nanoTime() - started) % 1_000_000_000L * bytesPerSecond / 1_000_000_000L;
    }

    private static String headers() {
        return "HTTP/1.1 200 OK" + CRLF
                + "Content-Type: audio/wav" + CRLF
                + "Accept-Ranges: none" + CRLF
                + "Cache-Control: no-cache, no-store" + CRLF
                + "Connection: close" + CRLF
                + CRLF;
    }

    static byte[] waveHeader(int sampleRate) {
        final ByteBuffer header = ByteBuffer.allocate(WAVE_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(ENDLESS + WAVE_HEADER_LENGTH - 8);
        header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        header.putShort((short) 1).putShort((short) CHANNELS).putInt(sampleRate);
        header.putInt(sampleRate * CHANNELS * BITS / Byte.SIZE).putShort((short) (CHANNELS * BITS / Byte.SIZE));
        header.putShort((short) BITS);
        header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(ENDLESS);
        return header.array();
    }

    private record Request(String path, boolean head, String range) {

        /**
         * The request line and headers, of which only the method and the path say anything to a stream.
         */
        static Request read(Socket client) throws IOException {
            final BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            final String line = in.readLine();
            if (line == null) {
                return null;
            }
            final String[] parts = line.split(" ");
            if (parts.length < 2) {
                return null;
            }
            String header;
            String range = "";
            while ((header = in.readLine()) != null && !header.isEmpty()) {
                if (header.regionMatches(true, 0, "Range:", 0, "Range:".length())) {
                    range = header.substring("Range:".length()).strip();
                }
            }
            return new Request(parts[1], "HEAD".equals(parts[0]), range);
        }
    }
}

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
import java.time.Duration;
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

    /**
     * The most silence written to make a song up to the length it said, so a length that is badly wrong ends
     * the stream rather than holding the connection open for hours.
     */
    private static final long MOST_SILENCE = 30L * 192_000;

    private final ServerSocket server;
    private final Map<String, PcmStream> streams = new ConcurrentHashMap<>();
    private final Map<PcmStream, Socket> connections = new ConcurrentHashMap<>();
    private final Map<String, byte[]> pictures = new ConcurrentHashMap<>();
    private final Map<PcmStream, Long> lengths = new ConcurrentHashMap<>();
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
     * A new stream at an address of its own, which is what the device is told to play, and beside it the
     * picture to show while it plays where there is one for the device to fetch.
     */
    public Served open(int sampleRate, Duration length, byte[] picture) {
        final String name = "/paula-" + names.incrementAndGet();
        final PcmStream stream = new PcmStream(sampleRate, sampleRate * HELD_SECONDS);
        lengths.put(stream, length == null ? ENDLESS
                : Math.min(ENDLESS, Math.round(length.toMillis() / 1000.0 * sampleRate) * CHANNELS * BITS / Byte.SIZE));
        streams.put(name + ".wav", stream);
        if (picture.length > 0) {
            pictures.put(name + ".png", picture);
        }
        return new Served(stream, at(name + ".wav"), picture.length > 0 ? at(name + ".png") : null);
    }

    private String at(String path) {
        return "http://" + server.getInetAddress().getHostAddress() + ":" + port() + path;
    }

    /**
     * Done with a stream, and with the connection the device was reading it over. Abandoning the sound alone
     * leaves a connection whose writer is stuck against a device that has stopped reading, and the device is
     * slow to take up the next stream while the one before is still open to it.
     */
    public void forget(Served served) {
        served.stream().abandon();
        streams.values().remove(served.stream());
        lengths.remove(served.stream());
        pictures.keySet().removeIf(path -> at(path).equals(served.picture()));
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

    public record Served(PcmStream stream, String url, String picture) {
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
            final OutputStream out = client.getOutputStream();
            final byte[] picture = request == null ? null : pictures.get(request.path);
            if (picture != null) {
                out.write(pictureHeaders(picture.length).getBytes(StandardCharsets.US_ASCII));
                out.write(request.head ? new byte[0] : picture);
                return;
            }
            final PcmStream stream = request == null ? null : streams.get(request.path);
            if (stream == null) {
                out.write(("HTTP/1.1 404 Not Found" + CRLF + "Connection: close" + CRLF + CRLF).getBytes(StandardCharsets.US_ASCII));
                return;
            }
            final long sound = lengths.getOrDefault(stream, (long) ENDLESS);
            out.write(headers(sound).getBytes(StandardCharsets.US_ASCII));
            if (request.head) {
                return;
            }
            stream.fetching();
            connections.put(stream, client);
            final int bytesPerSecond = stream.sampleRate() * CHANNELS * BITS / Byte.SIZE;
            final byte[] start = soundToStartOn(stream, (long) bytesPerSecond * START_SECONDS);
            out.write(waveHeader(stream.sampleRate(), sound));
            out.write(start);
            out.flush();
            pump(stream, out, bytesPerSecond, start.length, sound);
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

    private static void pump(PcmStream stream, OutputStream out, int bytesPerSecond, long alreadySent, long sound)
            throws IOException, InterruptedException {
        final long started = System.nanoTime();
        long sent = alreadySent;
        byte[] chunk;
        while (sent < sound && (chunk = stream.read()) != null) {
            while (sent > allowed(started, bytesPerSecond)) {
                Thread.sleep(PACE_STEP_MILLIS);
            }
            out.write(chunk, 0, (int) Math.min(chunk.length, sound - sent));
            out.flush();
            sent += chunk.length;
        }
        fillOut(out, sound - sent);
    }

    /**
     * Silence to the length the wave header promised, for a song that ended a shade before the length it gave.
     * A device told how long the sound is waits for all of it, and would sit at the end of the song otherwise.
     */
    private static void fillOut(OutputStream out, long missing) throws IOException {
        final byte[] silence = new byte[8192];
        for (long left = Math.min(missing, MOST_SILENCE); left > 0; left -= silence.length) {
            out.write(silence, 0, (int) Math.min(silence.length, left));
        }
        out.flush();
    }

    private static long allowed(long started, int bytesPerSecond) {
        return (long) AHEAD_SECONDS * bytesPerSecond + (System.nanoTime() - started) / 1_000_000_000L * bytesPerSecond
                + (System.nanoTime() - started) % 1_000_000_000L * bytesPerSecond / 1_000_000_000L;
    }

    private static String pictureHeaders(int length) {
        return "HTTP/1.1 200 OK" + CRLF
                + "Content-Type: image/png" + CRLF
                + "Content-Length: " + length + CRLF
                + "Connection: close" + CRLF
                + CRLF;
    }

    private static String headers(long sound) {
        return "HTTP/1.1 200 OK" + CRLF
                + "Content-Type: audio/wav" + CRLF
                + (sound < ENDLESS ? "Content-Length: " + (sound + WAVE_HEADER_LENGTH) + CRLF : "")
                + "Accept-Ranges: none" + CRLF
                + "Cache-Control: no-cache, no-store" + CRLF
                + "Connection: close" + CRLF
                + CRLF;
    }

    static byte[] waveHeader(int sampleRate, long sound) {
        final ByteBuffer header = ByteBuffer.allocate(WAVE_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt((int) sound + WAVE_HEADER_LENGTH - 8);
        header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        header.putShort((short) 1).putShort((short) CHANNELS).putInt(sampleRate);
        header.putInt(sampleRate * CHANNELS * BITS / Byte.SIZE).putShort((short) (CHANNELS * BITS / Byte.SIZE));
        header.putShort((short) BITS);
        header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt((int) sound);
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

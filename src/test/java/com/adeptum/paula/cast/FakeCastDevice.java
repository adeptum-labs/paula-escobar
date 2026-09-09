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

import com.adeptum.paula.cast.CastMessages.CastMessage;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A Cast device played by a test: answers over a plain socket the way a real one does over TLS, keeps every
 * message it was sent, and says what a test tells it to say.
 */
final class FakeCastDevice implements AutoCloseable {

    static final String APP_ID = "CC1AD845";
    static final String TRANSPORT = "transport-7";
    static final String SESSION = "session-1";

    private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    private final List<CastMessage> received = new CopyOnWriteArrayList<>();
    private final Map<String, Function<JsonObject, JsonObjectBuilder>> answers = new ConcurrentHashMap<>();
    private final CountDownLatch connected = new CountDownLatch(1);
    private volatile Socket client;
    private volatile DataOutputStream out;
    private volatile double currentTime;
    private volatile String playerState = "PLAYING";
    private volatile String idleReason;
    private volatile boolean fetches;
    private volatile int mediaSession;

    FakeCastDevice() throws IOException {
        answers.put("GET_STATUS", request -> receiverStatus());
        answers.put("LAUNCH", request -> receiverStatus());
        answers.put("LOAD", request -> mediaStatus());
        answers.put("STOP", request -> receiverStatus());
        final Thread thread = new Thread(this::serve, "fake-cast-device");
        thread.setDaemon(true);
        thread.start();
    }

    int port() {
        return server.getLocalPort();
    }

    Socket connect() throws IOException {
        return new Socket(InetAddress.getLoopbackAddress(), port());
    }

    List<CastMessage> received() {
        return received;
    }

    /**
     * What the device says it is playing, which is what the media status it sends reports.
     */
    void playingAt(double seconds, String state) {
        currentTime = seconds;
        playerState = state;
    }

    /**
     * Which media the statuses from here on are about, a device counting one up for every stream it is given.
     */
    void inSession(int id) {
        mediaSession = id;
    }

    /**
     * A device that reaches for the stream it is handed and reads it away, as a real one does the moment it
     * is told an address.
     */
    void fetchesWhatItIsGiven() {
        fetches = true;
    }

    /**
     * A device that will not play what it was given, as one that cannot reach the address answers.
     */
    void refusing(String reason) {
        playerState = "IDLE";
        idleReason = reason;
    }

    void answer(String type, Function<JsonObject, JsonObjectBuilder> reply) {
        answers.put(type, reply);
    }

    void awaitConnection() throws InterruptedException {
        connected.await(5, TimeUnit.SECONDS);
    }

    /**
     * A message from the device of its own accord, as a status it sends without being asked.
     */
    void send(String source, String namespace, JsonObjectBuilder payload) throws IOException {
        write(new CastMessage(source, CastMessages.SENDER, namespace, payload.build().toString()));
    }

    void sendMediaStatus() throws IOException {
        send(TRANSPORT, CastMessages.MEDIA, mediaStatus().add("requestId", 0));
    }

    void dropConnection() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    JsonObjectBuilder receiverStatus() {
        return CastChannel.object("RECEIVER_STATUS").add("status", Json.createObjectBuilder()
                .add("applications", Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("appId", APP_ID).add("displayName", "Default Media Receiver")
                        .add("sessionId", SESSION).add("transportId", TRANSPORT)
                        .add("namespaces", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder().add("name", CastMessages.MEDIA)))))
                .add("volume", Json.createObjectBuilder().add("level", 0.7).add("muted", false)));
    }

    JsonObjectBuilder mediaStatus() {
        final JsonObjectBuilder status = Json.createObjectBuilder().add("mediaSessionId", mediaSession)
                .add("playerState", playerState).add("currentTime", currentTime).add("playbackRate", 1);
        if (idleReason != null) {
            status.add("idleReason", idleReason);
        }
        return CastChannel.object("MEDIA_STATUS").add("status", Json.createArrayBuilder().add(status));
    }

    @Override
    public void close() throws IOException {
        dropConnection();
        server.close();
    }

    private void serve() {
        try (Socket accepted = server.accept()) {
            client = accepted;
            out = new DataOutputStream(accepted.getOutputStream());
            connected.countDown();
            final DataInputStream in = new DataInputStream(accepted.getInputStream());
            while (true) {
                final byte[] frame = new byte[in.readInt()];
                in.readFully(frame);
                handle(CastMessages.decode(frame));
            }
        } catch (IOException ended) {
            // The test closed the connection, or the channel did.
        }
    }

    private void handle(CastMessage message) throws IOException {
        received.add(message);
        final JsonObject payload = CastChannel.parse(message.payload());
        if (payload == null) {
            return;
        }
        final String type = payload.getString("type", "");
        if (CastMessages.HEARTBEAT.equals(message.namespace())) {
            if ("PING".equals(type)) {
                write(new CastMessage(message.destination(), message.source(), message.namespace(), "{\"type\":\"PONG\"}"));
            }
            return;
        }
        if (payload.containsKey("media")) {
            mediaSession++;
            if (fetches) {
                fetch(payload.getJsonObject("media").getString("contentId"));
            }
        }
        final Function<JsonObject, JsonObjectBuilder> reply = answers.get(type);
        if (reply != null && payload.containsKey("requestId")) {
            final JsonObjectBuilder answer = reply.apply(payload);
            if (answer != null) {
                write(new CastMessage(message.destination(), message.source(), message.namespace(),
                        answer.add("requestId", payload.getInt("requestId")).build().toString()));
            }
        }
    }

    private void fetch(String url) {
        final URI address = URI.create(url);
        final Thread reader = new Thread(() -> {
            try (Socket stream = new Socket(address.getHost(), address.getPort())) {
                stream.getOutputStream().write(("GET " + address.getPath() + " HTTP/1.1\r\nHost: x\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                stream.getInputStream().readAllBytes();
            } catch (IOException ended) {
                // The stream ends with the test.
            }
        }, "fake-cast-fetch");
        reader.setDaemon(true);
        reader.start();
    }

    private void write(CastMessage message) throws IOException {
        final byte[] encoded = message.encode();
        synchronized (this) {
            out.writeInt(encoded.length);
            out.write(encoded);
            out.flush();
        }
    }
}

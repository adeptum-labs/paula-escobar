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
import jakarta.json.JsonReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;

/**
 * One connection to a Cast device: messages framed by their length over TLS, read on a thread of their own,
 * a heartbeat kept up every five seconds, and answers matched to the requests that asked for them.
 *
 * <p>The device signs its own certificate, so the connection trusts whatever it is shown; it is a speaker on
 * the same network, found by name, and nothing secret crosses the wire.</p>
 */
@Slf4j
public final class CastChannel implements AutoCloseable {

    private static final Duration HEARTBEAT = Duration.ofSeconds(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final int LARGEST_MESSAGE = 64 * 1024;
    private static final String TYPE = "type";
    private static final String REQUEST_ID = "requestId";
    private static final String PING = "PING";
    private static final String PONG = "PONG";

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Map<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final List<Consumer<CastMessage>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger requestIds = new AtomicInteger();
    private final ScheduledExecutorService heartbeat;
    private volatile boolean closed;

    private CastChannel(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(runnable, "paula-cast-heartbeat"));
        daemon(this::readLoop, "paula-cast-reader").start();
        connect(CastMessages.RECEIVER);
        heartbeat.scheduleAtFixedRate(this::ping, HEARTBEAT.toMillis(), HEARTBEAT.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static CastChannel open(InetAddress address, int port) throws IOException {
        final SSLSocket socket = (SSLSocket) trustingEverything().getSocketFactory().createSocket();
        socket.connect(new InetSocketAddress(address, port), (int) CONNECT_TIMEOUT.toMillis());
        socket.startHandshake();
        return new CastChannel(socket);
    }

    /**
     * A channel over a socket already open, which is how a device played by a test is reached.
     */
    public static CastChannel over(Socket socket) throws IOException {
        return new CastChannel(socket);
    }

    public InetAddress localAddress() {
        return socket.getLocalAddress();
    }

    public boolean isOpen() {
        return !closed;
    }

    /**
     * Every message that is not the heartbeat, in the order it arrived, on the reading thread.
     */
    public void listen(Consumer<CastMessage> listener) {
        listeners.add(listener);
    }

    /**
     * Opens the virtual connection the device wants before it will talk to a sender, to itself or to the
     * transport of an application it has launched.
     */
    public void connect(String destination) throws IOException {
        send(destination, CastMessages.CONNECTION, object("CONNECT"));
    }

    public void send(String destination, String namespace, JsonObjectBuilder payload) throws IOException {
        write(new CastMessage(CastMessages.SENDER, destination, namespace, payload.build().toString()));
    }

    /**
     * Asks without waiting: the device answers a request number of its own, and whatever it sends back
     * reaches the listeners like anything else it says.
     */
    public void ask(String destination, String namespace, JsonObjectBuilder payload) throws IOException {
        send(destination, namespace, payload.add(REQUEST_ID, requestIds.incrementAndGet()));
    }

    /**
     * Sends and waits for the answer carrying the same request number, which the device echoes back.
     */
    public JsonObject request(String destination, String namespace, JsonObjectBuilder payload, Duration timeout)
            throws IOException {
        final int id = requestIds.incrementAndGet();
        final CompletableFuture<JsonObject> answer = new CompletableFuture<>();
        pending.put(id, answer);
        try {
            send(destination, namespace, payload.add(REQUEST_ID, id));
            return answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IOException("The device did not answer within " + timeout.toSeconds() + " seconds");
        } catch (ExecutionException e) {
            throw new IOException("The connection to the device broke while waiting for an answer", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the device");
        } finally {
            pending.remove(id);
        }
    }

    public static JsonObjectBuilder object(String type) {
        return Json.createObjectBuilder().add(TYPE, type);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        heartbeat.shutdownNow();
        try {
            socket.close();
        } catch (IOException e) {
            log.debug("Closing the cast connection", e);
        }
        pending.values().forEach(answer -> answer.completeExceptionally(new EOFException("The connection was closed")));
    }

    private void ping() {
        try {
            send(CastMessages.RECEIVER, CastMessages.HEARTBEAT, object(PING));
        } catch (IOException e) {
            log.debug("The heartbeat to the device failed", e);
            close();
        }
    }

    private void write(CastMessage message) throws IOException {
        final byte[] encoded = message.encode();
        synchronized (out) {
            out.writeInt(encoded.length);
            out.write(encoded);
            out.flush();
        }
    }

    private void readLoop() {
        try {
            while (!closed) {
                deliver(CastMessages.decode(readFrame()));
            }
        } catch (IOException e) {
            if (!closed) {
                log.debug("The connection to the device ended", e);
            }
            close();
        }
    }

    private byte[] readFrame() throws IOException {
        final int length = in.readInt();
        if (length < 0 || length > LARGEST_MESSAGE) {
            throw new IOException("The device sent a message of " + length + " bytes, which is no message");
        }
        final byte[] encoded = new byte[length];
        in.readFully(encoded);
        return encoded;
    }

    private void deliver(CastMessage message) throws IOException {
        final JsonObject payload = parse(message.payload());
        if (CastMessages.HEARTBEAT.equals(message.namespace())) {
            if (payload != null && PING.equals(payload.getString(TYPE, ""))) {
                write(new CastMessage(CastMessages.SENDER, message.source(), CastMessages.HEARTBEAT,
                        object(PONG).build().toString()));
            }
            return;
        }
        if (payload != null && payload.containsKey(REQUEST_ID)) {
            final CompletableFuture<JsonObject> answer = pending.get(payload.getInt(REQUEST_ID, 0));
            if (answer != null) {
                answer.complete(payload);
            }
        }
        listeners.forEach(listener -> listener.accept(message));
    }

    /**
     * The payload as JSON, or nothing when it is not the JSON every message Paula reads is.
     */
    public static JsonObject parse(String payload) {
        if (!payload.startsWith("{")) {
            return null;
        }
        try (JsonReader reader = Json.createReader(new StringReader(payload))) {
            return reader.readObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Thread daemon(Runnable runnable, String name) {
        final Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static SSLContext trustingEverything() throws IOException {
        try {
            final SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {new TrustingEverything()}, new SecureRandom());
            return context;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("No TLS to reach the device with", e);
        }
    }

    private static final class TrustingEverything implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}

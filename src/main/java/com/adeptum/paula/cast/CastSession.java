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

import com.adeptum.paula.audio.NowPlaying;
import com.adeptum.paula.cast.CastMessages.CastMessage;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Paula playing on one device: the media receiver launched, the device told what to fetch, and word of how it
 * is getting on.
 *
 * <p>The device says where it is in the sound whenever something changes and when asked, so it is asked once
 * a second; between answers the position is carried forward by the clock. What it reports, set against what
 * has been sent, is how far behind the sound runs.</p>
 */
@Slf4j
public final class CastSession implements AutoCloseable {

    /**
     * Google's own media receiver, which every device carries and which needs no registering.
     */
    static final String DEFAULT_MEDIA_RECEIVER = "CC1AD845";

    private static final Duration ANSWER = Duration.ofSeconds(10);

    /**
     * How long the device is given to say it has stopped before the connection goes. Dropping the connection
     * first leaves the stop unheard, and the device plays out the seconds it holds after Paula is gone.
     */
    private static final Duration STOPPING = Duration.ofSeconds(3);
    private static final String STATUS = "status";
    private static final String PLAYING = "PLAYING";
    private static final String BUFFERING = "BUFFERING";
    private static final String IDLE = "IDLE";
    private static final String FINISHED = "FINISHED";
    private static final int MUSIC_TRACK = 3;

    private final CastChannel channel;
    private final CastDevice device;
    private volatile String transport;
    private volatile String sessionId;
    private volatile int mediaSession;
    private volatile int loadsSince;
    private volatile int loadRequest;
    private volatile Position position;
    private volatile boolean played;
    private volatile String refusal;

    CastSession(CastChannel channel, CastDevice device) {
        this.channel = channel;
        this.device = device;
        channel.listen(this::heard);
    }

    public static CastSession open(CastDevice device) throws IOException {
        return new CastSession(CastChannel.open(device.address(), device.port()), device);
    }

    public CastDevice device() {
        return device;
    }

    public InetAddress localAddress() {
        return channel.localAddress();
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * Where the device says it is, or nothing before it has said.
     */
    public Optional<Position> position() {
        return Optional.ofNullable(position);
    }

    /**
     * Why the device gave up on what it was given, once it has said so: it could not reach the address, or
     * someone else took the device. Playing it all the way through is not one of these.
     */
    public Optional<String> refusal() {
        return Optional.ofNullable(refusal);
    }

    /**
     * True once the device has played what it was given and has run out, which is when the next can be given.
     */
    public boolean isFinished() {
        final Position current = position;
        return !channel.isOpen() || (played && current != null && IDLE.equals(current.state()));
    }

    /**
     * Launches the receiver if it is not up and hands it the stream to play; the card a screen shows carries
     * the title and what it is.
     *
     * <p>The answer is not waited for. A device asked to replace what it is already playing holds it back
     * until the new stream has sound in it, and no sound is written until the player has been handed on, so
     * waiting here waits on ourselves. What the device makes of it is heard on the reading thread, where the
     * media it is leaving behind is passed over.</p>
     */
    public void load(String url, NowPlaying song) throws IOException {
        if (transport == null) {
            launch();
        }
        played = false;
        position = null;
        refusal = null;
        loadsSince++;
        loadRequest = channel.nextRequest();
        final JsonObjectBuilder metadata = Json.createObjectBuilder()
                .add("metadataType", MUSIC_TRACK)
                .add("title", song.title())
                .add("artist", song.artist())
                .add("albumName", song.album());
        if (song.picture() != null) {
            metadata.add("images", Json.createArrayBuilder().add(Json.createObjectBuilder().add("url", song.picture())));
        }
        final JsonObjectBuilder media = Json.createObjectBuilder()
                .add("contentId", url)
                .add("contentType", "audio/wav")
                .add("streamType", "BUFFERED")
                .add("metadata", metadata);
        if (song.length() != null) {
            media.add("duration", song.length().toMillis() / 1000.0);
        }
        channel.ask(transport, CastMessages.MEDIA,
                CastChannel.object("LOAD").add("media", media).add("autoplay", true), loadRequest);
    }

    /**
     * Asks the device where it is, which it otherwise only says when something changes.
     */
    public void poll() {
        if (transport == null || !channel.isOpen()) {
            return;
        }
        try {
            channel.request(transport, CastMessages.MEDIA, CastChannel.object("GET_STATUS")
                    .add("mediaSessionId", mediaSession), ANSWER);
        } catch (IOException e) {
            log.debug("Asking {} where it is", device.name(), e);
        }
    }

    @Override
    public void close() {
        try {
            if (sessionId != null && channel.isOpen()) {
                channel.request(CastMessages.RECEIVER, CastMessages.RECEIVER_NAMESPACE,
                        CastChannel.object("STOP").add("sessionId", sessionId), STOPPING);
            }
        } catch (IOException e) {
            log.debug("Stopping the receiver on {}", device.name(), e);
        } finally {
            channel.close();
        }
    }

    private void launch() throws IOException {
        final JsonObject answer = channel.request(CastMessages.RECEIVER, CastMessages.RECEIVER_NAMESPACE,
                CastChannel.object("LAUNCH").add("appId", DEFAULT_MEDIA_RECEIVER), ANSWER);
        final JsonObject application = application(answer);
        if (application == null) {
            throw new IOException(device.name() + " would not launch the media receiver");
        }
        sessionId = application.getString("sessionId", "");
        transport = application.getString("transportId", "");
        channel.connect(transport);
    }

    private static JsonObject application(JsonObject receiverStatus) {
        final JsonObject status = receiverStatus.getJsonObject(STATUS);
        if (status == null || !status.containsKey("applications")) {
            return null;
        }
        for (final JsonValue value : status.getJsonArray("applications")) {
            final JsonObject application = value.asJsonObject();
            if (DEFAULT_MEDIA_RECEIVER.equals(application.getString("appId", ""))) {
                return application;
            }
        }
        return null;
    }

    /**
     * What the device says of its own accord and what it answers alike. Only the load being refused is the
     * song lost: a device asked about the media it has just replaced answers that the question was invalid,
     * which says nothing about the song it now plays.
     */
    private void heard(CastMessage message) {
        final JsonObject payload = CastChannel.parse(message.payload());
        if (payload == null) {
            return;
        }
        final String type = payload.getString("type", "");
        switch (type) {
            case "MEDIA_STATUS" -> mediaStatus(payload);
            case "RECEIVER_STATUS" -> receiverStatus(payload);
            case "CLOSE" -> transport = null;
            case "LOAD_FAILED" -> failedToLoad(payload);
            default -> { }
        }
    }

    /**
     * A load that failed matters only while it is the load in hand. A device handed the next song answers for
     * the one it was told to drop as well, and reading that as this song failing loses this song too.
     */
    private void failedToLoad(JsonObject payload) {
        if (payload.getInt("requestId", 0) == loadRequest) {
            refusal = "LOAD_FAILED";
        }
    }

    /**
     * A device counts one up for every stream it is given, so the newest is the last one it told us about
     * plus the loads sent since. Anything below that is a stream already replaced, and it says so as it ends:
     * read as this song, that it was interrupted or would not play, which loses this song and the next.
     */
    private boolean isTheMediaLeftBehind(int session) {
        return session < mediaSession + loadsSince;
    }

    private void mediaStatus(JsonObject payload) {
        final JsonArray statuses = payload.getJsonArray(STATUS);
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        final JsonObject status = statuses.getJsonObject(0);
        final int session = status.getInt("mediaSessionId", mediaSession);
        final String state = status.getString("playerState", IDLE);
        log.debug("{} is {} at {}s{} in {}", device.name(), state, status.get("currentTime"),
                status.containsKey("idleReason") ? " because " + status.getString("idleReason") : "", session);
        if (isTheMediaLeftBehind(session)) {
            return;
        }
        mediaSession = session;
        loadsSince = 0;
        played |= PLAYING.equals(state) || BUFFERING.equals(state);
        final String reason = status.getString("idleReason", "");
        if (IDLE.equals(state) && !reason.isEmpty() && !FINISHED.equals(reason)) {
            refusal = reason;
        }
        position = new Position(status.getJsonNumber("currentTime") == null ? 0
                : status.getJsonNumber("currentTime").doubleValue(), System.nanoTime(), state);
    }

    /**
     * The receiver going away, as when someone else takes the device, is the media going with it.
     */
    private void receiverStatus(JsonObject payload) {
        if (transport != null && application(payload) == null) {
            transport = null;
            position = new Position(position == null ? 0 : position.seconds(), System.nanoTime(), IDLE);
        }
    }

    /**
     * Where the device said it was and when, so the moment in between can be filled in from the clock while
     * it plays.
     */
    public record Position(double seconds, long atNanos, String state) {

        public double now() {
            return PLAYING.equals(state) ? seconds + (System.nanoTime() - atNanos) / 1e9 : seconds;
        }

        public boolean isPlaying() {
            return PLAYING.equals(state);
        }
    }
}

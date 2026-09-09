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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.audio.AudioException;
import com.adeptum.paula.audio.NowPlaying;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class CastSinkTest {

    private static final int SAMPLE_RATE = 8000;
    private static final int FRAMES = 400;
    private static final Duration FURTHEST_BEHIND = Duration.ofSeconds(3);
    private static final CastDevice KITCHEN = new CastDevice("id", "Kök", "Nest Audio",
            InetAddress.getLoopbackAddress(), CastDevice.CAST_PORT);

    private CastSession session;

    /**
     * A device that cannot reach the address answers this way within moments. Handing the player to it
     * anyway leaves it writing into a stream nothing fetches, which holds the whole screen still.
     */
    @Test
    void willNotHandThePlayerToADeviceThatWillNotFetchTheSound() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.refusing("ERROR");
            sink.open(SAMPLE_RATE);

            final AudioException refused = assertThrows(AudioException.class, () -> sink.begin(song("Paula Test")));
            assertTrue(refused.getMessage().contains("Kök"), refused.getMessage());
            assertTrue(refused.getMessage().contains("could not fetch"), refused.getMessage());
            assertTrue(refused.getMessage().contains("ERROR"), refused.getMessage());
        }
    }

    /**
     * A device replacing what it already plays holds its answer to the load back until the new stream has
     * sound in it, and no sound is written until the player has been handed on. Waiting for that answer
     * first is a wait on ourselves, which ran out and lost the song.
     */
    @Test
    void handsThePlayerOnToADeviceThatHasNotAnsweredTheLoadYet() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "PLAYING");
            fake.fetchesWhatItIsGiven();
            fake.answer("LOAD", request -> null);
            sink.open(SAMPLE_RATE);

            sink.begin(song("Paula Test"));
            sink.write(tone(), FRAMES);

            fake.sendMediaStatus();
            assertTrue(waitFor(() -> sink.lag().isPresent()), "and follows it once it does say where it is");
        }
    }

    /**
     * A device says the media it was playing was interrupted just after it is handed the next song. That is
     * the song before ending, not this one being refused, and taking it for a refusal skipped the song.
     */
    @Test
    void doesNotReadTheEndOfTheSongBeforeAsARefusalOfThisOne() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "PLAYING");
            fake.fetchesWhatItIsGiven();
            sink.open(SAMPLE_RATE);
            sink.begin(song("The song before"));
            sink.write(tone(), FRAMES);

            fake.answer("LOAD", request -> null);
            sink.begin(song("The song after that"));
            sink.begin(song("Paula Test"));
            fake.inSession(1);
            fake.refusing("INTERRUPTED");
            fake.sendMediaStatus();
            fake.inSession(2);
            fake.sendMediaStatus();
            Thread.sleep(200);

            assertDoesNotThrow(() -> sink.write(tone(), FRAMES),
                    "neither of the songs before ending is this one refused");
        }
    }

    /**
     * Asked where it is in media it has just replaced, a device answers that the request was invalid. That
     * is the question being late, not the device giving up on the song it now plays.
     */
    @Test
    void playsOnThroughAnAnswerSayingOnlyThatTheQuestionWasWrong() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "PLAYING");
            fake.fetchesWhatItIsGiven();
            sink.open(SAMPLE_RATE);
            sink.begin(song("Paula Test"));
            sink.write(tone(), FRAMES);

            fake.send(FakeCastDevice.TRANSPORT, CastMessages.MEDIA,
                    CastChannel.object("INVALID_REQUEST").add("requestId", 0));
            Thread.sleep(200);

            assertDoesNotThrow(() -> sink.write(tone(), FRAMES), "which is not the device giving up");
        }
    }

    /**
     * A release carrying no art of its own, which is most of them, still gives a screen something: a card of
     * what is known about the song, drawn and served beside the sound.
     */
    @Test
    void drawsACardForASongWithNoArtOfItsOwn() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "PLAYING");
            fake.fetchesWhatItIsGiven();
            sink.open(SAMPLE_RATE);

            sink.begin(NowPlaying.builder().title("Just for Blues").artist("Mantronix").build());

            assertTrue(waitFor(() -> fake.received().stream().anyMatch(message -> message.payload().contains("images")
                    && message.payload().contains(".png"))), "a screen is pointed at a picture to show");
        }
    }

    /**
     * A device handed the next song answers for the one it was told to drop as well. Read as this song
     * failing, that lost this song too, and the next, and the player ran through the playlist.
     */
    @Test
    void doesNotReadTheLoadBeforeFailingAsThisOneFailing() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "PLAYING");
            fake.fetchesWhatItIsGiven();
            sink.open(SAMPLE_RATE);
            sink.begin(song("The song before"));
            sink.write(tone(), FRAMES);

            fake.answer("LOAD", request -> null);
            sink.begin(song("Paula Test"));
            fake.send(FakeCastDevice.TRANSPORT, CastMessages.MEDIA,
                    CastChannel.object("LOAD_FAILED").add("requestId", 1));
            Thread.sleep(200);

            assertDoesNotThrow(() -> sink.write(tone(), FRAMES), "the load before failing is not this one");
        }
    }

    @Test
    void stopsWritingToADeviceSomeoneElseHasTakenOver() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "PLAYING");
            fake.fetchesWhatItIsGiven();
            sink.open(SAMPLE_RATE);
            sink.begin(song("Paula Test"));
            sink.write(tone(), FRAMES);

            fake.refusing("CANCELLED");
            fake.sendMediaStatus();
            assertTrue(waitFor(() -> session.refusal().isPresent()), "the device said it had given up");

            assertThrows(IllegalStateException.class, () -> sink.write(tone(), FRAMES),
                    "which is said rather than written into");
        }
    }

    @Test
    void measuresADeviceFillingItsBufferAsFallingFurtherBehind() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "BUFFERING");
            fake.fetchesWhatItIsGiven();
            sink.open(SAMPLE_RATE);
            sink.begin(song("Paula Test"));
            sink.write(tone(), FRAMES);

            assertTrue(waitFor(() -> sink.lag().isPresent()), "the device said where it is");
            assertTrue(sink.lag().orElseThrow().compareTo(Duration.ZERO) > 0,
                    "sound written that a device buffering has not played is sound it runs behind");
        }
    }

    /**
     * A device that gets nowhere while it still fetches otherwise falls behind for good, since the player
     * goes on at its own pace and the device never makes the seconds up.
     */
    @Test
    void holdsThePlayerBackWhenAPlayingDeviceGetsNowhere() throws Exception {
        try (FakeCastDevice fake = new FakeCastDevice(); CastSink sink = sink(fake)) {
            fake.playingAt(0, "PLAYING");
            fake.fetchesWhatItIsGiven();
            sink.open(SAMPLE_RATE);
            sink.begin(song("Paula Test"));

            final Thread stuck = sayItIsPlayingButNeverGetOn(fake);
            final Thread player = play(sink);
            try {
                assertTrue(waitFor(() -> behind(sink).compareTo(FURTHEST_BEHIND) >= 0),
                        "the player runs on while the device stays where it is");
                Thread.sleep(3000);

                assertFalse(behind(sink).compareTo(FURTHEST_BEHIND.plusSeconds(2)) > 0,
                        "and is then held there rather than running further ahead for the rest of the song");
            } finally {
                player.interrupt();
                stuck.interrupt();
                player.join(2000);
            }
        }
    }

    /**
     * A device saying it plays while its position stands still, which is a rebuffer as the player sees it.
     */
    private static Thread sayItIsPlayingButNeverGetOn(FakeCastDevice fake) {
        final Thread ticker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    fake.sendMediaStatus();
                    Thread.sleep(100);
                } catch (IOException e) {
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "stuck-device");
        ticker.setDaemon(true);
        ticker.start();
        return ticker;
    }

    private static Duration behind(CastSink sink) {
        return sink.lag().orElse(Duration.ZERO);
    }

    private static Thread play(CastSink sink) {
        final Thread player = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                sink.write(tone(), FRAMES);
            }
        }, "player");
        player.setDaemon(true);
        player.start();
        return player;
    }

    private CastSink sink(FakeCastDevice fake) {
        return new CastSink(KITCHEN, 0, FURTHEST_BEHIND, device -> {
            session = new CastSession(CastChannel.over(fake.connect()), device);
            return session;
        });
    }

    private static NowPlaying song(String title) {
        return NowPlaying.builder().title(title).build();
    }

    private static short[] tone() {
        final short[] interleaved = new short[FRAMES * 2];
        for (int i = 0; i < interleaved.length; i++) {
            interleaved[i] = (short) (i % 2 == 0 ? 1000 : -1000);
        }
        return interleaved;
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}

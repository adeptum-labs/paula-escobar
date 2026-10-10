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

package com.adeptum.paula.module.zx;

import static com.adeptum.paula.testing.TestTaps.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cache.CacheDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TapeLengthsTest {

    private static byte[] singleBlock(byte[] code) {
        return tape(codePart("music", ENTRY, code));
    }

    private static TapProgram program(byte[] tape) throws Exception {
        return TapProgram.of(Path.of("x.tap"), TapReader.read(Path.of("x.tap"), tape));
    }

    private TapeRun run(Path cache, byte[] code) throws Exception {
        final byte[] tape = singleBlock(code);
        return new TapeLengths(new CacheDirectory(cache)).of(tape, program(tape));
    }

    @Test
    void aProgramThatReturnsEndsWhereItReturns(@TempDir Path cache) throws Exception {
        final TapeRun run = run(cache, ONE_AND_A_HALF_SECONDS_THEN_RETURN);

        assertEquals(EndReason.RETURNED, run.reason());
        assertTrue(run.length().toMillis() >= 1470 && run.length().toMillis() <= 1510, run.length().toString());
        assertTrue(run.audible());
        assertEquals(Spectrum.Model.K48, run.model());
    }

    @Test
    void aProgramThatPlaysForeverIsCapped(@TempDir Path cache) throws Exception {
        final TapeRun run = run(cache, SQUARE_WAVE);

        assertEquals(EndReason.CAPPED, run.reason());
        assertEquals(TapeLengths.CAP, run.length());
    }

    @Test
    void aHaltWithInterruptsOffIsTheEnd(@TempDir Path cache) throws Exception {
        assertEquals(EndReason.HALTED, run(cache, PULSE_THEN_HALT).reason());
    }

    @Test
    void leavingForTheRomAfterSoundingIsAFinishedSong(@TempDir Path cache) throws Exception {
        final TapeRun run = run(cache, PULSE_THEN_JUMP_TO_ROM);

        assertEquals(EndReason.ROM_CALL, run.reason());
        assertTrue(run.audible());
    }

    @Test
    void leavingForTheRomInSilenceIsNotAudible(@TempDir Path cache) throws Exception {
        final TapeRun run = run(cache, SILENT_JUMP_TO_ROM);

        assertEquals(EndReason.ROM_CALL, run.reason());
        assertFalse(run.audible());
    }

    @Test
    void waitingInSilenceEndsAfterTheSilenceLimit(@TempDir Path cache) throws Exception {
        final TapeRun run = run(cache, WAIT_FOREVER);

        assertEquals(EndReason.SILENT, run.reason());
        assertFalse(run.audible());
        assertEquals(Duration.ofMillis(500), run.length());
    }

    @Test
    void silenceAfterSoundEndsHalfASecondAfterTheLastEdge(@TempDir Path cache) throws Exception {
        final TapeRun run = run(cache, PULSE_THEN_WAIT);

        assertEquals(EndReason.SILENT, run.reason());
        assertTrue(run.audible());
        assertTrue(run.length().toMillis() >= 490 && run.length().toMillis() <= 520, run.length().toString());
    }

    @Test
    void anAyThatKeepsBeingSilencedEndsTheSong(@TempDir Path cache) throws Exception {
        final TapeRun run = run(cache, AY_TONE_THEN_REWRITES_SILENCE);

        assertEquals(Spectrum.Model.K128, run.model());
        assertEquals(EndReason.SILENT, run.reason());
        assertTrue(run.audible());
        assertTrue(run.length().compareTo(Duration.ofSeconds(1)) < 0, run.length().toString());
    }

    @Test
    void aProgramThatPagesOrWritesTheAyNeeds128K(@TempDir Path cache) throws Exception {
        assertEquals(Spectrum.Model.K128, run(cache, AY_WRITE).model());
        assertEquals(Spectrum.Model.K128, run(cache, PAGE_BANK_ONE).model());
    }

    @Test
    void anAyToneIsAudibleOnThe128K(@TempDir Path cache) throws Exception {
        final TapeRun run = run(cache, AY_TONE);

        assertEquals(Spectrum.Model.K128, run.model());
        assertEquals(EndReason.CAPPED, run.reason());
        assertTrue(run.audible());
    }

    @Test
    void remembersAnswersByTheMd5OfTheTape(@TempDir Path cache) throws Exception {
        final byte[] tape = singleBlock(ONE_PULSE_THEN_RETURN);
        final TapeLengths lengths = new TapeLengths(new CacheDirectory(cache));
        final TapeRun first = lengths.of(tape, program(tape));

        final TapeRun again = lengths.of(tape, null);

        assertEquals(first, again, "a cached answer needs no program to run");
        try (var files = Files.list(cache.resolve("tapes"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void stillAnswersWhenTheCacheCannotBeWritten(@TempDir Path dir) throws Exception {
        final Path notADirectory = Files.writeString(dir.resolve("file"), "x");

        final TapeRun run = run(notADirectory.resolve("cache"), ONE_AND_A_HALF_SECONDS_THEN_RETURN);

        assertEquals(EndReason.RETURNED, run.reason());
    }

    @Test
    void recomputesAnAnswerItCannotRead(@TempDir Path cache) throws Exception {
        final byte[] tape = singleBlock(ONE_PULSE_THEN_RETURN);
        final TapeLengths lengths = new TapeLengths(new CacheDirectory(cache));
        lengths.of(tape, program(tape));
        try (var files = Files.list(cache.resolve("tapes"))) {
            for (final Path file : (Iterable<Path>) files::iterator) {
                Files.writeString(file, "version=0\nnonsense");
            }
        }

        assertEquals(EndReason.RETURNED, lengths.of(tape, program(tape)).reason());
    }
}

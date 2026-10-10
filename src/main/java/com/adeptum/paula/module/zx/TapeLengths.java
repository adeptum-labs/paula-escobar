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

import com.adeptum.paula.cache.CacheDirectory;
import com.adeptum.paula.module.ay.AyRegisters;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds out how long a tape plays by running it without audio: to the point the program returns, halts or leaves
 * for the ROM, or falls silent for a while, or to the cap where nothing of the kind happens, which is what most
 * looping engines do. The same run tells whether the program pages memory or writes to the AY, which makes it a
 * 128K tape and is run again on that machine. A run takes seconds at worst, so the answer is kept in the cache
 * under the MD5 of the tape.
 */
@Slf4j
final class TapeLengths {

    static final Duration CAP = Duration.ofMinutes(3);
    static final Duration SILENCE = Duration.ofSeconds(3);

    private static final String CACHE_SEGMENT = "tapes";
    private static final String VERSION = "1";
    private static final int MINIMUM_EDGES = 2;
    private static final Duration SILENT_TAIL = Duration.ofMillis(500);
    private static final String MD5 = "MD5";

    private final CacheDirectory cache;

    TapeLengths(CacheDirectory cache) {
        this.cache = cache;
    }

    static TapeLengths inUserCache() {
        return new TapeLengths(CacheDirectory.resolve());
    }

    TapeRun of(byte[] tape, TapProgram program) {
        final Optional<Path> file = locate(md5(tape) + ".properties");
        final Optional<TapeRun> cached = file.flatMap(TapeLengths::read);
        if (cached.isPresent()) {
            return cached.get();
        }
        final TapeRun run = analyse(program);
        file.ifPresent(target -> store(target, run));
        return run;
    }

    private Optional<Path> locate(String name) {
        try {
            return Optional.of(cache.file(CACHE_SEGMENT, name));
        } catch (IOException e) {
            log.warn("Not keeping tape lengths, the cache is unusable: {}", e.toString());
            return Optional.empty();
        }
    }

    private void store(Path file, TapeRun run) {
        try {
            cache.writeAtomically(file, write(run));
        } catch (IOException e) {
            log.warn("Could not keep the tape length in {}: {}", file, e.toString());
        }
    }

    private static TapeRun analyse(TapProgram program) {
        return run(program, Spectrum.Model.K48)
                .orElseGet(() -> run(program, Spectrum.Model.K128).orElseThrow());
    }

    /**
     * Runs to the end of the song, or answers nothing as soon as a 48K machine is found to be too small for the
     * program, so that the minutes a looping 128K tune would take are spent once, on the right machine.
     */
    private static Optional<TapeRun> run(TapProgram program, Spectrum.Model model) {
        final Probe probe = new Probe();
        final Spectrum machine = new Spectrum(model, program, probe);
        final long cap = tstates(CAP, model);
        final long silence = tstates(SILENCE, model);
        Optional<EndReason> ended = Optional.empty();
        while (ended.isEmpty()) {
            ended = machine.runUntil(machine.tstates() + model.frame());
            if (model == Spectrum.Model.K48 && (machine.pagingSeen() || machine.ayWritten())) {
                return Optional.empty();
            }
            if (ended.isEmpty() && machine.tstates() >= cap) {
                ended = Optional.of(EndReason.CAPPED);
            } else if (ended.isEmpty() && machine.tstates() - probe.lastSound >= silence && !probe.ay.audible()) {
                ended = Optional.of(EndReason.SILENT);
            }
        }
        final long end = switch (ended.get()) {
            case CAPPED -> cap;
            case SILENT -> Math.min(machine.tstates(), probe.lastSound + tstates(SILENT_TAIL, model));
            default -> machine.endedAt();
        };
        return Optional.of(new TapeRun(model, Duration.ofMillis(end * 1000 / model.clock()), ended.get(), probe.audible()));
    }

    private static long tstates(Duration duration, Spectrum.Model model) {
        return duration.toMillis() * model.clock() / 1000;
    }

    /**
     * What was heard of a run: when sound last changed, and whether it was ever more than a click.
     */
    private static final class Probe implements MachineOutput {

        private final AyRegisters ay = new AyRegisters();
        private long lastSound;
        private int edges;
        private boolean ayHeard;

        @Override
        public void beeper(long tstate, double level) {
            edges++;
            lastSound = tstate;
        }

        @Override
        public void ayRegister(long tstate, int register, int value) {
            final boolean wasAudible = ay.audible();
            ay.write(register, value);
            ayHeard |= ay.audible();
            if (wasAudible || ay.audible()) {
                lastSound = tstate;
            }
        }

        boolean audible() {
            return edges >= MINIMUM_EDGES || ayHeard;
        }
    }

    private static Optional<TapeRun> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            final Properties properties = new Properties();
            properties.load(reader);
            if (!VERSION.equals(properties.getProperty("version"))) {
                return Optional.empty();
            }
            return Optional.of(new TapeRun(Spectrum.Model.valueOf(properties.getProperty("model")),
                    Duration.ofMillis(Long.parseLong(properties.getProperty("millis"))),
                    EndReason.valueOf(properties.getProperty("reason")),
                    Boolean.parseBoolean(properties.getProperty("audible"))));
        } catch (IOException | RuntimeException e) {
            log.warn("Ignoring the unreadable tape length {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    private static byte[] write(TapeRun run) {
        return ("version=" + VERSION + "\nmodel=" + run.model() + "\nmillis=" + run.length().toMillis()
                + "\nreason=" + run.reason() + "\naudible=" + run.audible() + "\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String md5(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(MD5).digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is part of every Java runtime", e);
        }
    }
}
